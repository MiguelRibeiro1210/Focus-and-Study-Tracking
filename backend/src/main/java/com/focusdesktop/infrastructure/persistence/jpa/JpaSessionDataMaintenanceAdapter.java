package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.application.port.SessionDataMaintenance;
import com.focusdesktop.domain.model.SessionState;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaSessionDataMaintenanceAdapter implements SessionDataMaintenance {
    private static final EnumSet<SessionState> TERMINAL = EnumSet.of(SessionState.COMPLETED, SessionState.ABANDONED);
    private final SpringDataFocusSessionRepository sessions;
    private final SpringDataOutboxEventRepository events;

    public JpaSessionDataMaintenanceAdapter(SpringDataFocusSessionRepository sessions,
                                            SpringDataOutboxEventRepository events) {
        this.sessions = sessions;
        this.events = events;
    }

    @Override
    @Transactional
    public void clearTerminalHistory() {
        List<UUID> sessionIds = sessions.findIdsByStateIn(TERMINAL);
        if (sessionIds.isEmpty()) return;
        events.deleteAllByAggregateIdIn(sessionIds);
        sessions.deleteAllByIdInBatch(sessionIds);
    }

    @Override
    @Transactional
    public void deleteAllSessionData() {
        events.deleteAllInBatch();
        sessions.deleteAllInBatch();
    }
}
