package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.SettingsDto;
import com.focusdesktop.application.dto.TimerProfileDto;
import com.focusdesktop.domain.model.AppSettings;
import com.focusdesktop.domain.model.ApplicationTheme;
import com.focusdesktop.domain.model.DailyFocusGoal;
import com.focusdesktop.domain.model.DataRetentionPolicy;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.TimerPreferences;
import com.focusdesktop.domain.model.TimerProfile;
import com.focusdesktop.domain.model.TimerProfileId;
import com.focusdesktop.domain.repository.AppSettingsRepository;
import com.focusdesktop.application.usecase.exeptions.SettingsConflictException;
import java.util.UUID;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsUseCase {
    private final AppSettingsRepository settings;

    public SettingsUseCase(AppSettingsRepository settings) { this.settings = settings; }

    @Transactional
    public SettingsDto get() { return dto(settings.loadOrCreate()); }

    @Transactional
    public SettingsDto createProfile(String name) {
        AppSettings aggregate = settings.loadOrCreate();
        aggregate.createProfile(name);
        return dto(settings.save(aggregate));
    }

    @Transactional
    public SettingsDto updateProfile(UUID id, String name, long workSeconds, long shortSeconds,
                                     long longSeconds, int intervalsBeforeLongBreak,
                                     boolean autoStartBreaks, boolean autoStartWork) {
        AppSettings aggregate = settings.loadOrCreate();
        aggregate.updateProfile(new TimerProfileId(id), name, new TimerPreferences(
                Duration.ofSeconds(workSeconds), Duration.ofSeconds(shortSeconds), Duration.ofSeconds(longSeconds),
                intervalsBeforeLongBreak, autoStartBreaks, autoStartWork));
        return dto(settings.save(aggregate));
    }

    @Transactional
    public SettingsDto setDefault(UUID id) {
        AppSettings aggregate = settings.loadOrCreate();
        aggregate.selectDefaultProfile(new TimerProfileId(id));
        return dto(settings.save(aggregate));
    }

    @Transactional
    public SettingsDto deleteProfile(UUID id) {
        AppSettings aggregate = settings.loadOrCreate();
        try {
            aggregate.deleteProfile(new TimerProfileId(id));
        } catch (IllegalStateException exception) {
            throw new SettingsConflictException(exception.getMessage());
        }
        return dto(settings.save(aggregate));
    }

    @Transactional
    public SettingsDto updatePreferences(int dailyGoalHours, ApplicationTheme theme,
                                         boolean notificationsEnabled, Integer retentionDays) {
        AppSettings aggregate = settings.loadOrCreate();
        aggregate.updateApplicationPreferences(new DailyFocusGoal(dailyGoalHours), theme,
                notificationsEnabled, new DataRetentionPolicy(retentionDays));
        return dto(settings.save(aggregate));
    }

    private static SettingsDto dto(AppSettings aggregate) {
        return new SettingsDto(aggregate.defaultProfileId().value(), aggregate.profiles().stream()
                .sorted(Comparator.comparing((TimerProfile profile) ->
                                !aggregate.defaultProfileId().equals(profile.id()))
                        .thenComparing(TimerProfile::name, String.CASE_INSENSITIVE_ORDER))
                .map(profile -> profileDto(profile, aggregate.defaultProfileId().equals(profile.id()))).toList(),
                aggregate.dailyFocusGoal().hours(), aggregate.theme().name(), aggregate.notificationsEnabled(),
                aggregate.retentionPolicy().retentionDays());
    }

    private static TimerProfileDto profileDto(TimerProfile profile, boolean isDefault) {
        var preferences = profile.preferences();
        return new TimerProfileDto(profile.id().value(), profile.name(), preferences.workDuration().seconds(),
                preferences.shortBreakDuration().seconds(), preferences.longBreakDuration().seconds(),
                preferences.intervalsBeforeLongBreak(), preferences.autoStartBreaks(), preferences.autoStartWork(),
                isDefault);
    }
}
