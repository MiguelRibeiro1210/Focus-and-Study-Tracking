package com.focusdesktop.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.focusdesktop.infrastructure.proxy.WebsiteProtectionRuntime;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebsiteProtectionController {
    private final WebsiteProtectionRuntime runtime;
    public WebsiteProtectionController(WebsiteProtectionRuntime runtime) { this.runtime = runtime; }
    @GetMapping("/api/v1/website-protection")
    public WebsiteProtectionRuntime.Status status() { return runtime.status(); }
    @PatchMapping("/api/v1/website-protection")
    public WebsiteProtectionRuntime.Status configure(@RequestBody JsonNode body) {
        if (!body.isObject() || !body.path("enabled").isBoolean()) throw new IllegalArgumentException("enabled must be a boolean");
        body.fieldNames().forEachRemaining(field -> {
            if (!Set.of("enabled", "consent").contains(field)) throw new IllegalArgumentException("Unknown website protection field");
        });
        if (body.has("consent") && !body.get("consent").isTextual()) throw new IllegalArgumentException("Consent must be a string");
        return runtime.configure(body.get("enabled").booleanValue(), body.path("consent").asText(null));
    }
}
