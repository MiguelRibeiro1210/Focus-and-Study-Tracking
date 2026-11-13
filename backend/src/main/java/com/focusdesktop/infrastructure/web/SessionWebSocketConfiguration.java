package com.focusdesktop.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class SessionWebSocketConfiguration implements WebSocketConfigurer {
    private final SessionWebSocketHandler handler;

    public SessionWebSocketConfiguration(SessionWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*",
                        "http://127.0.0.1:*", "https://127.0.0.1:*", "http://[::1]:*",
                        "https://[::1]:*", "tauri://localhost", "http://tauri.localhost",
                        "https://tauri.localhost");
    }
}
