package com.focusdesktop.infrastructure.persistence.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.dto.SessionDto;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import com.focusdesktop.infrastructure.web.SessionEventEnvelope;
import com.focusdesktop.infrastructure.web.SessionEventBroadcaster;
import com.focusdesktop.infrastructure.web.SessionResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventDispatcher {
    private static final Logger LOGGER = Logger.getLogger(OutboxEventDispatcher.class.getName());
    private static final int BATCH_SIZE = 100;
    private static final Map<String, String> EVENT_NAMES = Map.of(
            "SessionStarted", "session.started",
            "SessionPaused", "session.paused",
            "SessionResumed", "session.resumed",
            "SessionCompleted", "session.completed",
            "SessionAbandoned", "session.abandoned",
            "IntervalCompleted", "interval.completed",
            "IntervalStarted", "interval.started",
            "BreakSkipped", "break.skipped");

    private final OutboxDeliveryStore outbox;
    private final FocusSessionRepository sessions;
    private final SessionEventBroadcaster websocket;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OutboxEventDispatcher(OutboxDeliveryStore outbox, FocusSessionRepository sessions,
                                 SessionEventBroadcaster websocket, Clock clock,
                                 ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.sessions = sessions;
        this.websocket = websocket;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${focusdesktop.outbox.dispatch-delay-ms:1000}")
    public void dispatchPendingEvents() {
        Instant now = clock.instant();
        List<PendingOutboxEvent> pending = outbox.findDue(now, BATCH_SIZE);
        for (PendingOutboxEvent event : pending) {
            dispatch(event);
        }
    }

    private void dispatch(PendingOutboxEvent event) {
        try {
            String type = EVENT_NAMES.get(event.eventType());
            if (type == null) {
                throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
            }
            SessionResponse snapshot = sessions.findById(new SessionId(event.aggregateId()))
                    .map(session -> SessionResponse.from(SessionDto.from(session,
                            new TimeStamp(clock.instant()))))
                    .orElseThrow(() -> new IllegalStateException(
                            "Session for outbox event was not found: " + event.aggregateId()));
            objectMapper.readTree(event.payload());
            websocket.broadcast(new SessionEventEnvelope(event.eventId(), 1, type,
                    event.occurredAt(), snapshot));
            outbox.markDelivered(event.eventId(), clock.instant());
        } catch (Exception exception) {
            Instant retryAt = clock.instant().plus(retryDelay(event.attemptCount()));
            outbox.recordFailure(event.eventId(), retryAt, errorDescription(exception));
            LOGGER.log(Level.WARNING, "Outbox event {0} dispatch failed; retry scheduled for {1}",
                    new Object[]{event.eventId(), retryAt});
        }
    }

    private static Duration retryDelay(int attempts) {
        long seconds = 1L << Math.min(Math.max(attempts, 0), 6);
        return Duration.ofSeconds(Math.min(seconds, 60));
    }

    private static String errorDescription(Exception exception) {
        String description = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return description.substring(0, Math.min(description.length(), 1000));
    }
}
