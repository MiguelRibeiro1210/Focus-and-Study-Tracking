package com.focusdesktop.application.port;

import com.focusdesktop.application.dto.BlockingRuleDto;
import com.focusdesktop.domain.model.TargetPlatform;
import java.time.Instant;
import java.util.List;

public interface ApplicationCatalog {
    Scan scan();
    record Entry(String id, String displayName, String source, BlockingRuleDto.Target target, boolean selectable, String reason) { }
    record Scan(TargetPlatform platform, List<Entry> applications, boolean partial, List<String> warnings, Instant scannedAt) {
        public Scan { applications = List.copyOf(applications); warnings = List.copyOf(warnings); }
    }
}
