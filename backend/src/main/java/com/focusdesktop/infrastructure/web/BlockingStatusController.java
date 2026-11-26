package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.usecase.DetectActiveApplicationUseCase;
import com.focusdesktop.application.usecase.ApplicationProtectionCoordinator;
import com.focusdesktop.application.port.ProtectionPreferences;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockingStatusController {
    private final DetectActiveApplicationUseCase detect;
    private final ApplicationProtectionCoordinator protection;
    private final ProtectionPreferences preferences;
    public BlockingStatusController(DetectActiveApplicationUseCase detect, ApplicationProtectionCoordinator protection,
                                    ProtectionPreferences preferences) {
        this.detect = detect; this.protection = protection; this.preferences = preferences;
    }
    @GetMapping("/api/v1/blocking-status")
    public Status status() {
        var detection = detect.status(); var runtime = protection.status(); var configured = preferences.get();
        String state = !configured.applicationsEnabled() ? runtime.ownedRestrictions() > 0 || runtime.applicationProtectionState().equals("RESTORING") ? "RESTORING" : "DISABLED"
                : runtime.applicationProtectionState().equals("DISABLED") ? "READY" : runtime.applicationProtectionState();
        return new Status(detection.monitorEnabled(), detection.applicationDetection(), detection.applicationReason(),
                runtime.ownedRestrictions() > 0, false, detection.checkedAt(), configured.applicationsEnabled(),
                runtime.ownedRestrictions(), state, runtime.notice());
    }

    @PatchMapping("/api/v1/blocking-protection")
    public ProtectionPreferences.State configure(@RequestBody JsonNode body) {
        if (!body.isObject() || !body.path("applicationsEnabled").isBoolean()) throw new IllegalArgumentException("applicationsEnabled must be a boolean");
        body.fieldNames().forEachRemaining(field -> {
            if (!Set.of("applicationsEnabled", "consent").contains(field)) throw new IllegalArgumentException("Unknown protection preference field");
        });
        if (body.has("consent") && !body.get("consent").isTextual()) throw new IllegalArgumentException("Consent must be a string");
        return preferences.setApplicationsEnabled(body.get("applicationsEnabled").booleanValue(), body.path("consent").asText(null));
    }
    public record Status(boolean monitorEnabled, String applicationDetection, String applicationReason,
                         boolean applicationEnforcement, boolean websiteEnforcement, Instant checkedAt,
                         boolean applicationProtectionEnabled, int ownedRestrictions, String applicationProtectionState,
                         ApplicationProtectionCoordinator.Notice notice) { }
}
