package com.focusdesktop.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.domain.model.AppSettings;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.TimerPreferences;
import com.focusdesktop.domain.repository.AppSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:app_settings_adapter_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(JpaAppSettingsRepositoryAdapter.class)
class JpaAppSettingsRepositoryAdapterTest {
    @Autowired private AppSettingsRepository settings;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsDefaultsPersistsProfileEditsAndReloadsTheSelectedProfile() {
        AppSettings created = settings.loadOrCreate();
        assertEquals(3, created.profiles().size());
        assertEquals("Classic", created.defaultProfile().name());

        var deepWork = created.profiles().get(1);
        created.selectDefaultProfile(deepWork.id());
        created.updateProfile(deepWork.id(), "Deep work edited", new TimerPreferences(
                Duration.ofSeconds(55 * 60), Duration.ofSeconds(9 * 60), Duration.ofSeconds(20 * 60),
                4, true, false));
        created.updateApplicationPreferences(new com.focusdesktop.domain.model.DailyFocusGoal(6),
                com.focusdesktop.domain.model.ApplicationTheme.DARK, false,
                new com.focusdesktop.domain.model.DataRetentionPolicy(180));
        settings.save(created);

        AppSettings loaded = settings.loadOrCreate();
        assertEquals("Deep work edited", loaded.defaultProfile().name());
        assertEquals(55 * 60, loaded.defaultProfile().preferences().workDuration().seconds());
        assertEquals(6, loaded.dailyFocusGoal().hours());
        assertEquals(com.focusdesktop.domain.model.ApplicationTheme.DARK, loaded.theme());
        assertEquals(180, loaded.retentionPolicy().retentionDays());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_settings", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM timer_profile", Integer.class));
    }

    @Test
    void resetRestoresSeedDataAfterSavedPreferenceChanges() {
        AppSettings changed = settings.loadOrCreate();
        changed.updateApplicationPreferences(new com.focusdesktop.domain.model.DailyFocusGoal(8),
                com.focusdesktop.domain.model.ApplicationTheme.DARK, false,
                new com.focusdesktop.domain.model.DataRetentionPolicy(60));
        settings.save(changed);

        AppSettings reset = settings.resetToDefaults();

        assertEquals("Classic", reset.defaultProfile().name());
        assertEquals(3, reset.profiles().size());
        assertEquals(3, reset.dailyFocusGoal().hours());
        assertEquals(com.focusdesktop.domain.model.ApplicationTheme.LIGHT, reset.theme());
        assertTrue(reset.notificationsEnabled());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM timer_profile", Integer.class));
    }
}
