package com.focusdesktop.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class SessionWebSocketHandler extends TextWebSocketHandler implements SessionEventBroadcaster {
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> clients = ConcurrentHashMap.newKeySet();

    public SessionWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        clients.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session);
    }

    @Override
    public void broadcast(SessionEventEnvelope event) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize session WebSocket event", exception);
        }

        TextMessage message = new TextMessage(json);
        IOException firstFailure = null;
        for (WebSocketSession client : clients) {
            if (!client.isOpen()) {
                clients.remove(client);
                continue;
            }
            try {
                synchronized (client) {
                    if (client.isOpen()) {
                        client.sendMessage(message);
                    }
                }
            } catch (IOException exception) {
                clients.remove(client);
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
                try {
                    client.close(CloseStatus.SERVER_ERROR);
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException("Could not broadcast session event to every connected client",
                    firstFailure);
        }
    }
}
