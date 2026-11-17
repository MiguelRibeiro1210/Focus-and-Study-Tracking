package com.focusdesktop.application.port;

import com.focusdesktop.application.dto.SessionHistoryPage;
import com.focusdesktop.domain.model.SessionState;
import java.time.Instant;
import java.util.List;

public interface SessionHistoryReader {
    SessionHistoryPage findHistory(Instant fromInclusive, Instant toExclusive,
                                   List<SessionState> statuses, int page, int size);
}
