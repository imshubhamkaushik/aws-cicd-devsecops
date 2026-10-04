package com.catalogix.security;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin (CORS) support — OPTIONAL, and off unless ALLOWED_ORIGINS is set.
 *
 * In every deployment (Docker Compose and Kubernetes) the browser talks to ONE
 * origin, the nginx gateway, which proxies /api/* to the services: the requests
 * are same-origin, so CORS never applies. ALLOWED_ORIGINS is only needed
 * for the one case that really is cross-origin: a browser calling a service directly,
 * e.g. the Vite dev server on localhost:5173, without going through the gateway.
 *
 * CORS_ALLOW_CREDENTIALS=true is meant for user-svc only (the httpOnly refresh
 * cookie has to travel on such direct calls). A wildcard origin combined with
 * credentials would let ANY website make credentialed requests, so that
 * combination is refused: credentials are switched off and a warning is logged.
 * List explicit origins instead.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${ALLOWED_ORIGINS:}")
    private String allowedOrigins;

    @Value("${CORS_ALLOW_CREDENTIALS:false}")
    private boolean allowCredentials;

    @Bean
    public WebMvcConfigurer corsConfigurer() {

        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (origins.length == 0) {
            // No cross-origin callers configured: register no CORS mappings at all.
            return new WebMvcConfigurer() { };
        }

        boolean credentials = allowCredentials;
        if (credentials && Arrays.asList(origins).contains("*")) {
            log.warn("CORS_ALLOW_CREDENTIALS=true is ignored because ALLOWED_ORIGINS contains \"*\": "
                    + "a wildcard origin with credentials would let any site make credentialed requests. "
                    + "List explicit origins to enable credentials.");
            credentials = false;
        }
        final boolean allowCreds = credentials;

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(allowCreds);
            }
        };
    }
}
