package com.codeying.task;

import com.codeying.websocket.CustomerServiceWebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 人工客服回复锁超时释放定时任务。
 *
 * @author Endercloud
 */
@Slf4j
@Component
public class CustomerServiceReplyLockTask {

    /**
     * 扫描并释放超时锁（固定延迟 5 秒）。
     */
    @Scheduled(fixedDelay = 5000)
    public void releaseExpiredLocks() {
        CustomerServiceWebSocketServer.releaseExpiredLocks();
    }
}
