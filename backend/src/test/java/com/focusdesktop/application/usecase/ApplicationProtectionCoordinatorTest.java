package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.focusdesktop.application.port.*;
import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;

class ApplicationProtectionCoordinatorTest {
    private final Instant now = Instant.parse("2026-11-26T00:00:10Z");
    private final FocusSessionRepository sessions = mock(FocusSessionRepository.class);
    private final BlockingPolicyRepository policies = mock(BlockingPolicyRepository.class);
    private final ProtectionPreferences preferences = mock(ProtectionPreferences.class);
    private final ApplicationSuspensionPort nativeApps = mock(ApplicationSuspensionPort.class);
    private final DomainEventOutbox outbox = mock(DomainEventOutbox.class);
    private final RecordDistractionEventUseCase recorder = new RecordDistractionEventUseCase(outbox);
    private final List<DomainEvent> events = new ArrayList<>();
    private final FocusSession session = session();
    private final BlockingPolicy policy = BlockingPolicy.create();
    private final ActiveApplicationPort.Observation observation = new ActiveApplicationPort.Observation(123,
            now.minusSeconds(20), new AppIdentifier(TargetPlatform.LINUX, "/usr/bin/chat", "chat"));

    private ApplicationProtectionCoordinator coordinator(BlockingAction action) {
        policy.addRule(BlockingRule.create("Chat", new ApplicationTarget(TargetPlatform.LINUX,
                ApplicationMatchKind.PROCESS_NAME, "chat"), DistractionCategory.OTHER, action, true));
        when(sessions.findActive()).thenReturn(Optional.of(session));
        when(policies.findActive()).thenReturn(Optional.of(policy));
        when(preferences.get()).thenReturn(new ProtectionPreferences.State(true, now));
        when(nativeApps.isSuspended(any())).thenReturn(true);
        doAnswer(call -> { events.add(call.getArgument(0)); return null; }).when(outbox).append(any());
        return new ApplicationProtectionCoordinator(sessions, policies, preferences, nativeApps, recorder,
                Clock.fixed(now, ZoneOffset.UTC), true);
    }

    @Test
    void aPauseDuringNativeSuspensionRestoresImmediatelyAndRecordsFailure() {
        var coordinator = coordinator(BlockingAction.BLOCK);
        doAnswer(call -> { session.pause(new TimeStamp(now)); return null; }).when(nativeApps).suspend(any());
        coordinator.observe(detected());
        verify(nativeApps).resume(observation);
        assertEquals(0, coordinator.status().ownedRestrictions());
        assertEquals(1, events.stream().filter(DistractionBlockFailed.class::isInstance).count());
        assertTrue(events.stream().noneMatch(DistractionBlocked.class::isInstance));
    }

    @Test
    void aNewInstanceIsProtectedAndLossOfOneRestrictionDoesNotReleaseTheOther() {
        var coordinator = coordinator(BlockingAction.BLOCK);
        coordinator.observe(detected());
        var second = new ActiveApplicationPort.Observation(456, observation.startedAt(), observation.identity());
        coordinator.observe(new DetectActiveApplicationUseCase.DetectedApplication(session.id(), session.currentInterval().id(), second, now));
        assertEquals(2, coordinator.status().ownedRestrictions());
        when(nativeApps.isSuspended(observation)).thenReturn(false);
        coordinator.reconcile();
        verify(nativeApps).resume(observation); verify(nativeApps, never()).resume(second);
        assertEquals(1, coordinator.status().ownedRestrictions());
    }

    @Test
    void failedRestorationBlocksNewSuspensionsUntilOwnedRecoverySucceeds() {
        var coordinator = coordinator(BlockingAction.BLOCK);
        doThrow(new IllegalStateException("Native failure")).when(nativeApps).suspend(any());
        doThrow(new IllegalStateException("Restore failed")).when(nativeApps).resume(any());
        coordinator.observe(detected());
        assertEquals("RESTORING", coordinator.status().applicationProtectionState());
        coordinator.observe(detected()); verify(nativeApps, times(1)).suspend(any());
        coordinator.reconcile(); verify(nativeApps).releaseAll();
        assertNotEquals("RESTORING", coordinator.status().applicationProtectionState());
    }

    @Test
    void recordsDetectionBeforeSuspensionAndConfirmsOnlyAfterNativeSuccess() {
        var coordinator = coordinator(BlockingAction.BLOCK);
        coordinator.observe(detected());
        var order = inOrder(outbox, nativeApps);
        order.verify(outbox).append(isA(DistractionDetected.class));
        order.verify(nativeApps).suspend(observation);
        order.verify(outbox).append(isA(DistractionBlocked.class));
        assertEquals(1, coordinator.status().ownedRestrictions());
        coordinator.observe(detected());
        assertEquals(2, events.size());
    }

    @Test
    void logAndWarnNeverSuspendAndRepeatedObservationsAreDebounced() {
        for (BlockingAction action : List.of(BlockingAction.LOG, BlockingAction.WARN)) {
            policy.rules().forEach(rule -> policy.removeRule(rule.id())); events.clear(); reset(nativeApps);
            var coordinator = coordinator(action);
            coordinator.observe(detected()); coordinator.observe(detected());
            assertEquals(1, events.size()); verify(nativeApps, never()).suspend(any());
            if (action == BlockingAction.WARN) assertNotNull(coordinator.status().notice());
        }
    }

    @Test
    void pausedOrDisabledConsentNeverSuspendsAndDisablingRuleRestoresOwnedInstance() {
        var coordinator = coordinator(BlockingAction.BLOCK);
        coordinator.observe(detected());
        policy.setRuleEnabled(policy.rules().get(0).id(), false);
        coordinator.reconcile();
        verify(nativeApps).resume(observation);
        assertEquals(0, coordinator.status().ownedRestrictions());
        when(preferences.get()).thenReturn(new ProtectionPreferences.State(false, null));
        coordinator.observe(detected());
        verify(nativeApps, times(1)).suspend(any());
    }

    @Test
    void pauseAndReadFailuresReleaseRestrictionsRatherThanKeepStaleBlocking() {
        var coordinator = coordinator(BlockingAction.BLOCK); coordinator.observe(detected());
        session.pause(new TimeStamp(now)); coordinator.reconcile();
        verify(nativeApps).resume(observation);
        assertEquals(0, coordinator.status().ownedRestrictions());
        when(sessions.findActive()).thenThrow(new IllegalStateException("Database unavailable"));
        coordinator.reconcile(); verify(nativeApps).releaseAll();
        assertEquals("UNAVAILABLE", coordinator.status().applicationProtectionState());
    }

    @Test
    void nativeFailureProducesCorrelatedFailureAndNeverSuccessfulOutcome() {
        var coordinator = coordinator(BlockingAction.BLOCK);
        doThrow(new IllegalStateException("Raw private native output")).when(nativeApps).suspend(any());
        coordinator.observe(detected());
        assertEquals(2, events.size());
        var detection = (DistractionDetected) events.get(0);
        var failure = (DistractionBlockFailed) events.get(1);
        assertEquals(detection.detectionId(), failure.detectionId());
        assertFalse(failure.reason().contains("Raw"));
        assertEquals(0, coordinator.status().ownedRestrictions());
    }

    private DetectActiveApplicationUseCase.DetectedApplication detected() {
        return new DetectActiveApplicationUseCase.DetectedApplication(session.id(), session.currentInterval().id(), observation, now);
    }
    private FocusSession session() {
        var start = new TimeStamp(Instant.parse("2026-11-26T00:00:00Z"));
        var result = FocusSessionFactory.create(new SessionSettings(Duration.ofSeconds(60), Duration.ofSeconds(60),
                Duration.ofSeconds(120), 4, false, false), start); result.start(start); return result;
    }
}
