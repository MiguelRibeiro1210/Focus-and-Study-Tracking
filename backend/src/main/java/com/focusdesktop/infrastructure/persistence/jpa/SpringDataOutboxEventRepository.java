package com.focusdesktop.infrastructure.persistence.jpa;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    @Query("select e from OutboxEventJpaEntity e where e.deliveredAt is null "
            + "and (e.nextAttemptAt is null or e.nextAttemptAt <= :now) "
            + "order by e.occurredAt asc, e.eventId asc")
    List<OutboxEventJpaEntity> findDue(@Param("now") Instant now, Pageable pageable);
}
