package com.codeying.service.impl;

import com.codeying.properties.AiModelHttpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real local sockets: verifies outbound counts, configured read timeout and automatic recovery. */
class AiModelHttpTest {
    HttpServer server;
    ExecutorService executor;
    final AtomicInteger requests = new AtomicInteger();
    volatile int status = 200;
    volatile String response = "{\"choices\":[{\"message\":{\"content\":\"legit content\"}}]}";
    volatile int delayMs;
    volatile CountDownLatch entered;
    volatile CountDownLatch release;

    @BeforeEach void start() throws Exception {
        executor=Executors.newCachedThreadPool();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes(); // Task-owned fixture; no body retained or logged.
            if (entered!=null) entered.countDown();
            try {
                if (release!=null) release.await(2,TimeUnit.SECONDS);
                if (delayMs>0) Thread.sleep(delayMs);
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
    }
    @AfterEach void stop() throws Exception {
        if (release!=null) release.countDown(); server.stop(0); executor.shutdownNow();
        assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
    }
    AiModelServiceImpl client(int timeout, int threshold, int coolDown) {
        var properties=new AiModelHttpProperties(); properties.setConnectTimeoutMs(100); properties.setReadTimeoutMs(timeout);
        properties.setFailureThreshold(threshold); properties.setCoolDownMs(coolDown);
        return new AiModelServiceImpl(new ObjectMapper(),properties,"http://127.0.0.1:"+server.getAddress().getPort(),
                "fixture-only", "/v1/chat/completions", "fixture",0.7,512);
    }
    @Test void validContentUsesOneOutboundRequest() {
        assertEquals("legit content",client(1000,3,100).chat("fixture","fixture")); assertEquals(1,requests.get());
    }
    @ParameterizedTest @ValueSource(strings={"not JSON", "{}", "[]", "{\"choices\":[]}", "{\"choices\":[{}]}",
            "{\"choices\":[{\"message\":{\"content\":42}}]}", "{\"choices\":[{\"message\":{\"content\":\" \"}}]}",
            "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]} {}",
            "```json\n{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}\n```"})
    void invalidHttp200RetainsCauseWithoutRetry(String body) {
        response=body; var failure=assertThrows(IllegalStateException.class,()->client(1000,3,100).chat("fixture","fixture"));
        assertNotNull(failure.getCause()); assertEquals("Model request unavailable",failure.getMessage()); assertEquals(1,requests.get());
    }
    @ParameterizedTest @ValueSource(ints={402,503})
    void statusErrorsCountWithoutAutomaticRetries(int code) {
        status=code; response="private upstream body"; var failure=assertThrows(IllegalStateException.class,()->client(1000,3,100).chat("fixture","fixture"));
        assertNotNull(failure.getCause()); assertFalse(failure.getMessage().contains(response)); assertEquals(1,requests.get());
    }
    @Test void actualReadTimeoutIsConfigurable() {
        delayMs=600;
        long begin=System.nanoTime(); var fast=client(120,3,100);
        var failure=assertThrows(IllegalStateException.class,()->fast.chat("fixture","fixture"));
        long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin);
        assertNotNull(failure.getCause()); assertTrue(elapsed>=80 && elapsed<550,"configured timeout elapsed="+elapsed);
        assertEquals("legit content",client(1500,3,100).chat("fixture","fixture")); assertEquals(2,requests.get());
    }
    @Test void consecutiveFailureSuppressionFailedRecoveryAndSuccessReset() throws Exception {
        var model=client(1000,2,100); status=503;
        for(int i=0;i<2;i++) assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture"));
        for(int i=0;i<5;i++) assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture"));
        assertEquals(2,requests.get()); Thread.sleep(130);
        assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture")); assertEquals(3,requests.get());
        assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture")); assertEquals(3,requests.get());
        Thread.sleep(130); status=200; assertEquals("legit content",model.chat("fixture","fixture"));
        status=402; assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture"));
        status=200; assertEquals("legit content",model.chat("fixture","fixture"));
        status=503; assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture"));
        status=200; assertEquals("legit content",model.chat("fixture","fixture")); assertEquals(8,requests.get());
    }
    @Test void onlyOneRecoveryRequestMayRunAfterCoolDown() throws Exception {
        var model=client(2000,1,100); status=503; assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture"));
        Thread.sleep(130); status=200; entered=new CountDownLatch(1); release=new CountDownLatch(1);
        var call=executor.submit(()->model.chat("fixture","fixture")); assertTrue(entered.await(1,TimeUnit.SECONDS));
        for(int i=0;i<5;i++) assertThrows(IllegalStateException.class,()->model.chat("fixture","fixture"));
        assertEquals(2,requests.get()); release.countDown(); assertEquals("legit content",call.get(2,TimeUnit.SECONDS));
        entered=null; release=null; assertEquals("legit content",model.chat("fixture","fixture")); assertEquals(3,requests.get());
    }
}
