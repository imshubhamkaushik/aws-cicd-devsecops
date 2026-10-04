package com.catalogix.user.integration;

import com.catalogix.user.UserSvcApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check through the real filter chain: a brand-new user has no JWT, so JwtAuthFilter
 * must let /users/register and /users/login through. Slice tests exclude the filter, so only a
 * real request can catch a mistake in filter wiring.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
        classes = UserSvcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
class AuthFlowIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("ALLOWED_ORIGINS", () -> "http://localhost:11000");

        registry.add(
                "JWT_SECRET",
                () -> "svfmhSAWW6kcsgGiSSz1eOoQDK1ku6+crVQPJHo+XmZxFoj7ujud7ImW4+e3RFW8"
        );

        // application-test.properties (the default "test" profile) excludes
        // DataSourceAutoConfiguration/HibernateJpaAutoConfiguration/
        // FlywayAutoConfiguration entirely, because every OTHER test in
        // this service is a slice test that doesn't want a real DB. This
        // class is the exception — it needs the real thing, migrated by
        // the real Flyway scripts in src/main/resources/db/migration, the
        // same way production does. Re-enabling here rather than editing
        // that shared file, so the 866+ lines of existing slice tests stay
        // exactly as fast and DB-free as they were.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "never");

        // security.public-paths isn't set in application-test.properties at
        // all (it's user-svc's own application.properties that sets it,
        // and Spring profile precedence means "test" wouldn't automatically
        // inherit it here) — set it explicitly so this test exercises the
        // real production configuration rather than accidentally testing
        // against JwtAuthFilter's permissive-if-unset-everywhere default.
        registry.add("security.public-paths", () ->
                "/users/register,/users/login,/users/refresh,/users/logout,"
                        + "/users/verify-email,/users/forgot-password,/users/reset-password");

        // No RabbitMQ container: UserEventPublisher wraps its
        // rabbitTemplate.convertAndSend() call in try/catch and only logs
        // on failure (see that class), so register()/login() succeeding
        // doesn't depend on a broker being reachable. Confirmed by reading
        // that class directly, not assumed.
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void aBrandNewUserCanRegisterAndThenLogIn_withNoAuthorizationHeaderOnEitherCall() {
        // TestRestTemplate sends no Authorization header unless told to, exactly like a browser
        // for a user who has never logged in.

        String email = "fresh-" + System.nanoTime() + "@example.com";

        Map<String, String> registerBody = Map.of(
                "name", "Fresh User",
                "email", email,
                "password", "Password1"
        );
        ResponseEntity<Map<String, Object>> registerResponse =
                rest.exchange(
                "/users/register",
                HttpMethod.POST,
                new HttpEntity<>(registerBody),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        System.out.println("REGISTER STATUS = " + registerResponse.getStatusCode().value());
        System.out.println("REGISTER BODY   = " + registerResponse.getBody());

        assertThat(registerResponse.getStatusCode().value())
                .as("registration must succeed with no Authorization header — "
                        + "this is the exact call that used to 401")
                .isEqualTo(201);
        assertThat(registerResponse.getBody()).containsKey("accessToken");

        Map<String, String> loginBody = Map.of("email", email, "password", "Password1");
        
        ResponseEntity<Map<String, Object>> loginResponse =
                rest.exchange(
                        "/users/login",
                        HttpMethod.POST,
                        new HttpEntity<>(loginBody),
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        assertThat(loginResponse.getStatusCode().value())
                .as("login must succeed with no Authorization header — "
                        + "this is the exact call that used to 401")
                .isEqualTo(200);
        assertThat(loginResponse.getBody()).containsKey("accessToken");
    }

    @Test
    void theFixDoesNotFailOpen_protectedEndpointsStillRejectRequestsWithNoToken() {
        // The other half of this regression test: shouldNotFilter's new
        // publicPaths check must exempt only the exact configured paths,
        // not accidentally the whole service. /users/me is deliberately
        // not in security.public-paths.
        ResponseEntity<Map<String, Object>> response =
        rest.exchange(
                "/users/me",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void loginWithWrongPasswordStillFails_thePublicPathDoesNotBypassCredentialChecking() {
        // Exempting /users/login from JwtAuthFilter must not be confused
        // with exempting it from actually checking the password —
        // shouldNotFilter only controls whether a Bearer token is
        // required, not what UserSvc.login() does once the request
        // arrives.
        String email = "wrongpass-" + System.nanoTime() + "@example.com";
        rest.exchange(
                "/users/register",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Wrong Pass",
                                "email", email,
                                "password", "Password1"
                        )
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        ResponseEntity<Map<String, Object>> response =
        rest.exchange(
                "/users/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "email", email,
                                "password", "DefinitelyNotThePassword1"
                        )
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
