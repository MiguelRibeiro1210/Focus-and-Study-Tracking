package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.ApplicationCatalog;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class ListApplicationsUseCase {
    private final ApplicationCatalog catalog;
    private final Clock clock;
    private ApplicationCatalog.Scan cached;
    public ListApplicationsUseCase(ApplicationCatalog catalog, Clock clock) { this.catalog = catalog; this.clock = clock; }
    public synchronized ApplicationCatalog.Scan execute(boolean refresh) {
        if (refresh || cached == null || !clock.instant().isBefore(cached.scannedAt().plusSeconds(60))) cached = catalog.scan();
        return cached;
    }
}
