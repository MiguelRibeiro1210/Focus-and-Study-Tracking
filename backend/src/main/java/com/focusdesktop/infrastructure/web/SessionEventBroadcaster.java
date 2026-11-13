package com.focusdesktop.infrastructure.web;

@FunctionalInterface
public interface SessionEventBroadcaster {
    void broadcast(SessionEventEnvelope event);
}
