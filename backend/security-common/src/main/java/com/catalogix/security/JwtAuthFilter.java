package com.catalogix.security;

import tools.jackson.databind.json.JsonMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates the "Authorization: Bearer <token>" header on every request
 * except health/metrics and each service's own declared public paths (see
 * {@code security.public-paths}). The caller's identity comes only from a
 * signature-verified token minted by user-svc, or a system token minted by
 * whichever service is making an unattended background call (see
 * JwtService's Javadoc).
 */
@Component
@Order(2)
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JsonMapper mapper = new JsonMapper();
    private final List<String> publicPaths;

    // Deliberately empty by default: only a service with pre-auth endpoints
    // of its own (currently just user-svc — login, register, refresh,
    // logout, verify-email, forgot-password, reset-password all have to be
    // reachable by a caller who doesn't have a token yet) needs to set
    // this. Every other service correctly requires a Bearer token on every
    // path, so leaving this unset for them is the right default, not an
    // oversight — see application.properties in whichever service sets it
    // for the specific list.
    public JwtAuthFilter(JwtService jwtService, @Value("${security.public-paths:}") String publicPathsCsv) {
        this.jwtService = jwtService;
        this.publicPaths = Arrays.stream(publicPathsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health")
                || path.startsWith("/actuator")
                || publicPaths.contains(path)
                || isCorsPreflight(request);
    }

    /**
     * A browser's CORS preflight (OPTIONS + Origin + Access-Control-Request-Method)
     * never carries an Authorization header, so requiring one made every cross-origin
     * call to a protected endpoint fail before Spring's CORS handling could answer.
     * Letting it through exposes nothing: a preflight has no body and Spring only
     * replies with the CORS headers (or rejects it if the origin is not allowed).
     */
    private static boolean isCorsPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Origin") != null
                && request.getHeader("Access-Control-Request-Method") != null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring(7).trim();
        try {
            Claims claims = jwtService.parseClaims(token);
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            request.setAttribute("userEmail", claims.get("email", String.class));
            request.setAttribute("userRole", claims.get("role", String.class));
            request.setAttribute("bearerToken", header);
        } catch (JwtException | IllegalArgumentException e) {
            reject(response, "Invalid or expired token");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("timestamp", Instant.now().toString());
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
