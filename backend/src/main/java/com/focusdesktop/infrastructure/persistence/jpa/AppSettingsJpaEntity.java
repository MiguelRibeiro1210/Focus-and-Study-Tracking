package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.domain.model.ApplicationTheme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "app_settings")
class AppSettingsJpaEntity {
    @Id
    @Column(name = "id", nullable = false, length = 40)
    private String id;

    @Column(name = "default_profile_id", nullable = false)
    private UUID defaultProfileId;

    @Column(name = "daily_goal_hours", nullable = false)
    private int dailyGoalHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 16)
    private ApplicationTheme theme;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppSettingsJpaEntity() { }

    AppSettingsJpaEntity(String id) { this.id = id; }

    void apply(UUID profileId, int goalHours, ApplicationTheme nextTheme,
               boolean notifications, Integer nextRetentionDays) {
        defaultProfileId = profileId;
        dailyGoalHours = goalHours;
        theme = nextTheme;
        notificationsEnabled = notifications;
        retentionDays = nextRetentionDays;
    }

    String id() { return id; }
    UUID defaultProfileId() { return defaultProfileId; }
    int dailyGoalHours() { return dailyGoalHours; }
    ApplicationTheme theme() { return theme; }
    boolean notificationsEnabled() { return notificationsEnabled; }
    Integer retentionDays() { return retentionDays; }
}
