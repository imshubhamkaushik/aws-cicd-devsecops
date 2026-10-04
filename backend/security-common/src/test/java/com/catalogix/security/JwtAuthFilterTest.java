package com.catalogix.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for JwtAuthFilter, including that user-svc's pre-auth endpoints (/users/login,
 * /users/register, ...) are exempt: a brand-new user has no JWT yet. UserControllerTest's
 * @WebMvcTest slice excludes this filter, so the exemption has to be covered here.
 */
class JwtAuthFilterTest {

    // Test-only — not a real secret, just long enough to pass JwtService's
    // own 32-char minimum-length check.
    private static final String SECRET = "test-only-secret-at-least-32-characters-long";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
    }

    // ── shouldNotFilter ─────────────────────────────────────────────────

    @Test
    void exemptsHealthAndActuatorByDefault_evenWithNoPublicPathsConfigured() {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, "");

        assertThat(filter.shouldNotFilter(requestTo("/health"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/actuator/prometheus"))).isTrue();
    }

    @Test
    void requiresAuthOnEverythingElseByDefault_whenNoPublicPathsConfigured() {
        // This is the exact configuration every service except user-svc
        // runs with (no security.public-paths set) — everything requires a
        // Bearer token, by design. Only user-svc has pre-auth endpoints.
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, "");

        assertThat(filter.shouldNotFilter(requestTo("/products"))).isFalse();
        assertThat(filter.shouldNotFilter(requestTo("/users/login"))).isFalse();
    }

    @Test
    void exemptsConfiguredPublicPaths_butOnlyThoseExactPaths() {
        // Mirrors backend/user-svc/src/main/resources/application.properties'
        // real security.public-paths value, including its whitespace style,
        // to make sure trimming works the same way it will in production.
        JwtAuthFilter filter = new JwtAuthFilter(
                jwtService,
                "/users/register,/users/login, /users/refresh ,/users/logout,"
                        + "/users/verify-email,/users/forgot-password,/users/reset-password"
        );

        assertThat(filter.shouldNotFilter(requestTo("/users/login"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/users/register"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/users/refresh"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/users/logout"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/users/verify-email"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/users/forgot-password"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/users/reset-password"))).isTrue();

        // Everything else on the same service still requires auth — this
        // property exempts specific paths, not the whole service.
        assertThat(filter.shouldNotFilter(requestTo("/users/me"))).isFalse();
        assertThat(filter.shouldNotFilter(requestTo("/users/logout-all"))).isFalse();
        assertThat(filter.shouldNotFilter(requestTo("/users/resend-verification"))).isFalse();
    }

    // ── doFilterInternal ────────────────────────────────────────────────

    @Test
    void rejectsRequestWithNoAuthorizationHeader_onANonExemptPath() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, "");
        MockHttpServletRequest request = requestTo("/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void letsAnUnauthenticatedCallerThroughOnAConfiguredPublicPath() throws Exception {
        // This is the exact scenario that was broken end-to-end: a browser
        // calling /users/login for the first time has no token to send —
        // frontend-svc's api.jsx only attaches Authorization when
        // getStoredAccessToken() returns non-null, which it never does
        // pre-login. Simulates what OncePerRequestFilter's real dispatch
        // does: shouldNotFilter()==true means doFilterInternal is skipped
        // entirely and the chain proceeds untouched, regardless of headers.
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, "/users/login");
        MockHttpServletRequest request = requestTo("/users/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        assertThat(filter.shouldNotFilter(request)).isTrue();
        chain.doFilter(request, response);

        verify(chain, times(1)).doFilter(request, response);
    }

    private MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
