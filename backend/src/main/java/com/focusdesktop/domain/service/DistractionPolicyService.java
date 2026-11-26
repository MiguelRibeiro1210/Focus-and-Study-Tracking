package com.focusdesktop.domain.service;

import com.focusdesktop.domain.model.BlockingDecision;
import com.focusdesktop.domain.model.BlockingPolicy;
import com.focusdesktop.domain.model.ObservedDistraction;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import java.util.Objects;
import java.util.Optional;

/** Pure focus-eligibility check and rule evaluation; does not collect or block observations. */
public final class DistractionPolicyService {
    public Optional<BlockingDecision> decide(SessionState state, SessionType intervalType,
                                             Optional<BlockingPolicy> policy, ObservedDistraction observation) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(intervalType, "intervalType");
        Objects.requireNonNull(policy, "policy");
        if (state != SessionState.RUNNING || intervalType != SessionType.WORK) return Optional.empty();
        return policy.flatMap(active -> active.evaluate(observation));
    }
}
