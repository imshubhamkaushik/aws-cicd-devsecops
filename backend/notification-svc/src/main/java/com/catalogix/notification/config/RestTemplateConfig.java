package com.catalogix.notification.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // notification-svc only ever GETs user-svc's notification-preferences
        // lookup (see UserPreferenceClient) — short timeouts on purpose,
        // since this call sits in the middle of an async RabbitMQ listener
        // invocation and should fail fast rather than hold up the retry/DLQ
        // machinery already in place for the email send itself.
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }
}
