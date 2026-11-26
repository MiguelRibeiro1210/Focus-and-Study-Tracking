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
            if (!Set.of("enabled", "consent", "routingMethod").contains(field)) throw new IllegalArgumentException("Unknown website protection field");
        });
        if (body.has("consent") && !body.get("consent").isTextual()) throw new IllegalArgumentException("Consent must be a string");
        if (body.has("routingMethod") && !body.get("routingMethod").isTextual()) throw new IllegalArgumentException("routingMethod must be a string");
        var method = body.has("routingMethod")
                ? com.focusdesktop.application.port.WebsiteProtectionPreferences.RoutingMethod.valueOf(body.get("routingMethod").asText())
                : runtime.status().routingMethod();
        return runtime.configure(body.get("enabled").booleanValue(), body.path("consent").asText(null), method);
    }
    @PostMapping("/api/v1/website-protection/brave")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public WebsiteProtectionRuntime.Status brave(@RequestBody JsonNode body) {
        if (!body.isObject() || body.size() != 1 || !body.path("confirmation").isTextual()
                || !body.get("confirmation").asText().equals("OPEN_EXISTING_BRAVE_PROFILE"))
            throw new IllegalArgumentException("Explicit existing-profile Brave setup confirmation is required");
        return runtime.requestBraveSetup();
    }
}
