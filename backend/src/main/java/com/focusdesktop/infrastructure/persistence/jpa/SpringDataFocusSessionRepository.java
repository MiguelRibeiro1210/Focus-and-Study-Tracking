package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.model.SessionState;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataFocusSessionRepository extends JpaRepository<FocusSessionJpaEntity, UUID> {
    Optional<FocusSessionJpaEntity> findFirstByStateIn(Collection<SessionState> states);
    @EntityGraph(attributePaths = "intervals")
    List<FocusSessionJpaEntity> findAllByOrderByCreatedAtDescIdAsc();
    @Query("select s.id from FocusSessionJpaEntity s where s.state in :states")
    List<UUID> findIdsByStateIn(@Param("states") Collection<SessionState> states);

    @Query("select s.id from FocusSessionJpaEntity s where s.state in :states "
            + "and s.endedAt <= :endedAtOrBefore")
    List<UUID> findIdsByStateInAndEndedAtLessThanEqual(@Param("states") Collection<SessionState> states,
            @Param("endedAtOrBefore") Instant endedAtOrBefore);

    @EntityGraph(attributePaths = "intervals")
    @Query("select s from FocusSessionJpaEntity s where s.state = :state "
            + "and (:fromInclusive is null or s.startedAt >= :fromInclusive) "
            + "and (:toExclusive is null or s.startedAt < :toExclusive)")
    List<FocusSessionJpaEntity> findCompletedSessions(@Param("state") SessionState state,
            @Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive);
}
