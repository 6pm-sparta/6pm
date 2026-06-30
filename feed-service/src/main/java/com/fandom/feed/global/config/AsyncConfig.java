package com.fandom.feed.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {
    @Value("${async.fanout.core-pool-size}")
    private int corePoolSize;

    @Value("${async.fanout.max-pool-size}")
    private int maxPoolSize;

    @Value("${async.fanout.queue-capacity}")
    private int queueCapacity;

    @Bean
    public Executor fanoutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("fanout-");
        executor.initialize();
        return executor;
    }
}