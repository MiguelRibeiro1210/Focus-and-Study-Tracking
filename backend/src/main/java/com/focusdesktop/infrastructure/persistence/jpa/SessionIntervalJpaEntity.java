package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.factories.snapshots.SessionIntervalSnapshot;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.IntervalId;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_interval", uniqueConstraints =
        @UniqueConstraint(name = "uq_session_interval_sequence", columnNames = {"session_id", "sequence_no"}))
public class SessionIntervalJpaEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private FocusSessionJpaEntity session;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_type", nullable = false, length = 20)
    private SessionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntervalState state;

    @Column(name = "planned_seconds", nullable = false)
    private long plannedSeconds;

    @Column(name = "actual_seconds", nullable = false)
    private long actualSeconds;

    @Column(name = "remaining_seconds", nullable = false)
    private long remainingSeconds;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    protected SessionIntervalJpaEntity() { }

    void apply(SessionIntervalSnapshot snapshot, FocusSessionJpaEntity session) {
        id = snapshot.id().value();
        this.session = session;
        sequenceNumber = snapshot.sequenceNumber();
        type = snapshot.type();
        state = snapshot.state();
        plannedSeconds = snapshot.plannedDuration().seconds();
        actualSeconds = snapshot.actualDuration().seconds();
        remainingSeconds = snapshot.remainingDuration().seconds();
        startedAt = instant(snapshot.startedAt());
        endedAt = instant(snapshot.endedAt());
        pausedAt = instant(snapshot.pausedAt());
    }

    SessionIntervalSnapshot toSnapshot() {
        return new SessionIntervalSnapshot(new IntervalId(id), sequenceNumber, type,
                Duration.ofSeconds(plannedSeconds), Duration.ofSeconds(actualSeconds),
                Duration.ofSeconds(remainingSeconds), state, timestamp(startedAt),
                timestamp(endedAt), timestamp(pausedAt));
    }

    UUID id() { return id; }
    int sequenceNumber() { return sequenceNumber; }

    private static Instant instant(TimeStamp timestamp) {
        return timestamp == null ? null : timestamp.value();
    }

    private static TimeStamp timestamp(Instant instant) {
        return instant == null ? null : new TimeStamp(instant);
    }
}
