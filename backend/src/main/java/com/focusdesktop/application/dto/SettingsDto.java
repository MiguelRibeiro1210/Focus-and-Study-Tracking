package com.focusdesktop.application.dto;

import java.util.List;
import java.util.UUID;

public record SettingsDto(UUID defaultProfileId, List<TimerProfileDto> profiles,
                          int dailyGoalHours, String theme, boolean notificationsEnabled,
                          Integer retentionDays) {
    public SettingsDto {
        profiles = List.copyOf(profiles);
    }
}
