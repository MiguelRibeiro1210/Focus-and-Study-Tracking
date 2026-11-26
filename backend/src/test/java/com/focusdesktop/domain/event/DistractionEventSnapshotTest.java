package com.focusdesktop.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.focusdesktop.domain.model.BlockingAction;
import com.focusdesktop.domain.model.BlockingPolicy;
import com.focusdesktop.domain.model.BlockingRule;
import com.focusdesktop.domain.model.DistractionCategory;
import com.focusdesktop.domain.model.ObservedDistraction;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.model.WebsiteTarget;
import com.focusdesktop.domain.readmodel.DistractionLog;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DistractionEventSnapshotTest {
    @Test
    void ruleEditsAndDeletionDoNotRewriteTheDetectionOrItsOutcomeSnapshots() {
        BlockingPolicy policy = BlockingPolicy.create();
        BlockingRule rule = BlockingRule.create("Video", new WebsiteTarget("youtube.com"),
                DistractionCategory.OTHER, BlockingAction.BLOCK, true);
        policy.addRule(rule);
        ObservedDistraction observed = ObservedDistraction.website("music.youtube.com");
        DistractionDetected detection = DistractionDetected.capture(SessionId.generate(),
                policy.evaluate(observed).orElseThrow(), observed, at(0));

        policy.updateRule(rule.id(), "Changed rule", new WebsiteTarget("example.org"),
                DistractionCategory.OTHER, BlockingAction.LOG, false);
        policy.removeRule(rule.id());
        DistractionBlocked outcome = DistractionBlocked.from(detection, at(1));

        assertEquals(rule.id(), detection.decision().ruleId());
        assertEquals(new WebsiteTarget("youtube.com"), detection.decision().target());
        assertEquals(BlockingAction.BLOCK, detection.decision().action());
        assertEquals(observed, detection.observation());
        assertEquals(detection.decision(), outcome.decision());
        assertEquals(detection.observation(), outcome.observation());
        assertEquals(detection.detectionId(), outcome.detectionId());
        assertEquals(detection.sessionId(), outcome.sessionId());
        assertNotEquals(detection.eventId(), outcome.eventId());
    }

    @Test
    void failureRetainsTheSameDetectionAndTargetAndReportsAnExplicitReason() {
        DistractionDetected detection = detection(BlockingAction.BLOCK);
        DistractionBlockFailed failure = DistractionBlockFailed.from(detection, "Permission denied", at(1));

        assertEquals(detection.detectionId(), failure.detectionId());
        assertEquals(detection.sessionId(), failure.sessionId());
        assertEquals(detection.decision(), failure.decision());
        assertEquals(detection.observation(), failure.observation());
        assertEquals("Permission denied", failure.reason());
        assertNotEquals(detection.eventId(), failure.eventId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void failuresCannotHideTheReasonFromTheLearner(String reason) {
        DistractionDetected detection = detection(BlockingAction.BLOCK);
        Class<? extends RuntimeException> expected = reason == null
                ? NullPointerException.class : IllegalArgumentException.class;
        assertThrows(expected, () -> DistractionBlockFailed.from(detection, reason, at(1)));
    }

    @Test
    void loggingAndWarningDecisionsCannotBeReportedAsBlockOutcomes() {
        for (BlockingAction action : new BlockingAction[] {BlockingAction.LOG, BlockingAction.WARN}) {
            DistractionDetected detection = detection(action);
            assertThrows(IllegalArgumentException.class, () -> DistractionBlocked.from(detection, at(1)));
            assertThrows(IllegalArgumentException.class,
                    () -> DistractionBlockFailed.from(detection, "No block was requested", at(1)));
        }
    }

    @Test
    void aDetectionCannotClaimAnUnrelatedObservedTarget() {
        DistractionDetected detection = detection(BlockingAction.BLOCK);
        assertThrows(IllegalArgumentException.class, () -> DistractionDetected.capture(detection.sessionId(),
                detection.decision(), ObservedDistraction.website("example.org"), at(0)));
    }

    @Test
    void anOutcomeCannotPrecedeTheDetectionItDescribes() {
        DistractionDetected detection = detection(BlockingAction.BLOCK);
        assertThrows(IllegalArgumentException.class, () -> DistractionBlocked.from(detection, at(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> DistractionBlockFailed.from(detection, "Permission denied", at(-1)));
    }

    @Test
    void logViewRequiresACorrelatedOutcomeAndDoesNotClaimSuccessWhilePendingOrFailed() {
        DistractionDetected detection = detection(BlockingAction.BLOCK);
        DistractionLog pending = new DistractionLog(detection, Optional.empty());
        DistractionLog failed = new DistractionLog(detection, Optional.of(
                DistractionBlockFailed.from(detection, "Permission denied", at(1))));
        DistractionLog blocked = new DistractionLog(detection, Optional.of(DistractionBlocked.from(detection, at(1))));

        assertFalse(pending.blocked());
        assertFalse(failed.blocked());
        assertEquals(Optional.of("Permission denied"), failed.failureReason());
        assertTrue(blocked.blocked());
        assertEquals(detection.detectionId(), blocked.detectionId());
        DistractionBlocked unrelated = DistractionBlocked.from(detection(BlockingAction.BLOCK), at(1));
        assertThrows(IllegalArgumentException.class, () -> new DistractionLog(detection, Optional.of(unrelated)));
    }

    private static DistractionDetected detection(BlockingAction action) {
        BlockingPolicy policy = BlockingPolicy.create();
        policy.addRule(BlockingRule.create("Video", new WebsiteTarget("youtube.com"),
                DistractionCategory.OTHER, action, true));
        ObservedDistraction observation = ObservedDistraction.website("youtube.com");
        return DistractionDetected.capture(SessionId.generate(), policy.evaluate(observation).orElseThrow(),
                observation, at(0));
    }

    private static TimeStamp at(long seconds) {
        return new TimeStamp(Instant.parse("2026-11-26T00:00:00Z").plusSeconds(seconds));
    }
}
