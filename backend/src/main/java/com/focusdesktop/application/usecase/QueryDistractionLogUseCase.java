package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.DistractionLogPage;
import com.focusdesktop.application.port.DistractionLogReader;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QueryDistractionLogUseCase {
    private final DistractionLogReader logs;
    public QueryDistractionLogUseCase(DistractionLogReader logs) { this.logs = logs; }
    public DistractionLogPage execute(UUID sessionId, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        ZoneId zone = ZoneId.systemDefault();
        return logs.find(sessionId, from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(), page, size);
    }
}
