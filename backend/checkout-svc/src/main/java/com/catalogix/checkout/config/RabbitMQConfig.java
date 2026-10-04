package com.catalogix.checkout.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * checkout-svc is a publisher only here — it declares the exchange (safe/idempotent
 * even if notification-svc's consumer-side declaration races it at startup) and
 * sends JSON messages to it; it declares no queues of its own.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "catalogix.events";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
