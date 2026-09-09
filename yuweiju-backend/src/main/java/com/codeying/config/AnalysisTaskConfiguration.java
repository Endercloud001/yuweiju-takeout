package com.codeying.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 分析任务线程池配置。
 *
 * @author Endercloud
 */
@Slf4j
@Configuration
public class AnalysisTaskConfiguration implements SchedulingConfigurer {

    /**
     * 分析定时任务线程池。
     *
     * @return 线程池调度器
     */
    @Bean
    public ThreadPoolTaskScheduler analysisTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("analysis-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(ex -> log.error("分析定时任务执行异常", ex));
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(analysisTaskScheduler());
    }
}
