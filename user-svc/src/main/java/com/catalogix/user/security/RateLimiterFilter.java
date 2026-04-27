package com.catalogix.user.security;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimiterFilter implements Filter {

    private static final long WINDOW_MS = 60000; // 1 minute in ms
    private static final int MAX_REQUESTS = 30; // 30 requests per min
    private static final long EVICT_AFTER  = 2 * WINDOW_MS; // evict after 2 idle windows

    // Key → per-client request counter. Entries are created on first request
    // and swept by the scheduler when they have been idle for EVICT_AFTER ms.
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
        // Sweep every 2 minutes. Delay 2 minutes before first run so the map
        // has time to accumulate entries worth evicting.
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
                res.setStatus(429); // Too Many Requests
                res.setContentType("text/plain");
                res.getWriter().write("Rate limit exceeded");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    
    /**
     * Returns the real client IP address.
     *
     * Behind an AWS ALB the original client IP is in the X-Forwarded-For header.
     * XFF format: "client, proxy1, proxy2". The first element is always the
     * original client. getRemoteAddr() would return the ALB's own IP, making
     * every user share a single rate-limit bucket.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the first (leftmost) address — that is the originating client.
            return xff.split(",")[0].strip();
        }
        // Fallback for direct connections (local dev, integration tests, etc.)
        return request.getRemoteAddr();
    }

    /**
     * Removes entries that have not been updated within the last EVICT_AFTER ms.
     * Called by the eviction scheduler on a background daemon thread.
     */
    private void evictStaleEntries() {
        long now = Instant.now().toEpochMilli();
        ipStore.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return (now - entry.getValue().windowStart) > EVICT_AFTER;
            }
        });
    }
    
    // Visible for testing.
    private static class RequestCounter {
        int count;
        long windowStart;

        RequestCounter(int count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }
}
