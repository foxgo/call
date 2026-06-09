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
        executor.setMaxPoolSize(properties.getDispatcherParallelism() * 5);
        executor.setQueueCapacity(properties.getDispatcherParallelism() * 100);
        executor.setThreadNamePrefix("call-task-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean(name = "callTaskDispatchSendExecutor")
    public ThreadPoolTaskExecutor callTaskDispatchSendExecutor(CallTaskDispatchProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getDispatchSendParallelism());
        executor.setMaxPoolSize(properties.getDispatchSendParallelism() * 10);
        executor.setQueueCapacity(properties.getDispatchSendParallelism() * 1000);
        executor.setThreadNamePrefix("call-task-dispatch-send-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
