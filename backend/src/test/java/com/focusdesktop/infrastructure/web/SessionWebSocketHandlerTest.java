package com.focusdesktop.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class SessionWebSocketHandlerTest {
    @Test
    void broadcastsVersionedEnvelopeWithStableEventIdAndSessionSnapshot() throws Exception {
        ObjectMapper mapper = objectMapper();
        SessionWebSocketHandler handler = new SessionWebSocketHandler(mapper);
        FakeWebSocketSession client = new FakeWebSocketSession();
        handler.afterConnectionEstablished(client);
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        SessionResponse snapshot = new SessionResponse(sessionId, "WORK", "RUNNING", "WORK", "RUNNING",
                1, 600, 0, 0, 600, Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"), null);

        handler.broadcast(new SessionEventEnvelope(eventId, 1, "session.started",
                Instant.parse("2026-01-01T10:00:00Z"), snapshot));

        var json = mapper.readTree(client.message.getPayload());
        assertEquals(eventId.toString(), json.path("eventId").asText());
        assertEquals(1, json.path("version").asInt());
        assertEquals("session.started", json.path("type").asText());
        assertEquals(sessionId.toString(), json.path("payload").path("sessionId").asText());
        assertEquals("RUNNING", json.path("payload").path("state").asText());
    }

    @Test
    void acceptsBroadcastWhenNoClientIsConnected() {
        SessionWebSocketHandler handler = new SessionWebSocketHandler(objectMapper());
        SessionResponse snapshot = new SessionResponse(UUID.randomUUID(), "WORK", "COMPLETED", "WORK",
                "COMPLETED", 1, 600, 600, 600, 0, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);

        handler.broadcast(new SessionEventEnvelope(UUID.randomUUID(), 1, "session.completed",
                Instant.EPOCH, snapshot));
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private TextMessage message;
        private boolean open = true;

        @Override public String getId() { return "test-session"; }
        @Override public URI getUri() { return URI.create("ws://localhost/ws"); }
        @Override public HttpHeaders getHandshakeHeaders() { return HttpHeaders.EMPTY; }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }
        @Override public Principal getPrincipal() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<WebSocketExtension> getExtensions() { return List.of(); }
        @Override public void sendMessage(WebSocketMessage<?> message) { this.message = (TextMessage) message; }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
        @Override public void close(CloseStatus status) throws IOException { close(); }
    }
}
