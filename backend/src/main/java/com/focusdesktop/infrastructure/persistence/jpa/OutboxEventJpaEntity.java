package com.focusdesktop.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "domain_event_outbox")
public class OutboxEventJpaEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(nullable = false, columnDefinition = "CLOB")
    private String payload;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected OutboxEventJpaEntity() { }

    OutboxEventJpaEntity(UUID eventId, String eventType, UUID aggregateId,
                         Instant occurredAt, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }

    UUID eventId() { return eventId; }
}
