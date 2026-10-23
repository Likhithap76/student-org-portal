package com.studentorgs.portal.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.studentorgs.portal.common.web.RequestIdFilter;

class NotificationClientTest {

    private static final NotificationRequest REQUEST = new NotificationRequest(
            List.of(1L, 2L), NotificationKind.TASK_ASSIGNED, "You have a new task", "task", 9L);

    private MockRestServiceServer server;
    private NotificationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NotificationClient(builder.baseUrl("http://notifications")
                .defaultHeader("X-Internal-Token", "secret").build());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void send_serviceAccepts_returnsTrue() {
        server.expect(requestTo("http://notifications/internal/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "secret"))
                .andExpect(content().json("{\"recipientIds\":[1,2],\"kind\":\"task_assigned\",\"referenceTable\":\"task\",\"referenceId\":9}"))
                .andRespond(withSuccess());

        assertThat(client.send(REQUEST)).isTrue();
        server.verify();
    }

    @Test
    void send_serviceFails_returnsFalseWithoutThrowing() {
        server.expect(requestTo("http://notifications/internal/notifications")).andRespond(withServerError());

        assertThat(client.send(REQUEST)).isFalse();
    }

    @Test
    void send_noRecipients_skipsCall() {
        assertThat(client.send(new NotificationRequest(List.of(), NotificationKind.ANNOUNCEMENT, "m", "announcement", 1L)))
                .isTrue();
        server.verify();
    }

    @Test
    void send_serviceUnreachable_returnsFalse() {
        NotificationClient unreachable = new NotificationClient(
                new InternalRestClientFactory(RestClient.builder(), "secret").create("http://localhost:1"));
        MDC.put(RequestIdFilter.MDC_KEY, "req-1");

        assertThat(unreachable.send(REQUEST)).isFalse();
    }
}
