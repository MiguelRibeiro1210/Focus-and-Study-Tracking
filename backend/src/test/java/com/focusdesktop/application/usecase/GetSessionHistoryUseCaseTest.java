package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.application.dto.SessionHistoryPage;
import com.focusdesktop.application.port.SessionHistoryReader;
import com.focusdesktop.application.usecase.exeptions.InvalidDateRangeException;
import com.focusdesktop.domain.model.SessionState;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetSessionHistoryUseCaseTest {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    @Test
    void convertsInclusiveLocalDateRangeToExclusiveInstantBoundsAndUsesDefaultTerminalStatuses() {
        CapturingReader reader = new CapturingReader();
        GetSessionHistoryUseCase useCase = new GetSessionHistoryUseCase(reader, ZONE);
        LocalDate from = LocalDate.of(2026, 3, 8);
        LocalDate to = LocalDate.of(2026, 3, 9);

        useCase.execute(from, to, null, 2, 40);

        assertEquals(from.atStartOfDay(ZONE).toInstant(), reader.fromInclusive);
        assertEquals(to.plusDays(1).atStartOfDay(ZONE).toInstant(), reader.toExclusive);
        assertEquals(List.of(SessionState.COMPLETED, SessionState.ABANDONED), reader.statuses);
        assertEquals(2, reader.page);
        assertEquals(40, reader.size);
    }

    @Test
    void supportsOneSidedDateAndStatusFiltersAndLeavesOtherBoundOpen() {
        CapturingReader reader = new CapturingReader();
        GetSessionHistoryUseCase useCase = new GetSessionHistoryUseCase(reader, ZONE);

        useCase.execute(null, LocalDate.of(2026, 6, 2), "completed", 0, 20);

        assertEquals(null, reader.fromInclusive);
        assertEquals(LocalDate.of(2026, 6, 3).atStartOfDay(ZONE).toInstant(), reader.toExclusive);
        assertEquals(List.of(SessionState.COMPLETED), reader.statuses);
    }

    @Test
    void rejectsInvalidDateRangePaginationAndNonTerminalStatuses() {
        GetSessionHistoryUseCase useCase = new GetSessionHistoryUseCase(new CapturingReader(), ZONE);

        assertThrows(InvalidDateRangeException.class,
                () -> useCase.execute(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1), null, 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(null, null, null, -1, 20));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(null, null, null, 0, 101));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(null, null, "RUNNING", 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(null, null, "unknown", 0, 20));
    }

    private static final class CapturingReader implements SessionHistoryReader {
        private Instant fromInclusive;
        private Instant toExclusive;
        private List<SessionState> statuses;
        private int page;
        private int size;

        @Override
        public SessionHistoryPage findHistory(Instant fromInclusive, Instant toExclusive,
                                               List<SessionState> statuses, int page, int size) {
            this.fromInclusive = fromInclusive;
            this.toExclusive = toExclusive;
            this.statuses = List.copyOf(statuses);
            this.page = page;
            this.size = size;
            return new SessionHistoryPage(List.of(), 0, page, size);
        }
    }
}
