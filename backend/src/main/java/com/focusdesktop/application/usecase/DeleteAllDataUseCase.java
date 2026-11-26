package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.SessionDataMaintenance;
import com.focusdesktop.domain.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteAllDataUseCase {
    public static final String CONFIRMATION = "DELETE_ALL_DATA";
    private final SessionDataMaintenance maintenance;
    private final AppSettingsRepository settings;
    private final ManageBlockingRulesUseCase blockingRules;

    public DeleteAllDataUseCase(SessionDataMaintenance maintenance, AppSettingsRepository settings,
                                ManageBlockingRulesUseCase blockingRules) {
        this.maintenance = maintenance;
        this.settings = settings;
        this.blockingRules = blockingRules;
    }

    @Transactional
    public void execute(String confirmation) {
        if (!CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("Explicit delete-all confirmation is required");
        }
        blockingRules.clear();
        maintenance.deleteAllSessionData();
        settings.resetToDefaults();
    }
}
