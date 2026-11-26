package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.application.port.SessionDataMaintenance;
import com.focusdesktop.domain.model.SessionState;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaSessionDataMaintenanceAdapter implements SessionDataMaintenance {
    private static final EnumSet<SessionState> TERMINAL = EnumSet.of(SessionState.COMPLETED, SessionState.ABANDONED);
    private final SpringDataFocusSessionRepository sessions;
    private final SpringDataOutboxEventRepository events;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public JpaSessionDataMaintenanceAdapter(SpringDataFocusSessionRepository sessions,
                                            SpringDataOutboxEventRepository events, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.sessions = sessions;
        this.events = events;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void clearTerminalHistory() {
        lockProjection();
        List<UUID> sessionIds = sessions.findIdsByStateIn(TERMINAL);
        if (sessionIds.isEmpty()) return;
        lockSessions(sessionIds);
        events.deleteAllByAggregateIdIn(sessionIds);
        sessions.deleteAllByIdInBatch(sessionIds);
    }

    @Override
    @Transactional
    public int clearExpiredTerminalHistory(Instant endedAtOrBefore) {
        lockProjection();
        List<UUID> sessionIds = sessions.findIdsByStateInAndEndedAtLessThanEqual(TERMINAL, endedAtOrBefore);
        if (sessionIds.isEmpty()) return 0;
        lockSessions(sessionIds);
        events.deleteAllByAggregateIdIn(sessionIds);
        sessions.deleteAllByIdInBatch(sessionIds);
        return sessionIds.size();
    }

    @Override
    @Transactional
    public void deleteAllSessionData() {
        lockProjection();
        lockSessions(jdbc.query("SELECT id FROM focus_session", (row, index) -> row.getObject(1, UUID.class)));
        events.deleteAllInBatch();
        sessions.deleteAllInBatch();
    }
    private void lockProjection() {
        jdbc.queryForObject("SELECT singleton_slot FROM distraction_projection_lock WHERE singleton_slot=1 FOR UPDATE", Integer.class);
    }
    private void lockSessions(List<UUID> ids) {
        ids.stream().sorted().forEach(id -> jdbc.query("SELECT id FROM focus_session WHERE id=? FOR UPDATE", (row, index) -> row.getObject(1, UUID.class), id));
    }
}
