package com.focusdesktop.domain.repository;

import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FocusSessionRepository {
    Optional<FocusSession> findById(SessionId sessionId);

    Optional<FocusSession> findActive();

    List<FocusSession> findCompletedSessions(Instant startedAtFromInclusive, Instant startedAtToExclusive);

    void save(FocusSession session);
}
