package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.domain.event.DomainEvent;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SessionUseCaseTestFixtures {
    static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    static final SessionSettings TEN_MINUTES = new SessionSettings(Duration.ofSeconds(600),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 5, false, false);

    private SessionUseCaseTestFixtures() { }

    static TimeStamp at(long secondsAfterStart) {
        return new TimeStamp(START.plusSeconds(secondsAfterStart));
    }

    static FocusSession runningSession() {
        FocusSession session = FocusSessionFactory.create(TEN_MINUTES, at(0));
        session.start(at(0));
        return session;
    }

    static Fixture fixture(FocusSession session, long clockSeconds) {
        Fixture fixture = fixture();
        fixture.sessions.put(session.id(), session);
        fixture.clockSeconds = clockSeconds;
        return fixture;
    }

    static Fixture fixture() {
        return new Fixture();
    }

    static final class Fixture implements FocusSessionRepository, DomainEventOutbox {
        final Map<SessionId, FocusSession> sessions = new HashMap<>();
        final List<DomainEvent> events = new ArrayList<>();
        final List<String> writes = new ArrayList<>();
        long clockSeconds;
        int activeLookups;
        int saves;
        FocusSession lastSaved;
        boolean failOnSave;
        boolean failOnAppend;

        Fixture() { }

        Clock clock() {
            return Clock.fixed(START.plusSeconds(clockSeconds), ZoneOffset.UTC);
        }

        @Override
        public Optional<FocusSession> findById(SessionId sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Optional<FocusSession> findActive() {
            activeLookups++;
            return sessions.values().stream().filter(s -> s.state().isActive()).findFirst();
        }

        @Override
        public void save(FocusSession session) {
            saves++;
            writes.add("save");
            if (failOnSave) {
                throw new IllegalStateException("Persistence failed");
            }
            lastSaved = session;
            sessions.put(session.id(), session);
        }

        @Override
        public void append(DomainEvent event) {
            writes.add("append");
            if (failOnAppend) {
                throw new IllegalStateException("Outbox append failed");
            }
            events.add(event);
        }
    }
}
