package com.codeying.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AliOssResourceTest {
    OSS client=mock(OSS.class);
    AliOssUtil util=new AliOssUtil("http://127.0.0.1", "synthetic", "synthetic", "local", "http://127.0.0.1/local", false, 300) {
        @Override protected OSS createClient() { return client; }
    };
    @Test void uploadSuccessFailureAndShutdownKeepCause() {
        var captured=new AtomicReference<InputStream>();
        when(client.putObject(any(PutObjectRequest.class))).thenAnswer(i->{
            var request=i.getArgument(0,PutObjectRequest.class); assertFalse(request.isLogEnabled()); captured.set(request.getInputStream()); assertArrayEquals(new byte[]{1,2},captured.get().readAllBytes()); return new PutObjectResult();
        });
        assertEquals("http://127.0.0.1/local/folder/a%20%23%3F.png",util.upload(new byte[]{1,2},"folder/a #?.png"));
        assertNotNull(captured.get()); verify(client).shutdown();
        var cause=new OSSException("synthetic-secret-body"); reset(client);
        when(client.putObject(any(PutObjectRequest.class))).thenThrow(cause);
        var failure=assertThrows(IllegalStateException.class,()->util.upload(new byte[]{1},"x.png"));
        assertSame(cause,failure.getCause()); verify(client).shutdown();
    }
    ObjectListing page(boolean truncated,String marker,String...keys) {
        var p=new ObjectListing(); p.setTruncated(truncated); p.setNextMarker(marker);
        p.setObjectSummaries(java.util.Arrays.stream(keys).map(key->{var s=new OSSObjectSummary();s.setKey(key);return s;}).toList());return p;
    }
    @Test void paginationFiltersBeforeLimitAndUsesMarkers() {
        when(client.listObjects(any(ListObjectsRequest.class))).thenAnswer(i->{
            var req=i.getArgument(0,ListObjectsRequest.class); assertFalse(req.isLogEnabled()); assertEquals("dish/",req.getPrefix()); assertEquals(2,req.getMaxKeys());
            if(req.getMarker()==null) return page(true,"dish/b.txt","dish/","dish/b.txt");
            assertEquals("dish/b.txt",req.getMarker()); return page(false,null,"dish/c.png","dish/d.png","dish/e.png");
        });
        var result=util.listObjects("dish/",2,s->s.getKey().endsWith(".png"));
        assertEquals(List.of("dish/c.png","dish/d.png"),result.stream().map(OSSObjectSummary::getKey).toList());
        verify(client,times(2)).listObjects(any(ListObjectsRequest.class)); verify(client).shutdown();
    }
    @Test void malformedPaginationAndUpstreamFailureCloseClient() {
        when(client.listObjects(any(ListObjectsRequest.class))).thenReturn(page(true,null,"x.txt"));
        assertThrows(IllegalStateException.class,()->util.listObjects("",1,s->false)); verify(client).shutdown();
        reset(client); var cause=new OSSException("synthetic-secret"); when(client.listObjects(any(ListObjectsRequest.class))).thenThrow(cause);
        assertSame(cause,assertThrows(IllegalStateException.class,()->util.listObjects("",1)).getCause()); verify(client).shutdown();
    }
    @Test void signedUrlClientClosesOnSuccessAndFailure() throws Exception {
        var privateUtil=new AliOssUtil("http://127.0.0.1", "synthetic", "synthetic", "local", "", true, 300) {
            @Override protected OSS createClient() { return client; }
        };
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(new java.net.URL("http://127.0.0.1/key?Signature=synthetic"));
        assertTrue(privateUtil.buildObjectUrl("key").startsWith("http://127.0.0.1/key")); verify(client).shutdown();
        reset(client); var cause=new OSSException("synthetic");when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenThrow(cause);
        assertSame(cause,assertThrows(OSSException.class,()->privateUtil.buildObjectUrl("key")));verify(client).shutdown();
    }
}
