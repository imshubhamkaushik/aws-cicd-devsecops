package com.catalogix.product.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterFilter implements Filter {

    private static final long WINDOW_MS = 60000; // 1 minute
    private static final int MAX_REQUESTS = 30; // 30 requests per min

    private final Map<String, RequestCounter> ipStore = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String ip = httpRequest.getRemoteAddr();
        long now = Instant.now().toEpochMilli();

        ipStore.putIfAbsent(ip, new RequestCounter(0, now));

        RequestCounter counter = ipStore.get(ip);

        synchronized (counter) {
            if (now - counter.windowStart >= WINDOW_MS) {
                counter.windowStart = now;
                counter.count = 0;
            }

            counter.count++;

            if (counter.count > MAX_REQUESTS) {
                HttpServletResponse res = (HttpServletResponse) response;
                res.setStatus(429); // Too Many Requests
                res.getWriter().write("Rate limit exceeded");
                return;
            }
        }

        chain.doFilter(request, response);
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
