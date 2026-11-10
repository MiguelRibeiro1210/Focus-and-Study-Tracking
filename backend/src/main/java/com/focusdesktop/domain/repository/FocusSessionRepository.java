package com.focusdesktop.domain.repository;

import com.focusdesktop.domain.model.FocusSession;
import java.util.Optional;

public interface FocusSessionRepository {
    Optional<FocusSession> findActive();

    void save(FocusSession session);
}
