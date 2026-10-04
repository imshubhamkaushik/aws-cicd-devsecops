package com.catalogix.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** A browser's CORS preflight carries no Authorization header and must not be rejected as unauthenticated. */
class JwtAuthFilterCorsPreflightTest {

    private final JwtAuthFilter filter = new JwtAuthFilter(
            new JwtService("test-only-secret-at-least-32-characters-long"), "");

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    void exemptsRealPreflightRequests() {
        MockHttpServletRequest preflight = request("OPTIONS", "/products");
        preflight.addHeader("Origin", "http://localhost:5173");
        preflight.addHeader("Access-Control-Request-Method", "POST");

        assertThat(filter.shouldNotFilter(preflight)).isTrue();
    }

    @Test
    void doesNotExemptPlainOptionsWithoutPreflightHeaders() {
        assertThat(filter.shouldNotFilter(request("OPTIONS", "/products"))).isFalse();
    }

    @Test
    void doesNotExemptOtherMethodsThatCarryPreflightLookingHeaders() {
        MockHttpServletRequest post = request("POST", "/products");
        post.addHeader("Origin", "http://localhost:5173");
        post.addHeader("Access-Control-Request-Method", "POST");

        assertThat(filter.shouldNotFilter(post)).isFalse();
    }
}
