package com.callcenter.task.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DispatchExecutorConfiguration {

    @Bean(name = "callTaskDispatchExecutor")
    public Executor callTaskDispatchExecutor(CallTaskDispatchProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getDispatcherParallelism());
        executor.setMaxPoolSize(properties.getDispatcherParallelism());
        executor.setQueueCapacity(properties.getDispatcherParallelism());
        executor.setThreadNamePrefix("call-task-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
