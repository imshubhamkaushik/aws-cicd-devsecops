package com.catalogix.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimiterFilterTest {

    private RateLimiterFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimiterFilter(true, 2, 1);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        filter.shutdown();
    }

    @Test
    void generalRequestsUseConfiguredLimitAndReturnRetryHeaders() throws Exception {
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", "/products", "192.0.2.10"), response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/products", "192.0.2.10"), limited, chain);
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getHeader("RateLimit-Limit")).isEqualTo("2");
        assertThat(limited.getHeader("RateLimit-Remaining")).isEqualTo("0");
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticationPathsHaveTheirOwnStricterBucket() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/users/login", "192.0.2.11"), first, chain);
        assertThat(first.getStatus()).isNotEqualTo(429);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/users/login", "192.0.2.11"), second, chain);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("RateLimit-Limit")).isEqualTo("1");

        // Authentication and general traffic are independent buckets.
        MockHttpServletResponse general = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/products", "192.0.2.11"), general, chain);
        assertThat(general.getStatus()).isNotEqualTo(429);
    }

    @Test
    void healthChecksAndCorsPreflightAreExempt() throws Exception {
        for (MockHttpServletRequest req : new MockHttpServletRequest[] {
                request("GET", "/health", "192.0.2.12"),
                request("GET", "/actuator/health", "192.0.2.12"),
                request("OPTIONS", "/users/login", "192.0.2.12")
        }) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(req, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void xForwardedForIsNotTrustedAsAnArbitraryClientIdentity() throws Exception {
        MockHttpServletRequest first = request("GET", "/products", "198.51.100.7");
        first.addHeader("X-Forwarded-For", "203.0.113.1");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, chain);

        MockHttpServletRequest second = request("GET", "/products", "198.51.100.7");
        second.addHeader("X-Forwarded-For", "203.0.113.2");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, chain);

        MockHttpServletRequest third = request("GET", "/products", "198.51.100.7");
        third.addHeader("X-Forwarded-For", "203.0.113.3");
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(third, thirdResponse, chain);
        assertThat(thirdResponse.getStatus()).isEqualTo(429);
    }

    private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
