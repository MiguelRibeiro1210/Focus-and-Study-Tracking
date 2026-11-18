package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.TimerPreferences;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "timer_profile")
class TimerProfileJpaEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private AppSettingsJpaEntity settings;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = 60)
    private String nameNormalized;

    @Column(name = "work_seconds", nullable = false)
    private long workSeconds;

    @Column(name = "short_break_seconds", nullable = false)
    private long shortBreakSeconds;

    @Column(name = "long_break_seconds", nullable = false)
    private long longBreakSeconds;

    @Column(name = "intervals_before_long_break", nullable = false)
    private int intervalsBeforeLongBreak;

    @Column(name = "auto_start_breaks", nullable = false)
    private boolean autoStartBreaks;

    @Column(name = "auto_start_work", nullable = false)
    private boolean autoStartWork;

    protected TimerProfileJpaEntity() { }

    TimerProfileJpaEntity(UUID id, AppSettingsJpaEntity settings) {
        this.id = id;
        this.settings = settings;
    }

    void apply(String nextName, TimerPreferences preferences) {
        name = nextName;
        nameNormalized = nextName.toLowerCase(java.util.Locale.ROOT);
        workSeconds = preferences.workDuration().seconds();
        shortBreakSeconds = preferences.shortBreakDuration().seconds();
        longBreakSeconds = preferences.longBreakDuration().seconds();
        intervalsBeforeLongBreak = preferences.intervalsBeforeLongBreak();
        autoStartBreaks = preferences.autoStartBreaks();
        autoStartWork = preferences.autoStartWork();
    }

    UUID id() { return id; }
    String name() { return name; }
    TimerPreferences preferences() {
        return new TimerPreferences(Duration.ofSeconds(workSeconds), Duration.ofSeconds(shortBreakSeconds),
                Duration.ofSeconds(longBreakSeconds), intervalsBeforeLongBreak, autoStartBreaks, autoStartWork);
    }
}
