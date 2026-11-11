package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.model.SessionState;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFocusSessionRepository extends JpaRepository<FocusSessionJpaEntity, UUID> {
    Optional<FocusSessionJpaEntity> findFirstByStateIn(Collection<SessionState> states);
}
