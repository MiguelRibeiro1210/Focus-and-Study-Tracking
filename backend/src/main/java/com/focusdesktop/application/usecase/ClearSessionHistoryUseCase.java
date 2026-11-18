package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.SessionDataMaintenance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClearSessionHistoryUseCase {
    private final SessionDataMaintenance maintenance;

    public ClearSessionHistoryUseCase(SessionDataMaintenance maintenance) { this.maintenance = maintenance; }

    @Transactional
    public void execute() { maintenance.clearTerminalHistory(); }
}
