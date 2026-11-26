package com.focusdesktop.application.usecase.exeptions;

import java.util.List;

public final class BlockingRuleException extends RuntimeException {
    public enum Kind { INVALID, DUPLICATE, NOT_FOUND }
    private final Kind kind;
    private final List<String> details;

    public BlockingRuleException(Kind kind, String message, List<String> details) {
        super(message);
        this.kind = kind;
        this.details = List.copyOf(details);
    }

    public static BlockingRuleException invalid(String field, String message) {
        return new BlockingRuleException(Kind.INVALID, message, List.of(field));
    }

    public static BlockingRuleException duplicate() {
        return new BlockingRuleException(Kind.DUPLICATE, "A rule already exists for this target", List.of("target"));
    }

    public static BlockingRuleException missing() {
        return new BlockingRuleException(Kind.NOT_FOUND, "Blocking rule not found", List.of("id"));
    }

    public Kind kind() { return kind; }
    public List<String> details() { return details; }
}
