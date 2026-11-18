package com.focusdesktop.application.port;

import java.time.Instant;

public interface SessionDataMaintenance {
    void clearTerminalHistory();
    int clearExpiredTerminalHistory(Instant endedAtOrBefore);
    void deleteAllSessionData();
}
