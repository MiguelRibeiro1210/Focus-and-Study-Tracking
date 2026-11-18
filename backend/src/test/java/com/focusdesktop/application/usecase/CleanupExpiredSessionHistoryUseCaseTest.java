package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.focusdesktop.application.port.SessionDataMaintenance;
import com.focusdesktop.domain.model.AppSettings;
import com.focusdesktop.domain.model.ApplicationTheme;
import com.focusdesktop.domain.model.DailyFocusGoal;
import com.focusdesktop.domain.model.DataRetentionPolicy;
import com.focusdesktop.domain.repository.AppSettingsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CleanupExpiredSessionHistoryUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    @Mock private SessionDataMaintenance maintenance;
    @Mock private AppSettingsRepository settingsRepository;

    @Test
    void usesConfiguredRetentionAgeAndBackendClockForCutoff() {
        AppSettings settings = AppSettings.defaults();
        settings.updateApplicationPreferences(new DailyFocusGoal(3), ApplicationTheme.LIGHT, true,
                new DataRetentionPolicy(7));
        when(settingsRepository.loadOrCreate()).thenReturn(settings);
        when(maintenance.clearExpiredTerminalHistory(Instant.parse("2026-11-11T12:00:00Z"))).thenReturn(2);

        int removed = useCase().execute();

        assertEquals(2, removed);
        verify(maintenance).clearExpiredTerminalHistory(Instant.parse("2026-11-11T12:00:00Z"));
    }

    @Test
    void keepForeverDoesNotInvokePersistenceCleanup() {
        when(settingsRepository.loadOrCreate()).thenReturn(AppSettings.defaults());

        assertEquals(0, useCase().execute());

        verifyNoInteractions(maintenance);
    }

    private CleanupExpiredSessionHistoryUseCase useCase() {
        return new CleanupExpiredSessionHistoryUseCase(maintenance, settingsRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
