package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.application.dto.SessionHistoryItem;
import com.focusdesktop.application.dto.SessionHistoryPage;
import com.focusdesktop.application.port.SessionHistoryReader;
import com.focusdesktop.domain.model.SessionState;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaSessionHistoryReader implements SessionHistoryReader {
    private final SpringDataSessionHistoryRepository sessions;

    public JpaSessionHistoryReader(SpringDataSessionHistoryRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public SessionHistoryPage findHistory(Instant fromInclusive, Instant toExclusive,
                                          List<SessionState> statuses, int page, int size) {
        var result = sessions.findHistory(fromInclusive, toExclusive, statuses, PageRequest.of(page, size));
        List<SessionHistoryItem> items = result.getContent().stream()
                .map(row -> new SessionHistoryItem(row.sessionId(), row.projectName(), row.sessionType().name(),
                        row.status().name(), row.startedAt(), row.endedAt(), row.actualFocusDurationSeconds()))
                .toList();
        return new SessionHistoryPage(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }
}
