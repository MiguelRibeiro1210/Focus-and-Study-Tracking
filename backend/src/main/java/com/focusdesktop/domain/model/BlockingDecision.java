package com.focusdesktop.domain.model;

import java.util.Objects;

/** Immutable snapshot of a matching rule; no match is represented by Optional.empty(). */
public record BlockingDecision(BlockingRuleId ruleId, DistractionTarget target,
                               DistractionCategory category, BlockingAction action) {
    public BlockingDecision {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(action, "action");
    }
}
