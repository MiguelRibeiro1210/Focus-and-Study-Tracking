package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.SessionDataMaintenance;
import com.focusdesktop.domain.repository.AppSettingsRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CleanupExpiredSessionHistoryUseCase {
    private final SessionDataMaintenance maintenance;
    private final AppSettingsRepository settings;
    private final Clock clock;

    public CleanupExpiredSessionHistoryUseCase(SessionDataMaintenance maintenance,
                                               AppSettingsRepository settings,
                                               Clock clock) {
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public int execute() {
        Integer retentionDays = settings.loadOrCreate().retentionPolicy().retentionDays();
        if (retentionDays == null) return 0;
        return maintenance.clearExpiredTerminalHistory(clock.instant().minus(retentionDays, ChronoUnit.DAYS));
    }
}
