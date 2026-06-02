package com.callcenter.task.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DispatchExecutorConfiguration {

    @Bean(name = "callTaskDispatchExecutor")
    public ThreadPoolTaskExecutor callTaskDispatchExecutor(CallTaskDispatchProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getDispatcherParallelism());
        executor.setMaxPoolSize(properties.getDispatcherParallelism());
        executor.setQueueCapacity(properties.getDispatcherParallelism());
        executor.setThreadNamePrefix("call-task-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean(name = "callTaskDispatchSendExecutor")
    public ThreadPoolTaskExecutor callTaskDispatchSendExecutor(CallTaskDispatchProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getDispatchSendParallelism());
        executor.setMaxPoolSize(properties.getDispatchSendParallelism());
        executor.setQueueCapacity(properties.getDispatchSendParallelism());
        executor.setThreadNamePrefix("call-task-dispatch-send-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
