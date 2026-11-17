package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.application.dto.AnalyticsSummary;
import com.focusdesktop.application.usecase.exeptions.AnalyticsRangeTooLargeException;
import com.focusdesktop.application.usecase.exeptions.InvalidDateRangeException;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetAnalyticsSummaryUseCaseTest {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final SessionSettings SETTINGS = new SessionSettings(Duration.ofSeconds(600),
            Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false);

    @Test
    void summarizesCompletedSessionsForInclusiveLocalDatesAndBuildsZeroFilledDailyBuckets() {
        LocalDate from = LocalDate.of(2026, 3, 8);
        LocalDate to = from.plusDays(1);
        InMemorySessions repository = new InMemorySessions();
        repository.add(completed("first", from.atTime(9, 0).atZone(ZONE).toInstant(), 600));
        repository.add(completed("second", to.atTime(9, 0).atZone(ZONE).toInstant(), 300));
        repository.add(completed("outside", to.plusDays(1).atTime(9, 0).atZone(ZONE).toInstant(), 600));
        repository.add(abandoned("short", from.atTime(10, 0).atZone(ZONE).toInstant(), 299));

        AnalyticsSummary result = new GetAnalyticsSummaryUseCase(repository, ZONE)
                .execute(from.toString(), to.toString());

        assertEquals(from, result.from());
        assertEquals(to, result.to());
        assertEquals(900, result.totalFocusSeconds());
        assertEquals(0, result.totalBreakSeconds());
        assertEquals(2, result.completedSessions());
        assertEquals(450, result.averageSessionSeconds());
        assertEquals(75, result.focusScore());
        assertEquals(from, result.bestDay().date());
        assertEquals(2, result.daily().size());
        assertEquals(600, result.daily().get(0).focusSeconds());
        assertEquals(1, result.daily().get(0).completedSessions());
        assertEquals(300, result.daily().get(1).focusSeconds());
    }

    @Test
    void calculatesBreakTotalsAndExcludesUnstartedWorkFromFocusScoreDenominator() {
        Instant start = Instant.parse("2026-06-01T12:00:00Z");
        FocusSession session = FocusSessionFactory.create("study", SETTINGS, new TimeStamp(start));
        session.start(new TimeStamp(start));
        session.completeInterval(SessionType.SHORT_BREAK, new TimeStamp(start.plusSeconds(600)));
        session.startNextInterval(new TimeStamp(start.plusSeconds(600)));
        session.completeInterval(SessionType.WORK, new TimeStamp(start.plusSeconds(900)));
        session.stop(new TimeStamp(start.plusSeconds(900)));
        InMemorySessions repository = new InMemorySessions();
        repository.add(session);

        AnalyticsSummary result = new GetAnalyticsSummaryUseCase(repository, ZoneId.of("UTC"))
                .execute("2026-06-01", "2026-06-01");

        assertEquals(600, result.totalFocusSeconds());
        assertEquals(300, result.totalBreakSeconds());
        assertEquals(100, result.focusScore());
    }

    @Test
    void allTimeReturnsAllTimeTotalsAndLatestSevenLocalDaysForChart() {
        LocalDate today = LocalDate.now(ZONE);
        InMemorySessions repository = new InMemorySessions();
        repository.add(completed("older", today.minusYears(1).atStartOfDay(ZONE).toInstant(), 300));

        AnalyticsSummary result = new GetAnalyticsSummaryUseCase(repository, ZONE).execute(null, null);

        assertNull(result.from());
        assertNull(result.to());
        assertEquals(300, result.totalFocusSeconds());
        assertEquals(1, result.completedSessions());
        assertEquals(7, result.daily().size());
        assertEquals(today.minusDays(6), result.daily().get(0).date());
        assertEquals(today, result.daily().get(6).date());
    }

    @Test
    void rejectsMalformedIncompleteReversedAndOversizedRanges() {
        GetAnalyticsSummaryUseCase useCase = new GetAnalyticsSummaryUseCase(new InMemorySessions(), ZONE);

        assertThrows(InvalidDateRangeException.class, () -> useCase.execute("2026-01-01", null));
        assertThrows(InvalidDateRangeException.class, () -> useCase.execute("not-a-date", "2026-01-02"));
        assertThrows(InvalidDateRangeException.class, () -> useCase.execute("2026-01-02", "2026-01-01"));
        assertThrows(AnalyticsRangeTooLargeException.class,
                () -> useCase.execute("2024-12-31", "2026-01-01"));
    }

    private static FocusSession completed(String name, Instant start, long elapsedSeconds) {
        FocusSession session = FocusSessionFactory.create(name, SETTINGS, new TimeStamp(start));
        session.start(new TimeStamp(start));
        session.stop(new TimeStamp(start.plusSeconds(elapsedSeconds)));
        return session;
    }

    private static FocusSession abandoned(String name, Instant start, long elapsedSeconds) {
        return completed(name, start, elapsedSeconds);
    }

    private static final class InMemorySessions implements FocusSessionRepository {
        private final List<FocusSession> records = new ArrayList<>();

        void add(FocusSession session) {
            records.add(session);
        }

        @Override
        public Optional<FocusSession> findById(com.focusdesktop.domain.model.SessionId id) {
            return records.stream().filter(session -> session.id().equals(id)).findFirst();
        }

        @Override
        public Optional<FocusSession> findActive() {
            return records.stream().filter(session -> session.state().isActive()).findFirst();
        }

        @Override
        public List<FocusSession> findCompletedSessions(Instant fromInclusive, Instant toExclusive) {
            return records.stream()
                    .filter(session -> session.state() == com.focusdesktop.domain.model.SessionState.COMPLETED)
                    .filter(session -> fromInclusive == null || !session.startedAt().value().isBefore(fromInclusive))
                    .filter(session -> toExclusive == null || session.startedAt().value().isBefore(toExclusive))
                    .toList();
        }

        @Override
        public void save(FocusSession session) { }
    }
}
