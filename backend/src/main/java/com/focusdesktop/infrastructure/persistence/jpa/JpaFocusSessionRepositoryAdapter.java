package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.factories.snapshots.FocusSessionSnapshot;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.util.EnumSet;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaFocusSessionRepositoryAdapter implements FocusSessionRepository {
    private static final EnumSet<SessionState> ACTIVE_STATES = EnumSet.of(
            SessionState.RUNNING, SessionState.PAUSED, SessionState.WAITING_FOR_NEXT_INTERVAL);

    private final SpringDataFocusSessionRepository sessions;

    public JpaFocusSessionRepositoryAdapter(SpringDataFocusSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FocusSession> findById(com.focusdesktop.domain.model.SessionId sessionId) {
        return sessions.findById(sessionId.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FocusSession> findActive() {
        return sessions.findFirstByStateIn(ACTIVE_STATES).map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(FocusSession session) {
        FocusSessionSnapshot snapshot = session.snapshot();
        String settingsJson = PersistenceJson.write(snapshot.settings());
        FocusSessionJpaEntity entity = sessions.findById(snapshot.id().value())
                .orElseGet(() -> FocusSessionJpaEntity.from(snapshot, settingsJson));
        entity.apply(snapshot, settingsJson);
        sessions.save(entity);
    }

    private FocusSession toDomain(FocusSessionJpaEntity entity) {
        SessionSettings settings = PersistenceJson.read(entity.settingsSnapshot(), SessionSettings.class);
        return FocusSessionFactory.reconstitute(entity.toSnapshot(settings));
    }
}
