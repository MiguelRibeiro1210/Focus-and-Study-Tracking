package com.focusdesktop.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.application.port.SessionSettingsProvider;
import com.focusdesktop.domain.model.AppSettings;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.TimerPreferences;
import com.focusdesktop.domain.repository.AppSettingsRepository;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:app_settings_adapter_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(JpaAppSettingsRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaAppSettingsRepositoryAdapterTest {
    @Autowired private AppSettingsRepository settings;
    @Autowired private SessionSettingsProvider sessionSettings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearSettings() {
        jdbc.update("DELETE FROM timer_profile");
        jdbc.update("DELETE FROM app_settings");
    }

    @Test
    void concurrentFirstReadsShareOneCompleteSetOfDefaults() throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        try {
            var reads = new ArrayList<Future<AppSettings>>();
            for (int index = 0; index < 6; index++) {
                reads.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return new TransactionTemplate(transactionManager).execute(status -> settings.loadOrCreate());
                }));
            }
            start.countDown();
            var defaultId = reads.get(0).get(10, TimeUnit.SECONDS).defaultProfileId();
            for (var read : reads) {
                AppSettings loaded = read.get(10, TimeUnit.SECONDS);
                assertEquals(defaultId, loaded.defaultProfileId());
                assertEquals(3, loaded.profiles().size());
                assertEquals("Classic", loaded.defaultProfile().name());
            }
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_settings", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM timer_profile", Integer.class));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void sessionSettingsAreAvailableBeforeInitializingCallerFinishesAndSurviveItsRollback() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var initialized = new CountDownLatch(1);
        var releaseCaller = new CountDownLatch(1);
        try {
            var caller = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                AppSettings loaded = settings.loadOrCreate();
                initialized.countDown();
                try {
                    assertTrue(releaseCaller.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                status.setRollbackOnly();
                return loaded;
            }));
            assertTrue(initialized.await(5, TimeUnit.SECONDS));
            var sessionRead = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> sessionSettings.findCurrentSettings().orElseThrow()));
            assertEquals(25 * 60, sessionRead.get(5, TimeUnit.SECONDS).workDuration().seconds());
            releaseCaller.countDown();
            AppSettings loaded = caller.get(5, TimeUnit.SECONDS);
            assertEquals(loaded.defaultProfileId(), settings.loadOrCreate().defaultProfileId());
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM timer_profile", Integer.class));
        } finally {
            releaseCaller.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

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
