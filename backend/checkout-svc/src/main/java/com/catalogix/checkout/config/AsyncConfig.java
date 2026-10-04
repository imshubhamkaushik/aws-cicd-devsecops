package com.catalogix.checkout.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Backs every @Async method (currently OrderEventPublisher's RabbitMQ relay
// methods) with a small bounded pool instead of Spring's default
// SimpleAsyncTaskExecutor, which spins up an unbounded new thread per call.
//
// @EnableAsync / @EnableScheduling are what actually switch the annotations on. Neither was
// declared anywhere, so @Async publishers ran on the request thread and — more importantly —
// CompensationOutboxProcessor's @Scheduled retry loop NEVER RAN: queued stock releases
// were written to the outbox and then sat there forever. (This also enables
// PendingOrderExpiryJob.)
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("order-async-");
        executor.initialize();
        return executor;
    }
}
