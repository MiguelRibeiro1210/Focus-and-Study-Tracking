package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.application.dto.DataExportBundle.IntervalExport;
import com.focusdesktop.application.dto.DataExportBundle.SessionExport;
import com.focusdesktop.application.port.DataExportReader;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaDataExportReader implements DataExportReader {
    private final SpringDataFocusSessionRepository sessions;

    public JpaDataExportReader(SpringDataFocusSessionRepository sessions) { this.sessions = sessions; }

    @Override
    @Transactional(readOnly = true)
    public List<SessionExport> loadAllSessions() {
        return sessions.findAllByOrderByCreatedAtDescIdAsc().stream().map(session -> new SessionExport(
                session.id(), session.projectName(), session.sessionType().name(), session.state().name(),
                session.createdAt(), session.startedAt(), session.endedAt(), session.settingsSnapshotValue(),
                session.intervals().stream().map(interval -> new IntervalExport(interval.id(),
                        interval.sequenceNumber(), interval.type().name(), interval.state().name(),
                        interval.plannedSeconds(), interval.actualSeconds(), interval.remainingSeconds(),
                        interval.startedAt(), interval.endedAt(), interval.pausedAt())).toList())).toList();
    }
}
