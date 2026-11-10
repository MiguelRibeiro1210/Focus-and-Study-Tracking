package com.focusdesktop.domain.repository;

import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionId;
import java.util.Optional;

public interface FocusSessionRepository {
    Optional<FocusSession> findById(SessionId sessionId);

    Optional<FocusSession> findActive();

    void save(FocusSession session);
}
