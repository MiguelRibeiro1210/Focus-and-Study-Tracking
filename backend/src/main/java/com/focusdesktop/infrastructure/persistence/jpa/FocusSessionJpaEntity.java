package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.factories.snapshots.FocusSessionSnapshot;
import com.focusdesktop.domain.factories.snapshots.SessionIntervalSnapshot;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "focus_session", uniqueConstraints =
        @UniqueConstraint(name = "uq_focus_session_active_slot", columnNames = "active_slot"))
public class FocusSessionJpaEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionState state;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "planned_seconds", nullable = false)
    private long plannedSeconds;

    @Column(name = "actual_seconds", nullable = false)
    private long actualSeconds;

    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(name = "settings_snapshot", nullable = false, columnDefinition = "CLOB")
    private String settingsSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "active_slot")
    private Short activeSlot;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<SessionIntervalJpaEntity> intervals = new ArrayList<>();

    protected FocusSessionJpaEntity() { }

    static FocusSessionJpaEntity from(FocusSessionSnapshot snapshot, String settingsJson) {
        FocusSessionJpaEntity entity = new FocusSessionJpaEntity();
        entity.id = snapshot.id().value();
        entity.apply(snapshot, settingsJson);
        return entity;
    }

    void apply(FocusSessionSnapshot snapshot, String settingsJson) {
        sessionType = snapshot.sessionType();
        state = snapshot.state();
        startedAt = snapshot.startedAt() == null ? null : snapshot.startedAt().value();
        endedAt = snapshot.endedAt() == null ? null : snapshot.endedAt().value();
        createdAt = snapshot.createdAt().value();
        settingsSnapshot = settingsJson;
        activeSlot = snapshot.state().isActive() ? (short) 1 : null;
        plannedSeconds = snapshot.intervals().stream()
                .filter(i -> i.type() == SessionType.WORK)
                .mapToLong(i -> i.plannedDuration().seconds())
                .sum();
        actualSeconds = snapshot.intervals().stream()
                .filter(i -> i.type() == SessionType.WORK)
                .mapToLong(i -> i.actualDuration().seconds())
                .sum();
        synchronizeIntervals(snapshot.intervals());
    }

    private void synchronizeIntervals(List<SessionIntervalSnapshot> snapshots) {
        Map<UUID, SessionIntervalJpaEntity> existing = intervals.stream()
                .collect(Collectors.toMap(SessionIntervalJpaEntity::id, interval -> interval));
        Set<UUID> retainedIds = new HashSet<>();
        for (SessionIntervalSnapshot snapshot : snapshots) {
            UUID intervalId = snapshot.id().value();
            retainedIds.add(intervalId);
            SessionIntervalJpaEntity interval = existing.get(intervalId);
            if (interval == null) {
                interval = new SessionIntervalJpaEntity();
                intervals.add(interval);
            }
            interval.apply(snapshot, this);
        }
        intervals.removeIf(interval -> !retainedIds.contains(interval.id()));
    }

    @PrePersist
    void onInsert() {
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    FocusSessionSnapshot toSnapshot(SessionSettings settings) {
        List<SessionIntervalSnapshot> intervalSnapshots = intervals.stream()
                .sorted(Comparator.comparingInt(SessionIntervalJpaEntity::sequenceNumber))
                .map(SessionIntervalJpaEntity::toSnapshot)
                .toList();
        return new FocusSessionSnapshot(
                new SessionId(id), sessionType, settings,
                new TimeStamp(createdAt), state,
                startedAt == null ? null : new TimeStamp(startedAt),
                endedAt == null ? null : new TimeStamp(endedAt),
                intervalSnapshots);
    }

    UUID id() { return id; }
    String settingsSnapshot() { return settingsSnapshot; }
}
