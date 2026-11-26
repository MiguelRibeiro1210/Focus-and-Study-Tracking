package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.BlockingRuleDto;
import com.focusdesktop.application.port.BlockingRuleAccess;
import com.focusdesktop.application.usecase.exeptions.BlockingRuleException;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.BlockingPolicyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageBlockingRulesUseCase {
    private final BlockingPolicyRepository policies;
    private final BlockingRuleAccess access;

    public ManageBlockingRulesUseCase(BlockingPolicyRepository policies, BlockingRuleAccess access) {
        this.policies = policies;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<BlockingRuleDto> list() { return access.list(); }

    @Transactional
    public BlockingRuleDto create(Change change) {
        BlockingPolicy policy = lockAndLoad();
        ensureUnique(policy, change.target(), null);
        BlockingRule rule;
        try {
            rule = BlockingRule.create(change.displayName(), change.target(), change.category(), change.action(), change.enabled());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw BlockingRuleException.invalid("displayName", "A valid display name and complete rule are required");
        }
        policy.addRule(rule);
        policies.save(policy);
        return access.find(rule.id().value()).orElseThrow(BlockingRuleException::missing);
    }

    @Transactional
    public BlockingRuleDto patch(UUID id, Change change) {
        BlockingPolicy policy = lockAndLoad();
        BlockingRule old = policy.rules().stream().filter(rule -> rule.id().value().equals(id))
                .findFirst().orElseThrow(BlockingRuleException::missing);
        DistractionTarget target = change.target() == null ? old.target() : change.target();
        ensureUnique(policy, target, old.id());
        try {
            policy.updateRule(old.id(), change.displayName() == null ? old.displayName() : change.displayName(),
                    target, change.category() == null ? old.category() : change.category(),
                    change.action() == null ? old.action() : change.action(),
                    change.enabled() == null ? old.enabled() : change.enabled());
        } catch (IllegalArgumentException exception) {
            throw BlockingRuleException.invalid("displayName", exception.getMessage());
        }
        policies.save(policy);
        return access.find(id).orElseThrow(BlockingRuleException::missing);
    }

    @Transactional
    public void delete(UUID id) {
        BlockingPolicy policy = lockAndLoad();
        BlockingRule rule = policy.rules().stream().filter(item -> item.id().value().equals(id))
                .findFirst().orElseThrow(BlockingRuleException::missing);
        policy.removeRule(rule.id());
        policies.save(policy);
    }

    @Transactional
    public void clear() {
        access.lockActivePolicy();
        access.clear();
    }

    private BlockingPolicy lockAndLoad() {
        access.lockActivePolicy();
        return policies.findActive().orElseThrow(() -> new IllegalStateException("Seeded blocking policy is missing"));
    }

    private static void ensureUnique(BlockingPolicy policy, DistractionTarget target, BlockingRuleId except) {
        if (policy.rules().stream().anyMatch(rule -> !rule.id().equals(except) && rule.target().equals(target))) {
            throw BlockingRuleException.duplicate();
        }
    }

    /** Null fields represent omissions only; HTTP explicitly supplied nulls are rejected before this boundary. */
    public record Change(String displayName, DistractionTarget target, DistractionCategory category,
                         BlockingAction action, Boolean enabled) { }
}
