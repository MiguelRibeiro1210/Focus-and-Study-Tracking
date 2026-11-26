package com.focusdesktop.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.domain.model.BlockingAction;
import com.focusdesktop.domain.model.BlockingPolicy;
import com.focusdesktop.domain.model.BlockingRule;
import com.focusdesktop.domain.model.DistractionCategory;
import com.focusdesktop.domain.model.ObservedDistraction;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.WebsiteTarget;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DistractionPolicyServiceTest {
    private final DistractionPolicyService service = new DistractionPolicyService();
    private final ObservedDistraction observed = ObservedDistraction.website("music.youtube.com");

    @Test
    void selectsAMatchingEnabledRuleOnlyDuringRunningWork() {
        BlockingPolicy policy = configuredPolicy();
        assertEquals(BlockingAction.BLOCK, service.decide(SessionState.RUNNING, SessionType.WORK,
                Optional.of(policy), observed).orElseThrow().action());
    }

    @ParameterizedTest(name = "{0} / {1} does not permit distraction monitoring")
    @MethodSource("ineligibleSessionAndIntervalStates")
    void ignoresMatchingRulesWhenTheSessionOrIntervalIsIneligible(SessionState state, SessionType intervalType) {
        assertTrue(service.decide(state, intervalType, Optional.of(configuredPolicy()), observed).isEmpty());
    }

    @Test
    void ignoresMissingEmptyAndDisabledPolicies() {
        assertTrue(service.decide(SessionState.RUNNING, SessionType.WORK, Optional.empty(), observed).isEmpty());
        assertTrue(service.decide(SessionState.RUNNING, SessionType.WORK,
                Optional.of(BlockingPolicy.create()), observed).isEmpty());
        BlockingPolicy disabled = configuredPolicy();
        disabled.setRuleEnabled(disabled.rules().get(0).id(), false);
        assertTrue(service.decide(SessionState.RUNNING, SessionType.WORK, Optional.of(disabled), observed).isEmpty());
    }

    @Test
    void reevaluationCancelsAStaleDecisionAfterPauseBreakStopDisableOrDelete() {
        BlockingPolicy policy = configuredPolicy();
        assertTrue(service.decide(SessionState.RUNNING, SessionType.WORK, Optional.of(policy), observed).isPresent());
        assertTrue(service.decide(SessionState.PAUSED, SessionType.WORK, Optional.of(policy), observed).isEmpty());
        assertTrue(service.decide(SessionState.RUNNING, SessionType.SHORT_BREAK,
                Optional.of(policy), observed).isEmpty());
        assertTrue(service.decide(SessionState.COMPLETED, SessionType.WORK,
                Optional.of(policy), observed).isEmpty());
        var ruleId = policy.rules().get(0).id();
        policy.setRuleEnabled(ruleId, false);
        assertTrue(service.decide(SessionState.RUNNING, SessionType.WORK, Optional.of(policy), observed).isEmpty());
        policy.setRuleEnabled(ruleId, true);
        policy.removeRule(ruleId);
        assertTrue(service.decide(SessionState.RUNNING, SessionType.WORK, Optional.of(policy), observed).isEmpty());
    }

    private static Stream<Arguments> ineligibleSessionAndIntervalStates() {
        return Stream.of(SessionState.values()).flatMap(state -> Stream.of(SessionType.values())
                .filter(type -> state != SessionState.RUNNING || type != SessionType.WORK)
                .map(type -> Arguments.of(state, type)));
    }

    private static BlockingPolicy configuredPolicy() {
        BlockingPolicy policy = BlockingPolicy.create();
        policy.addRule(BlockingRule.create("Video", new WebsiteTarget("youtube.com"),
                DistractionCategory.OTHER, BlockingAction.BLOCK, true));
        return policy;
    }
}
