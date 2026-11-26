package com.focusdesktop.domain.model;

import java.util.Objects;
import java.util.UUID;

public record BlockingRuleId(UUID value) {
    public BlockingRuleId { Objects.requireNonNull(value, "value"); }

    public static BlockingRuleId create() { return new BlockingRuleId(UUID.randomUUID()); }
}
