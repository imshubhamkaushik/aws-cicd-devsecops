package com.catalogix.security;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight, per-pod rate limiter.
 *
 * The API gateway should enforce the primary client-wide rate limit.
 * This filter provides an additional application-level limit, with
 * stricter limits for authentication endpoints.
 *
 * Counters are maintained in memory and are not shared across replicas.
 *
 * X-Real-IP is trusted only when the application is reachable exclusively
 * through a trusted gateway that overwrites this header.
 */
@Component
@Order(1)
public class RateLimiterFilter implements Filter {

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private static final long WINDOW_MS = 60_000L;
    private static final long EVICT_AFTER_MS = 2 * WINDOW_MS;
    private static final long EVICTION_INTERVAL_MINUTES = 2L;

    private static final String AUTH_SCOPE = "auth";
    private static final String GENERAL_SCOPE = "general";

    private static final String RATE_LIMIT_LIMIT = "RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING = "RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET = "RateLimit-Reset";

    private static final String RATE_LIMIT_EXCEEDED_JSON = "{\"message\":\"Rate limit exceeded\",\"status\":429}";

    private final int generalRequestsPerMinute;
    private final int authRequestsPerMinute;
    private final boolean enabled;

    private final ConcurrentHashMap<String, RequestCounter> counters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService evictionScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "rate-limiter-eviction");
        thread.setDaemon(true);
        return thread;
    });

    public RateLimiterFilter(
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${security.rate-limit.general.requests-per-minute:300}") int generalRequestsPerMinute,
            @Value("${security.rate-limit.auth.requests-per-minute:10}") int authRequestsPerMinute) {

        if (generalRequestsPerMinute < 1 || authRequestsPerMinute < 1) {
            throw new IllegalStateException(
                    "Rate limits must be positive integers");
        }

        this.enabled = enabled;
        this.generalRequestsPerMinute = generalRequestsPerMinute;
        this.authRequestsPerMinute = authRequestsPerMinute;

        evictionScheduler.scheduleAtFixedRate(
                this::evictStaleEntries,
                EVICTION_INTERVAL_MINUTES,
                EVICTION_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        evictionScheduler.shutdown();

        try {
            if (!evictionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                evictionScheduler.shutdownNow();
            }
        } catch (InterruptedException exception) {
            evictionScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!enabled || isExempt(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        boolean authenticationRequest = isSensitiveAuthenticationPath(httpRequest.getRequestURI());

        String scope = authenticationRequest
                ? AUTH_SCOPE
                : GENERAL_SCOPE;

        int limit = authenticationRequest
                ? authRequestsPerMinute
                : generalRequestsPerMinute;

        String clientIp = resolveClientIp(httpRequest);
        String counterKey = clientIp + "|" + scope;

        long now = System.currentTimeMillis();

        RequestCounter counter = counters.computeIfAbsent(
                counterKey,
                ignored -> new RequestCounter(now));

        synchronized (counter) {
            counter.lastSeen = now;

            // Start a fresh fixed window when the current window expires.
            // Also recover if the system clock moves backwards.
            if (now < counter.windowStart
                    || now - counter.windowStart >= WINDOW_MS) {
                counter.windowStart = now;
                counter.count = 0;
            }

            long resetInMs = Math.max(
                    1L,
                    WINDOW_MS - (now - counter.windowStart));

            long resetInSeconds = Math.max(
                    1L,
                    (resetInMs + 999L) / 1000L);

            if (counter.count >= limit) {
                writeRateLimitExceededResponse(
                        httpResponse,
                        limit,
                        resetInSeconds);
                return;
            }

            counter.count++;

            httpResponse.setHeader(
                    RATE_LIMIT_LIMIT,
                    Integer.toString(limit));

            httpResponse.setHeader(
                    RATE_LIMIT_REMAINING,
                    Integer.toString(Math.max(0, limit - counter.count)));

            httpResponse.setHeader(
                    RATE_LIMIT_RESET,
                    Long.toString(resetInSeconds));
        }

        chain.doFilter(request, response);
    }

    /**
     * Excludes preflight requests and health/monitoring endpoints from
     * application-level rate limiting.
     *
     * Monitoring endpoints should still be protected by network or
     * authentication controls as appropriate.
     */
    private static boolean isExempt(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        return "/health".equals(path)
                || "/actuator".equals(path)
                || path.startsWith("/actuator/");
    }

    /**
     * Authentication endpoints receive a stricter per-IP limit.
     */
    private static boolean isSensitiveAuthenticationPath(String path) {
        return "/users/login".equals(path)
                || "/users/register".equals(path)
                || "/users/refresh".equals(path)
                || "/users/logout".equals(path)
                || "/users/logout-all".equals(path)
                || "/users/verify-email".equals(path)
                || "/users/resend-verification".equals(path)
                || "/users/forgot-password".equals(path)
                || "/users/reset-password".equals(path);
    }

    /**
     * Trust X-Real-IP only if a trusted gateway overwrites it.
     * Otherwise, clients may spoof this header to bypass IP-based limits.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }

        String remoteAddress = request.getRemoteAddr();

        return remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress;
    }

    /**
     * Returns HTTP 429 with retry information when the request limit
     * has been exhausted.
     */
    private static void writeRateLimitExceededResponse(
            HttpServletResponse response,
            int limit,
            long retryAfterSeconds) throws IOException {

        response.setStatus(HTTP_TOO_MANY_REQUESTS);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.setHeader(
                "Retry-After",
                Long.toString(retryAfterSeconds));

        response.setHeader(
                RATE_LIMIT_LIMIT,
                Integer.toString(limit));

        response.setHeader(
                RATE_LIMIT_REMAINING,
                "0");

        response.setHeader(
                RATE_LIMIT_RESET,
                Long.toString(retryAfterSeconds));

        response.getWriter().write(RATE_LIMIT_EXCEEDED_JSON);
    }

    /**
     * Periodically removes inactive counter entries to prevent the
     * in-memory map from growing indefinitely.
     */
    private void evictStaleEntries() {
        long now = System.currentTimeMillis();

        counters.entrySet().removeIf(entry -> {
            RequestCounter counter = entry.getValue();

            synchronized (counter) {
                return now >= counter.lastSeen
                        && now - counter.lastSeen > EVICT_AFTER_MS;
            }
        });
    }

    private static final class RequestCounter {

        private int count;
        private long windowStart;
        private long lastSeen;

        private RequestCounter(long now) {
            this.windowStart = now;
            this.lastSeen = now;
        }
    }
}