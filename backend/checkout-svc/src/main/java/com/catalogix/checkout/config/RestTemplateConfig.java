package com.catalogix.checkout.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Explicitly use Apache HttpClient5 (via HttpComponentsClientHttpRequestFactory):
        // the JDK's default HttpURLConnection-based factory does not support PATCH,
        // which this service needs for its calls to inventory-svc's adjust endpoint.
        return builder
                .requestFactoryBuilder(ClientHttpRequestFactoryBuilder.httpComponents())
                // Keep connect failures fast, but allow a cold downstream
                // Spring/JPA operation a little more time. Payment is
                // idempotent now, so a timeout can be retried safely with
                // the same key instead of risking a duplicate charge.
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(8))
                .build();
    }
}