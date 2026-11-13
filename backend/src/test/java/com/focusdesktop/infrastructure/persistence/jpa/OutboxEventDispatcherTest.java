package com.focusdesktop.infrastructure.persistence.jpa;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import com.focusdesktop.infrastructure.web.SessionEventEnvelope;
import com.focusdesktop.infrastructure.web.SessionEventBroadcaster;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("8e6d12aa-9d8c-467e-9fd7-ec43b32ee362");

    @Mock private OutboxDeliveryStore outbox;
    @Mock private FocusSessionRepository sessions;
    private final RecordingBroadcaster websocket = new RecordingBroadcaster();

    private OutboxEventDispatcher dispatcher;
    private PendingOutboxEvent pending;
    private FocusSession session;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ObjectMapper mapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        dispatcher = new OutboxEventDispatcher(outbox, sessions, websocket, clock, mapper);
        websocket.events.clear();
        websocket.failure = null;
        session = FocusSessionFactory.create(settings(), new TimeStamp(NOW));
        session.start(new TimeStamp(NOW));
        sessionId = session.id().value();
        pending = new PendingOutboxEvent(EVENT_ID, "SessionStarted", sessionId, NOW,
                "{\"sessionId\":\"" + sessionId + "\"}", 0);
    }

    @Test
    void broadcastsSessionSnapshotAndMarksCommittedEventDelivered() {
        when(outbox.findDue(NOW, 100)).thenReturn(List.of(pending));
        when(sessions.findById(new SessionId(sessionId))).thenReturn(Optional.of(session));

        dispatcher.dispatchPendingEvents();

        org.junit.jupiter.api.Assertions.assertEquals(1, websocket.events.size());
        org.junit.jupiter.api.Assertions.assertEquals(EVENT_ID, websocket.events.get(0).eventId());
        verify(outbox).markDelivered(EVENT_ID, NOW);
        verify(outbox, never()).recordFailure(eq(EVENT_ID), any(), any());
    }

    @Test
    void failedBroadcastLeavesEventPendingAndSchedulesExponentialRetry() {
        when(outbox.findDue(NOW, 100)).thenReturn(List.of(pending));
        when(sessions.findById(new SessionId(sessionId))).thenReturn(Optional.of(session));
        websocket.failure = new IllegalStateException("transport unavailable");

        dispatcher.dispatchPendingEvents();

        verify(outbox).recordFailure(EVENT_ID, NOW.plusSeconds(1),
                "IllegalStateException: transport unavailable");
        verify(outbox, never()).markDelivered(eq(EVENT_ID), any());
    }

    @Test
    void capsRetryDelayAtOneMinute() {
        PendingOutboxEvent repeatedlyFailed = new PendingOutboxEvent(EVENT_ID, "SessionStarted", sessionId,
                NOW, pending.payload(), 10);
        when(outbox.findDue(NOW, 100)).thenReturn(List.of(repeatedlyFailed));
        when(sessions.findById(new SessionId(sessionId))).thenReturn(Optional.of(session));
        websocket.failure = new IllegalStateException("transport unavailable");

        dispatcher.dispatchPendingEvents();

        verify(outbox).recordFailure(EVENT_ID, NOW.plusSeconds(60),
                "IllegalStateException: transport unavailable");
    }

    @Test
    void processesPendingEventsInStoreOrder() {
        PendingOutboxEvent second = new PendingOutboxEvent(UUID.randomUUID(), "SessionPaused", sessionId,
                NOW.plusSeconds(1), "{}", 0);
        when(outbox.findDue(NOW, 100)).thenReturn(List.of(pending, second));
        when(sessions.findById(new SessionId(sessionId))).thenReturn(Optional.of(session));

        dispatcher.dispatchPendingEvents();

        org.junit.jupiter.api.Assertions.assertEquals(2, websocket.events.size());
        InOrder order = inOrder(outbox);
        order.verify(outbox).markDelivered(EVENT_ID, NOW);
        order.verify(outbox).markDelivered(second.eventId(), NOW);
    }

    @Test
    void emptyPendingQueueDoesNothing() {
        when(outbox.findDue(NOW, 100)).thenReturn(List.of());

        dispatcher.dispatchPendingEvents();

        verify(sessions, never()).findById(any());
        org.junit.jupiter.api.Assertions.assertEquals(0, websocket.events.size());
    }

    private static SessionSettings settings() {
        return new SessionSettings(Duration.ofSeconds(600), Duration.ofSeconds(300),
                Duration.ofSeconds(600), 4, false, false);
    }

    private static final class RecordingBroadcaster implements SessionEventBroadcaster {
        private final java.util.ArrayList<SessionEventEnvelope> events = new java.util.ArrayList<>();
        private RuntimeException failure;

        @Override
        public void broadcast(SessionEventEnvelope event) {
            if (failure != null) {
                throw failure;
            }
            events.add(event);
        }
    }
}
