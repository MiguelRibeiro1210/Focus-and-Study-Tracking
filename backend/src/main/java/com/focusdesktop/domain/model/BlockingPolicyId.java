package com.focusdesktop.domain.model;

import java.util.Objects;
import java.util.UUID;

public record BlockingPolicyId(UUID value) {
    public BlockingPolicyId { Objects.requireNonNull(value, "value"); }

    public static BlockingPolicyId create() { return new BlockingPolicyId(UUID.randomUUID()); }
}
