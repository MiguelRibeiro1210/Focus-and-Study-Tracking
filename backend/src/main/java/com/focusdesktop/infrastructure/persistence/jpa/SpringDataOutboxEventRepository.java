package com.focusdesktop.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
}
