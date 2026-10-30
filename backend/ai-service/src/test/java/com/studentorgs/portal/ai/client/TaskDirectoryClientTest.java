package com.studentorgs.portal.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.studentorgs.portal.ai.client.TaskDirectoryClient.CommitteeMember;
import com.studentorgs.portal.common.client.InternalRestClientFactory;
import com.studentorgs.portal.common.config.OrgPortalProperties;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.error.ServiceUnavailableException;

/** Runs against a tiny JDK HTTP server that imitates task-service's internal endpoint. */
class TaskDirectoryClientTest {

    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/committees/5/members",
                ex -> respond(ex, 200, "[{\"userId\":2,\"displayName\":\"Ana Lopez\"}]"));
        server.createContext("/internal/committees/9/members", ex -> respond(ex, 404, "{}"));
        server.createContext("/internal/committees/7/members", ex -> respond(ex, 500, "{}"));
        server.start();
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

    private TaskDirectoryClient client(String taskUrl) {
        OrgPortalProperties properties = new OrgPortalProperties(null,
                new OrgPortalProperties.Services(null, null, taskUrl, null, null, null, null), null, ZoneId.of("UTC"));
        return new TaskDirectoryClient(new InternalRestClientFactory(RestClient.builder(), "secret"), properties);
    }

    private String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void committeeMembers_known_returnsMembers() {
        assertThat(client(url()).committeeMembers(5L)).containsExactly(new CommitteeMember(2L, "Ana Lopez"));
    }

    @Test
    void committeeMembers_unknown_throwsNotFound() {
        assertThatThrownBy(() -> client(url()).committeeMembers(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void committeeMembers_serverError_throws503() {
        assertThatThrownBy(() -> client(url()).committeeMembers(7L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void committeeMembers_serviceDown_throws503() {
        assertThatThrownBy(() -> client("http://localhost:1").committeeMembers(5L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
