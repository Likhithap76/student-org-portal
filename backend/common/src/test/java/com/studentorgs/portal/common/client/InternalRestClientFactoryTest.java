package com.studentorgs.portal.common.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import com.studentorgs.portal.common.web.RequestIdFilter;

class InternalRestClientFactoryTest {

    private HttpServer server;
    private final AtomicReference<Headers> received = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            received.set(exchange.getRequestHeaders());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        MDC.clear();
    }

    @Test
    void create_sendsInternalTokenAndRequestId() {
        RestClient client = new InternalRestClientFactory(RestClient.builder(), "secret")
                .create("http://localhost:" + server.getAddress().getPort());
        MDC.put(RequestIdFilter.MDC_KEY, "req-42");

        client.get().uri("/internal/users/1/status").retrieve().toBodilessEntity();

        assertThat(received.get().getFirst("X-Internal-Token")).isEqualTo("secret");
        assertThat(received.get().getFirst("X-Request-Id")).isEqualTo("req-42");
    }

    @Test
    void create_noRequestIdInMdc_omitsHeader() {
        RestClient client = new InternalRestClientFactory(RestClient.builder(), "secret")
                .create("http://localhost:" + server.getAddress().getPort());

        client.get().uri("/internal/x").retrieve().toBodilessEntity();

        assertThat(received.get().containsKey("X-Request-Id")).isFalse();
    }
}
