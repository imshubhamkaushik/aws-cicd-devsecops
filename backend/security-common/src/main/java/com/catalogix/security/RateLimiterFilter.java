package com.catalogix.security;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Runs before JwtAuthFilter (@Order(2)) so abusive clients get throttled
// before we spend any effort parsing/verifying their token.
@Component
@Order(1)
public class RateLimiterFilter implements Filter {

    private static final long WINDOW_MS = 60000; // 1 minute in ms
    private static final int MAX_REQUESTS = 30; // 30 requests per min
    private static final long EVICT_AFTER  = 2 * WINDOW_MS; // evict after 2 idle windows

    private final ConcurrentHashMap<String, RequestCounter> ipStore = new ConcurrentHashMap<>();

    private final ScheduledExecutorService evictionScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rate-limiter-eviction");
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    public void shutdown() {
        evictionScheduler.shutdown();
        try {
            if (!evictionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                evictionScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            evictionScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public RateLimiterFilter() {
        evictionScheduler.scheduleAtFixedRate(
                this::evictStaleEntries, 2, 2, TimeUnit.MINUTES
        );
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String ip = resolveClientIp((HttpServletRequest) request);
        long now = Instant.now().toEpochMilli();

        ipStore.putIfAbsent(ip, new RequestCounter(0, now));
        RequestCounter counter = ipStore.computeIfAbsent(ip, k -> new RequestCounter(0, now));

        synchronized (counter) {
            if (now - counter.windowStart >= WINDOW_MS) {
                counter.windowStart = now;
                counter.count = 0;
            }

            counter.count++;

            if (counter.count > MAX_REQUESTS) {
                HttpServletResponse res = (HttpServletResponse) response;
                res.setStatus(429);
                res.setContentType("text/plain");
                res.getWriter().write("Rate limit exceeded. Try again in a moment.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Returns the real client IP address.
     *
     * X-Real-IP is set by the gateway from nginx's own $remote_addr, which is
     * NOT attacker-controllable at that hop — prefer it. X-Forwarded-For is
     * used only as a fallback for direct-to-service calls (e.g. local dev
     * without the gateway in front); note that a caller reaching this
     * service directly can freely spoof XFF, so this fallback is weaker and
     * only relied on outside the gateway path.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private void evictStaleEntries() {
        long now = Instant.now().toEpochMilli();
        ipStore.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return (now - entry.getValue().windowStart) > EVICT_AFTER;
            }
        });
    }

    private static class RequestCounter {
        int count;
        long windowStart;

        RequestCounter(int count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }
}
