package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.model.SessionState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataSessionHistoryRepository extends JpaRepository<FocusSessionJpaEntity, UUID> {
    @Query("select new com.focusdesktop.infrastructure.persistence.jpa.SessionHistoryJpaRow("
            + "s.id, s.projectName, s.sessionType, s.state, s.startedAt, s.endedAt, s.actualSeconds) "
            + "from FocusSessionJpaEntity s where s.state in :statuses "
            + "and (:fromInclusive is null or s.startedAt >= :fromInclusive) "
            + "and (:toExclusive is null or s.startedAt < :toExclusive) "
            + "order by s.startedAt desc, s.id asc")
    Page<SessionHistoryJpaRow> findHistory(@Param("fromInclusive") Instant fromInclusive,
                                           @Param("toExclusive") Instant toExclusive,
                                           @Param("statuses") List<SessionState> statuses,
                                           Pageable pageable);
}
