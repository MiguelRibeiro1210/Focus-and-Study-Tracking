package com.focusdesktop.domain.model;

import java.util.Objects;

/** Immutable entity state; replacing it through its policy preserves aggregate invariants. */
public record BlockingRule(BlockingRuleId id, String displayName, DistractionTarget target,
                           DistractionCategory category, BlockingAction action, boolean enabled) {
    public BlockingRule {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (displayName.isEmpty()) throw new IllegalArgumentException("A rule needs a display name");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(action, "action");
    }

    public static BlockingRule create(String displayName, DistractionTarget target,
                                      DistractionCategory category, BlockingAction action, boolean enabled) {
        return new BlockingRule(BlockingRuleId.create(), displayName, target, category, action, enabled);
    }
}
