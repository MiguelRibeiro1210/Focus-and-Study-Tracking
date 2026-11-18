package com.focusdesktop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppSettingsTest {
    @Test
    void seedsApprovedStarterProfilesWithClassicSelectedAndThreeHourGoal() {
        AppSettings settings = AppSettings.defaults();

        assertEquals(3, settings.profiles().size());
        assertEquals("Classic", settings.defaultProfile().name());
        assertEquals(25 * 60, settings.defaultProfile().preferences().workDuration().seconds());
        assertEquals(5 * 60, settings.defaultProfile().preferences().shortBreakDuration().seconds());
        assertEquals(15 * 60, settings.defaultProfile().preferences().longBreakDuration().seconds());
        assertEquals(4, settings.defaultProfile().preferences().intervalsBeforeLongBreak());
        assertFalse(settings.defaultProfile().preferences().autoStartBreaks());
        assertFalse(settings.defaultProfile().preferences().autoStartWork());
        assertEquals(3, settings.dailyFocusGoal().hours());
    }

    @Test
    void createsFromCurrentDefaultAndNormalizesUniqueNames() {
        AppSettings settings = AppSettings.defaults();
        TimerProfile created = settings.createProfile("  My routine  ");

        assertEquals("My routine", created.name());
        assertEquals(settings.defaultProfile().preferences(), created.preferences());
        assertThrows(IllegalArgumentException.class, () -> settings.createProfile(" classic "));
        assertThrows(IllegalArgumentException.class, () -> settings.createProfile("  "));
    }

    @Test
    void editsAnyProfileIncludingStarterAndRejectsInvalidPreferences() {
        AppSettings settings = AppSettings.defaults();
        TimerProfile classic = settings.defaultProfile();
        TimerPreferences changed = new TimerPreferences(Duration.ofSeconds(40 * 60),
                Duration.ofSeconds(7 * 60), Duration.ofSeconds(18 * 60), 3, true, false);

        settings.updateProfile(classic.id(), "Custom classic", changed);

        assertEquals("Custom classic", settings.defaultProfile().name());
        assertEquals(changed, settings.defaultProfile().preferences());
        assertThrows(IllegalArgumentException.class, () -> new TimerPreferences(Duration.ofSeconds(59),
                Duration.ofSeconds(300), Duration.ofSeconds(600), 4, false, false));
        assertThrows(IllegalArgumentException.class, () -> new TimerPreferences(Duration.ofSeconds(60),
                Duration.ofSeconds(600), Duration.ofSeconds(300), 4, false, false));
    }

    @Test
    void requiresAReplacementDefaultBeforeDeletingAndNeverAllowsDeletingLastProfile() {
        AppSettings settings = AppSettings.defaults();
        TimerProfile classic = settings.defaultProfile();
        TimerProfile deepWork = settings.profiles().get(1);

        assertThrows(IllegalStateException.class, () -> settings.deleteProfile(classic.id()));
        settings.selectDefaultProfile(deepWork.id());
        settings.deleteProfile(classic.id());
        assertEquals(2, settings.profiles().size());
        assertTrue(settings.profiles().stream().noneMatch(profile -> profile.id().equals(classic.id())));

        settings.deleteProfile(settings.profiles().stream()
                .filter(profile -> !profile.id().equals(settings.defaultProfileId())).findFirst().orElseThrow().id());
        assertThrows(IllegalStateException.class,
                () -> settings.deleteProfile(settings.defaultProfile().id()));
    }

    @Test
    void validatesGoalRetentionAndDefaultProfileReference() {
        assertThrows(IllegalArgumentException.class, () -> new DailyFocusGoal(0));
        assertThrows(IllegalArgumentException.class, () -> new DailyFocusGoal(25));
        assertThrows(IllegalArgumentException.class, () -> new DataRetentionPolicy(6));
        assertThrows(IllegalArgumentException.class, () -> new DataRetentionPolicy(3651));
        assertTrue(DataRetentionPolicy.keepForever().retentionDays() == null);
        AppSettings settings = AppSettings.defaults();
        assertThrows(IllegalArgumentException.class,
                () -> settings.selectDefaultProfile(TimerProfileId.create()));
    }
}
