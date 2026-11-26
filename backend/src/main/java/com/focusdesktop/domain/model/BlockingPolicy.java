package com.focusdesktop.domain.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Aggregate root for the configured rules. Evaluation has no external side effects. */
public final class BlockingPolicy {
    private final BlockingPolicyId id;
    private final Map<BlockingRuleId, BlockingRule> rules = new LinkedHashMap<>();

    public BlockingPolicy(BlockingPolicyId id, List<BlockingRule> rules) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rules, "rules").forEach(this::addRule);
    }

    public static BlockingPolicy create() { return new BlockingPolicy(BlockingPolicyId.create(), List.of()); }

    public void addRule(BlockingRule rule) {
        Objects.requireNonNull(rule, "rule");
        if (rules.containsKey(rule.id())) throw new IllegalArgumentException("Rule ID already exists");
        ensureUniqueTarget(rule);
        rules.put(rule.id(), rule);
    }

    public void updateRule(BlockingRuleId ruleId, String displayName, DistractionTarget target,
                           DistractionCategory category, BlockingAction action, boolean enabled) {
        requireRule(ruleId);
        BlockingRule replacement = new BlockingRule(ruleId, displayName, target, category, action, enabled);
        ensureUniqueTarget(replacement);
        rules.put(ruleId, replacement);
    }

    public void setRuleEnabled(BlockingRuleId ruleId, boolean enabled) {
        BlockingRule rule = requireRule(ruleId);
        rules.put(ruleId, new BlockingRule(rule.id(), rule.displayName(), rule.target(),
                rule.category(), rule.action(), enabled));
    }

    public void removeRule(BlockingRuleId ruleId) {
        requireRule(ruleId);
        rules.remove(ruleId);
    }

    public Optional<BlockingDecision> evaluate(ObservedDistraction observation) {
        Objects.requireNonNull(observation, "observation");
        return rules.values().stream().filter(BlockingRule::enabled)
                .filter(rule -> rule.target().matches(observation))
                .max(Comparator.comparingInt(rule -> rule.target().specificity()))
                .map(rule -> new BlockingDecision(rule.id(), rule.target(), rule.category(), rule.action()));
    }

    private void ensureUniqueTarget(BlockingRule candidate) {
        if (rules.values().stream().anyMatch(rule -> !rule.id().equals(candidate.id())
                && rule.target().equals(candidate.target()))) {
            throw new IllegalArgumentException("A rule already exists for this target");
        }
    }

    private BlockingRule requireRule(BlockingRuleId ruleId) {
        BlockingRule rule = rules.get(Objects.requireNonNull(ruleId, "ruleId"));
        if (rule == null) throw new IllegalArgumentException("Unknown blocking rule");
        return rule;
    }

    public BlockingPolicyId id() { return id; }
    public List<BlockingRule> rules() { return List.copyOf(rules.values()); }
}
