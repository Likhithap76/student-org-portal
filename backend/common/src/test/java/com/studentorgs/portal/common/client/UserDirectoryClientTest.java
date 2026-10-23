package com.studentorgs.portal.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.ServiceUnavailableException;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

/** Runs against a tiny JDK HTTP server that imitates user-service's internal endpoints. */
class UserDirectoryClientTest {

    private HttpServer server;
    private UserDirectoryClient client;
    private final List<String> batchQueries = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/users/7/status", ex -> respond(ex, 200, "{\"status\":\"ACTIVE\",\"role\":\"OFFICER_ADMIN\"}"));
        server.createContext("/internal/users/404/status", ex -> respond(ex, 404, "{}"));
        server.createContext("/internal/users/500/status", ex -> respond(ex, 500, "{}"));
        server.createContext("/internal/users/batch", ex -> {
            String query = ex.getRequestURI().getQuery();
            batchQueries.add(query);
            String id = query.split("=")[1].split(",")[0].split("&")[0];
            respond(ex, 200, "[{\"id\":" + id + ",\"displayName\":\"User " + id + "\"}]");
        });
        server.createContext("/internal/users/active-ids", ex -> respond(ex, 200, "[1,7]"));
        server.start();
        client = new UserDirectoryClient(RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort()).build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void respond(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    @Test
    void status_knownUser_returnsStatusAndRole() {
        assertThat(client.status(7L)).contains(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN));
    }

    @Test
    void status_unknownUser_returnsEmpty() {
        assertThat(client.status(404L)).isEmpty();
    }

    @Test
    void status_serverError_throwsServiceUnavailable() {
        assertThatThrownBy(() -> client.status(500L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void status_serviceDown_throwsServiceUnavailable() {
        UserDirectoryClient down = new UserDirectoryClient(RestClient.builder().baseUrl("http://localhost:1").build());

        assertThatThrownBy(() -> down.status(7L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void names_moreThanBatchSize_splitsIntoChunks() {
        List<Long> ids = LongStream.rangeClosed(1, 150).boxed().toList();

        var names = client.names(ids);

        assertThat(batchQueries).hasSize(2);
        assertThat(names).containsEntry(1L, "User 1").containsEntry(101L, "User 101");
    }

    @Test
    void names_empty_makesNoCall() {
        assertThat(client.names(List.of())).isEmpty();
        assertThat(batchQueries).isEmpty();
    }

    @Test
    void activeIds_returnsIds() {
        assertThat(client.activeIds()).containsExactly(1L, 7L);
    }

    @Test
    void names_serviceDown_throwsServiceUnavailable() {
        UserDirectoryClient down = new UserDirectoryClient(RestClient.builder().baseUrl("http://localhost:1").build());

        assertThatThrownBy(() -> down.names(List.of(1L))).isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(down::activeIds).isInstanceOf(ServiceUnavailableException.class);
    }
}
