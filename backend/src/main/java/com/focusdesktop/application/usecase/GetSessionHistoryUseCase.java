package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.SessionHistoryPage;
import com.focusdesktop.application.port.SessionHistoryReader;
import com.focusdesktop.application.usecase.exeptions.InvalidDateRangeException;
import com.focusdesktop.domain.model.SessionState;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public class GetSessionHistoryUseCase {
    private static final List<SessionState> TERMINAL_STATES =
            List.of(SessionState.COMPLETED, SessionState.ABANDONED);

    private final SessionHistoryReader history;
    private final ZoneId localZone;

    public GetSessionHistoryUseCase(SessionHistoryReader history, ZoneId localZone) {
        this.history = Objects.requireNonNull(history, "history");
        this.localZone = Objects.requireNonNull(localZone, "localZone");
    }

    public SessionHistoryPage execute(LocalDate from, LocalDate to, String status, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        List<SessionState> statuses = status == null || status.isBlank()
                ? TERMINAL_STATES : List.of(parseTerminalStatus(status));
        var fromInclusive = from == null ? null : from.atStartOfDay(localZone).toInstant();
        var toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(localZone).toInstant();
        return history.findHistory(fromInclusive, toExclusive, statuses, page, size);
    }

    private static SessionState parseTerminalStatus(String status) {
        final SessionState parsed;
        try {
            parsed = SessionState.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status must be COMPLETED or ABANDONED", exception);
        }
        if (!parsed.isTerminal()) {
            throw new IllegalArgumentException("status must be COMPLETED or ABANDONED");
        }
        return parsed;
    }
}
