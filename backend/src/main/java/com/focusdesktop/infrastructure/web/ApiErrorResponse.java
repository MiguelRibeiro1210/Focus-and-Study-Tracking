package com.focusdesktop.infrastructure.web;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(String code, String message, List<String> details, Instant timestamp) {
    public ApiErrorResponse(String code, String message) {
        this(code, message, List.of(), Instant.now());
    }
}
