package com.studentorgs.portal.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

/** Downstream services are not running here, so allowed requests end in a 5xx from the gateway, never a 401/403. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "orgportal.services.event-url=http://localhost:1")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayApplicationTests {

    @Autowired
    private WebTestClient client;

    @Autowired
    private JwtService jwtService;

    @LocalServerPort
    private int port;

    @Test
    void health_noToken_returnsUp() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void protectedRoute_noToken_returns401Problem() {
        client.get().uri("/api/events").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void protectedRoute_invalidToken_returns401() {
        client.get().uri("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Invalid or expired token");
    }

    @Test
    void protectedRoute_validToken_isForwarded() {
        String token = jwtService.issue(new AuthenticatedUser(1L, "a@uta.edu", "A", Role.MEMBER, UserStatus.ACTIVE));
        HttpStatus status = (HttpStatus) client.get().uri("/api/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().returnResult(Void.class).getStatus();
        assertThat(status.is5xxServerError()).isTrue();
    }

    @Test
    void publicRoute_noToken_isForwarded() {
        HttpStatus status = (HttpStatus) client.post().uri("/api/auth/login").exchange()
                .returnResult(Void.class).getStatus();
        assertThat(status).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void internalPath_viaTraversal_returns403() {
        client.get().uri("/api/users/%2e%2e/internal/users/1/status").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void internalPath_direct_returns403() {
        client.get().uri("/internal/users/active-ids").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void preflight_allowedOrigin_returnsCorsHeaders() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .options().uri("/api/events")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void swaggerConfig_noToken_listsAllServices() {
        client.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.urls.length()").isEqualTo(7);
    }
}
