package com.focusdesktop.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focusdesktop.domain.model.*;
import java.time.Instant;
import java.util.UUID;

public record BlockingRuleDto(UUID id, String displayName, Target target, DistractionCategory category,
                              BlockingAction action, boolean enabled, Instant createdAt, Instant updatedAt) {
    public BlockingRule toDomain() {
        return new BlockingRule(new BlockingRuleId(id), displayName, target.toDomain(), category, action, enabled);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Target(String type, TargetPlatform platform, ApplicationMatchKind matchKind,
                         String value, String domain) {
        public DistractionTarget toDomain() {
            return switch (type) {
                case "APPLICATION" -> new ApplicationTarget(platform, matchKind, value);
                case "WEBSITE" -> new WebsiteTarget(domain);
                default -> throw new IllegalArgumentException("Unsupported target type");
            };
        }

        public static Target from(DistractionTarget target) {
            if (target instanceof ApplicationTarget app) {
                return new Target("APPLICATION", app.platform(), app.matchKind(), app.value(), null);
            }
            return new Target("WEBSITE", null, null, null, ((WebsiteTarget) target).domain());
        }
    }
}
