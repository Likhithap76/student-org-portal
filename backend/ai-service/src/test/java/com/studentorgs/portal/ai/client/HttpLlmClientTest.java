package com.studentorgs.portal.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studentorgs.portal.ai.exception.LlmInvalidOutputException;
import com.studentorgs.portal.ai.exception.LlmUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HttpLlmClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final List<Reply> replies = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        replies.clear();
        requests.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", this::respond);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(requestBody.contains("llama3.2:3b"));
            int index = requests.getAndIncrement();
            Reply reply = replies.isEmpty() ? new Reply(500, "error")
                    : replies.get(Math.min(index, replies.size() - 1));
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private HttpLlmClient client() {
        return new HttpLlmClient(MAPPER, baseUrl, "llama3.2:3b", Duration.ofSeconds(5));
    }

    private String ollamaResponse(String modelJson) throws Exception {
        return MAPPER.writeValueAsString(java.util.Map.of("response", modelJson));
    }

    @Test
    void validOllamaResponseMapsToMeetingSummary() throws Exception {
        replies.add(new Reply(200, ollamaResponse("""
                {"summary":"The group planned a workshop.",
                 "decisions":["Hold a workshop"],
                 "actionItems":[{"description":"Book room","assigneeName":"Sarah","dueDate":"2026-11-04"}]}
                """)));
        MeetingSummary result = client().summarize("Sarah will book a room for the workshop by Nov 4, 2026.");
        assertEquals("The group planned a workshop.", result.summary());
        assertEquals(List.of("Hold a workshop"), result.decisions());
        assertEquals("Book room", result.actionItems().get(0).description());
        assertEquals("Sarah", result.actionItems().get(0).assigneeName());
        assertEquals(LocalDate.of(2026, 11, 4), result.actionItems().get(0).dueDate());
        assertEquals(1, requests.get());
    }

    @Test
    void retriesOneInvalidModelAnswer() throws Exception {
        replies.add(new Reply(200, ollamaResponse("{\"summary\":\"\",\"decisions\":[],\"actionItems\":[]}")));
        replies.add(new Reply(200, ollamaResponse("{\"summary\":\"Meeting done\",\"decisions\":[],\"actionItems\":[]}")));
        assertEquals("Meeting done", client().summarize("Meeting finished.").summary());
        assertEquals(2, requests.get());
    }

    @Test
    void rejectsRepeatedInvalidOutput() throws Exception {
        replies.add(new Reply(200, ollamaResponse("{\"summary\":42,\"decisions\":[],\"actionItems\":[]}")));
        assertThrows(LlmInvalidOutputException.class, () -> client().summarize("Notes"));
        assertEquals(2, requests.get());
    }

    @Test
    void providerHttpErrorIsUnavailable() {
        replies.add(new Reply(503, "unavailable"));
        assertThrows(LlmUnavailableException.class, () -> client().summarize("Notes"));
        assertEquals(1, requests.get());
    }

    @Test
    void missingConfigurationIsUnavailable() {
        HttpLlmClient unconfigured = new HttpLlmClient(MAPPER, "", "", Duration.ofSeconds(5));
        assertThrows(LlmUnavailableException.class, () -> unconfigured.summarize("Notes"));
        assertEquals(0, requests.get());
    }

    @Test
    void inventedYearIsDiscardedWhenNotesDoNotSpecifyYear() throws Exception {
        replies.add(new Reply(200, ollamaResponse("""
                {"summary":"The committee planned a workshop and assigned the room reservation to Sarah.",
                 "decisions":["Hold a workshop"],
                 "actionItems":[{"description":"Book room","assigneeName":"Sarah","dueDate":"2024-11-10"}]}
                """)));
        MeetingSummary result = client().summarize("Sarah will book the room by November 10.");
        assertNull(result.actionItems().get(0).dueDate());
    }

    @Test
    void preservesMultipleAssignedTasksWithExplicitDates() throws Exception {
        replies.add(new Reply(200, ollamaResponse("""
                {"summary":"The technology committee scheduled a workshop and assigned preparations.",
                 "decisions":["Hold coding workshop on November 20"],
                 "actionItems":[
                    {"description":"Reserve classroom","assigneeName":"Sarah","dueDate":"2026-11-10"},
                    {"description":"Prepare slides","assigneeName":"John","dueDate":"2026-11-15"}]}
                """)));
        MeetingSummary result = client().summarize(
                "On November 5, 2026 the committee planned a workshop. "
              + "Sarah will reserve a classroom by November 10, 2026. "
              + "John will prepare slides by November 15, 2026.");
        assertEquals(2, result.actionItems().size());
        assertEquals("Sarah", result.actionItems().get(0).assigneeName());
        assertEquals("John", result.actionItems().get(1).assigneeName());
        assertEquals(LocalDate.of(2026, 11, 15), result.actionItems().get(1).dueDate());
    }

    @Test
    void genericHeadingIsRejectedAndRetried() throws Exception {
        replies.add(new Reply(200, ollamaResponse("""
                {"summary":"Technology Committee Meeting Notes","decisions":[],"actionItems":[]}
                """)));
        replies.add(new Reply(200, ollamaResponse("""
                {"summary":"The committee discussed a coding workshop and agreed to host it.",
                 "decisions":["Host a coding workshop"],"actionItems":[]}
                """)));
        assertEquals("The committee discussed a coding workshop and agreed to host it.",
                client().summarize("The committee agreed to host a coding workshop.").summary());
        assertEquals(2, requests.get());
    }

    private record Reply(int status, String body) {}
}
