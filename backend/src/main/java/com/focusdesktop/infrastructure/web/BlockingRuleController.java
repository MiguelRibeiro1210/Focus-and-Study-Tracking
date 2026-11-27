package com.focusdesktop.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.focusdesktop.application.dto.BlockingRuleDto;
import com.focusdesktop.application.usecase.ManageBlockingRulesUseCase;
import com.focusdesktop.application.usecase.ManageBlockingRulesUseCase.Change;
import com.focusdesktop.application.usecase.exeptions.BlockingRuleException;
import com.focusdesktop.domain.model.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/blocking-rules")
public class BlockingRuleController {
    private static final Set<String> EDITABLE = Set.of("displayName", "target", "category", "action", "enabled");
    private final ManageBlockingRulesUseCase rules;

    public BlockingRuleController(ManageBlockingRulesUseCase rules) { this.rules = rules; }

    @GetMapping
    public List<BlockingRuleDto> list() { return rules.list(); }

    @PostMapping
    public ResponseEntity<BlockingRuleDto> create(@RequestBody JsonNode body) {
        BlockingRuleDto stored = rules.create(parse(body, true));
        return ResponseEntity.created(URI.create("/api/v1/blocking-rules/" + stored.id())).body(stored);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<BlockingRuleDto>> createBatch(@RequestBody JsonNode body) {
        if (!body.isObject()) throw BlockingRuleException.invalid("request", "Expected an object containing rules");
        fields(body, Set.of("rules"), "request");
        var entries = required(body, "rules");
        if (!entries.isArray() || entries.isEmpty() || entries.size() > 100)
            throw BlockingRuleException.invalid("rules", "Select between 1 and 100 applications");
        var changes = new java.util.ArrayList<Change>();
        for (var entry : entries) {
            try { changes.add(parse(entry, true)); }
            catch (IllegalArgumentException exception) { throw BlockingRuleException.invalid("rules", "Each application needs a complete rule object"); }
        }
        return ResponseEntity.status(201).body(rules.createBatch(changes));
    }

    @PatchMapping("/{id}")
    public BlockingRuleDto patch(@PathVariable UUID id, @RequestBody JsonNode body) { return rules.patch(id, parse(body, false)); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { rules.delete(id); return ResponseEntity.noContent().build(); }

    @ExceptionHandler(BlockingRuleException.class)
    public ResponseEntity<ApiErrorResponse> error(BlockingRuleException exception) {
        int status = switch (exception.kind()) { case INVALID -> 422; case DUPLICATE -> 409; case NOT_FOUND -> 404; };
        String code = switch (exception.kind()) { case INVALID -> "VALIDATION_ERROR"; case DUPLICATE -> "DUPLICATE_RULE"; case NOT_FOUND -> "RESOURCE_NOT_FOUND"; };
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, exception.getMessage(), exception.details(), Instant.now()));
    }

    private static Change parse(JsonNode body, boolean create) {
        if (!body.isObject()) throw new IllegalArgumentException("Request body must be a JSON object");
        fields(body, EDITABLE, "request");
        if (body.isEmpty()) throw BlockingRuleException.invalid("request", "At least one editable field is required");
        if (create) for (String field : EDITABLE) required(body, field);
        return new Change(body.has("displayName") ? text(body, "displayName") : null,
                body.has("target") ? target(body.get("target")) : null,
                body.has("category") ? enumeration(body, "category", DistractionCategory.class) : null,
                body.has("action") ? enumeration(body, "action", BlockingAction.class) : null,
                body.has("enabled") ? bool(body, "enabled") : null);
    }

    private static DistractionTarget target(JsonNode node) {
        if (!node.isObject()) throw BlockingRuleException.invalid("target", "Target must be a typed object");
        String type = text(node, "type");
        try {
            if (type.equals("WEBSITE")) {
                fields(node, Set.of("type", "domain"), "target");
                return new WebsiteTarget(text(node, "domain"));
            }
            if (type.equals("APPLICATION")) {
                fields(node, Set.of("type", "platform", "matchKind", "value"), "target");
                return new ApplicationTarget(enumeration(node, "platform", TargetPlatform.class),
                        enumeration(node, "matchKind", ApplicationMatchKind.class), text(node, "value"));
            }
        } catch (IllegalArgumentException exception) { throw BlockingRuleException.invalid("target", exception.getMessage()); }
        throw BlockingRuleException.invalid("target.type", "Unsupported target type");
    }

    private static void fields(JsonNode body, Set<String> allowed, String path) {
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw BlockingRuleException.invalid(path + "." + field, "Unknown or read-only field");
            if (body.get(field).isNull()) throw BlockingRuleException.invalid(path + "." + field, "Null values are not allowed");
        });
    }

    private static JsonNode required(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) throw BlockingRuleException.invalid(field, "Required field is missing");
        return value;
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = required(body, field);
        if (!value.isTextual()) throw BlockingRuleException.invalid(field, "Expected a string");
        return value.textValue();
    }

    private static boolean bool(JsonNode body, String field) {
        JsonNode value = required(body, field);
        if (!value.isBoolean()) throw BlockingRuleException.invalid(field, "Expected a boolean");
        return value.booleanValue();
    }

    private static <E extends Enum<E>> E enumeration(JsonNode body, String field, Class<E> type) {
        try { return Enum.valueOf(type, text(body, field)); }
        catch (IllegalArgumentException exception) { throw BlockingRuleException.invalid(field, "Unsupported value for " + field); }
    }
}
