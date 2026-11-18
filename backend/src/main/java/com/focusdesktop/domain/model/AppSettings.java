package com.focusdesktop.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Aggregate root for timer profiles and installation-level preferences. */
public final class AppSettings {
    private final String id;
    private final List<TimerProfile> profiles;
    private TimerProfileId defaultProfileId;
    private DailyFocusGoal dailyFocusGoal;
    private ApplicationTheme theme;
    private boolean notificationsEnabled;
    private DataRetentionPolicy retentionPolicy;

    public AppSettings(String id, List<TimerProfile> profiles, TimerProfileId defaultProfileId,
                       DailyFocusGoal dailyFocusGoal, ApplicationTheme theme,
                       boolean notificationsEnabled, DataRetentionPolicy retentionPolicy) {
        this.id = Objects.requireNonNull(id, "id");
        this.profiles = new ArrayList<>(Objects.requireNonNull(profiles, "profiles"));
        this.defaultProfileId = Objects.requireNonNull(defaultProfileId, "defaultProfileId");
        this.dailyFocusGoal = Objects.requireNonNull(dailyFocusGoal, "dailyFocusGoal");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.notificationsEnabled = notificationsEnabled;
        this.retentionPolicy = Objects.requireNonNull(retentionPolicy, "retentionPolicy");
        validateProfiles();
    }

    public static AppSettings defaults() {
        var classic = profile("Classic", 25, 5, 15);
        var deep = profile("Deep work", 50, 10, 20);
        var quick = profile("Quick session", 15, 3, 10);
        return new AppSettings("installation", List.of(classic, deep, quick), classic.id(),
                new DailyFocusGoal(3), ApplicationTheme.LIGHT, true, DataRetentionPolicy.keepForever());
    }

    private static TimerProfile profile(String name, int work, int shortBreak, int longBreak) {
        return new TimerProfile(TimerProfileId.create(), name, new TimerPreferences(
                Duration.ofSeconds(work * 60L), Duration.ofSeconds(shortBreak * 60L),
                Duration.ofSeconds(longBreak * 60L), 4, false, false));
    }

    public TimerProfile createProfile(String name) {
        ensureUniqueName(name, null);
        TimerProfile source = defaultProfile();
        TimerProfile created = new TimerProfile(TimerProfileId.create(), name, source.preferences());
        profiles.add(created);
        return created;
    }

    public void updateProfile(TimerProfileId profileId, String name, TimerPreferences preferences) {
        TimerProfile profile = requireProfile(profileId);
        ensureUniqueName(name, profileId);
        profile.update(name, preferences);
    }

    public void selectDefaultProfile(TimerProfileId profileId) {
        requireProfile(profileId);
        defaultProfileId = profileId;
    }

    public void deleteProfile(TimerProfileId profileId) {
        TimerProfile profile = requireProfile(profileId);
        if (profiles.size() == 1) throw new IllegalStateException("At least one timer profile must remain");
        if (profileId.equals(defaultProfileId)) {
            throw new IllegalStateException("Select another default profile before deleting the current default");
        }
        profiles.remove(profile);
    }

    public void updateApplicationPreferences(DailyFocusGoal goal, ApplicationTheme nextTheme,
                                             boolean nextNotifications, DataRetentionPolicy retention) {
        dailyFocusGoal = Objects.requireNonNull(goal, "goal");
        theme = Objects.requireNonNull(nextTheme, "theme");
        notificationsEnabled = nextNotifications;
        retentionPolicy = Objects.requireNonNull(retention, "retention");
    }

    public TimerProfile defaultProfile() { return requireProfile(defaultProfileId); }
    public TimerProfile requireProfile(TimerProfileId profileId) {
        return profiles.stream().filter(profile -> profile.id().equals(profileId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Timer profile was not found"));
    }
    public List<TimerProfile> profiles() { return List.copyOf(profiles); }
    public String id() { return id; }
    public TimerProfileId defaultProfileId() { return defaultProfileId; }
    public DailyFocusGoal dailyFocusGoal() { return dailyFocusGoal; }
    public ApplicationTheme theme() { return theme; }
    public boolean notificationsEnabled() { return notificationsEnabled; }
    public DataRetentionPolicy retentionPolicy() { return retentionPolicy; }

    private void ensureUniqueName(String name, TimerProfileId except) {
        String normalized = TimerProfile.normalizeName(name);
        boolean duplicate = profiles.stream().anyMatch(profile -> !profile.id().equals(except)
                && profile.name().equalsIgnoreCase(normalized));
        if (duplicate) throw new IllegalArgumentException("A timer profile with this name already exists");
    }

    private void validateProfiles() {
        if (profiles.isEmpty()) throw new IllegalArgumentException("At least one timer profile must exist");
        requireProfile(defaultProfileId);
        for (int index = 0; index < profiles.size(); index++) {
            for (int other = index + 1; other < profiles.size(); other++) {
                if (profiles.get(index).name().equalsIgnoreCase(profiles.get(other).name())) {
                    throw new IllegalArgumentException("Timer profile names must be unique");
                }
            }
        }
    }
}
