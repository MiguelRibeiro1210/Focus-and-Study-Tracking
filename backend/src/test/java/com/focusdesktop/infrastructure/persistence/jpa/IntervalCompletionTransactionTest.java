package com.focusdesktop.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.application.usecase.CompleteDueIntervalsUseCase;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "focusdesktop.data-dir=target/test-interval-transaction-data",
        "focusdesktop.api.token=test-token",
        "focusdesktop.session.interval-check-delay-ms=86400000",
        "spring.datasource.url=jdbc:h2:mem:interval_completion_transaction_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@org.springframework.context.annotation.Import(IntervalCompletionTransactionTest.ClockConfiguration.class)
class IntervalCompletionTransactionTest {
    private static final TimeStamp START = new TimeStamp(Instant.parse("2026-01-01T10:00:00Z"));
    private static final SessionSettings SETTINGS = new SessionSettings(Duration.ofSeconds(60),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false);

    @Autowired
    private CompleteDueIntervalsUseCase completeDueIntervals;

    @Autowired
    private FocusSessionRepository sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM session_interval");
        jdbc.update("DELETE FROM focus_session");
        jdbc.update("DELETE FROM domain_event_outbox");
    }

    @Test
    void commitsIntervalTransitionAndOutboxEventTogether() {
        FocusSession session = seedRunningSession();

        assertEquals(1, completeDueIntervals.execute());

        assertEquals(SessionState.WAITING_FOR_NEXT_INTERVAL,
                sessions.findById(session.id()).orElseThrow().state());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_event_outbox WHERE event_type = 'IntervalCompleted'", Integer.class));
    }

    @Test
    void rollsBackIntervalTransitionAndOutboxEventTogether() {
        FocusSession session = seedRunningSession();
        TransactionTemplate rollback = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> rollback.executeWithoutResult(status -> {
            assertEquals(1, completeDueIntervals.execute());
            throw new IllegalStateException("Force rollback after session and outbox writes");
        }));

        assertEquals(SessionState.RUNNING, sessions.findById(session.id()).orElseThrow().state());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_event_outbox WHERE event_type = 'IntervalCompleted'", Integer.class));
    }

    private FocusSession seedRunningSession() {
        FocusSession session = FocusSessionFactory.create(SETTINGS, START);
        session.start(START);
        TransactionTemplate seed = new TransactionTemplate(transactionManager);
        seed.executeWithoutResult(status -> sessions.save(session));
        return session;
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        Clock intervalCompletionTestClock() {
            return Clock.fixed(Instant.parse("2026-01-01T10:01:00Z"), ZoneOffset.UTC);
        }
    }
}
