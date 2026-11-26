package com.focusdesktop.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.focusdesktop.application.port.*;
import com.focusdesktop.domain.event.*;
import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EvaluateWebsiteRequestUseCaseTest {
    private final Instant now = Instant.parse("2026-11-26T00:00:10Z");
    private final FocusSessionRepository sessions = mock(FocusSessionRepository.class);
    private final BlockingPolicyRepository policies = mock(BlockingPolicyRepository.class);
    private final WebsiteProtectionPreferences preferences = mock(WebsiteProtectionPreferences.class);
    private final DomainEventOutbox outbox = mock(DomainEventOutbox.class);
    private final List<DomainEvent> events = new ArrayList<>();
    private final BlockingPolicy policy = BlockingPolicy.create();
    private final FocusSession session = FocusSessionFactory.create(new SessionSettings(com.focusdesktop.domain.model.Duration.ofSeconds(600), com.focusdesktop.domain.model.Duration.ofSeconds(300), com.focusdesktop.domain.model.Duration.ofSeconds(600), 4, false, false), new TimeStamp(now.minusSeconds(1)));
    private EvaluateWebsiteRequestUseCase useCase(BlockingAction action) {
        session.start(new TimeStamp(now));
        policy.addRule(BlockingRule.create("Example", new WebsiteTarget("example.com"), DistractionCategory.OTHER, action, true));
        when(sessions.findActive()).thenReturn(Optional.of(session));
        when(policies.findActive()).thenReturn(Optional.of(policy));
        when(preferences.get()).thenReturn(new WebsiteProtectionPreferences.State(true, now));
        doAnswer(call -> { events.add(call.getArgument(0)); return null; }).when(outbox).append(any());
        return new EvaluateWebsiteRequestUseCase(sessions, policies, preferences,
                new RecordDistractionEventUseCase(outbox), Clock.fixed(now, ZoneOffset.UTC));
    }
    @Test void recordsHostDetectionAndOnlyConfirmsAfterTransportDenial() {
        var useCase = useCase(BlockingAction.BLOCK);
        var result = useCase.execute("music.example.com");
        assertTrue(result.block()); assertEquals(1, events.size());
        assertInstanceOf(DistractionDetected.class, events.get(0));
        result.denied().run(); result.denied().run();
        assertEquals(2, events.size()); assertInstanceOf(DistractionBlocked.class, events.get(1));
        assertTrue(useCase.execute("music.example.com").block()); assertEquals(2, events.size());
        assertFalse(useCase.execute("notexample.com").block());
    }
    @Test void disabledConsentAndPauseAndReadFailureAllowRequestsWithoutStaleBlocks() {
        var useCase = useCase(BlockingAction.BLOCK);
        session.pause(new TimeStamp(now));
        assertFalse(useCase.execute("example.com").block()); assertTrue(events.isEmpty());
        session.resume(new TimeStamp(now));
        when(preferences.get()).thenReturn(new WebsiteProtectionPreferences.State(false, null));
        assertFalse(useCase.execute("example.com").block()); assertTrue(events.isEmpty());
        when(preferences.get()).thenThrow(new IllegalStateException());
        assertFalse(useCase.execute("example.com").block()); assertNotNull(useCase.notice());
    }
    @Test void mostSpecificLogRuleDoesNotBlockOrClaimSuccess() {
        var useCase = useCase(BlockingAction.BLOCK);
        policy.addRule(BlockingRule.create("Allowed subdomain", new WebsiteTarget("study.example.com"),
                DistractionCategory.OTHER, BlockingAction.LOG, true));
        assertFalse(useCase.execute("study.example.com").block());
        assertEquals(1, events.size()); assertInstanceOf(DistractionDetected.class, events.get(0));
    }
    @Test void warnsAndRecordsFailedDenialWithoutSuccessfulOutcome() {
        var useCase = useCase(BlockingAction.WARN);
        assertFalse(useCase.execute("example.com").block()); assertNotNull(useCase.notice());
        var rule = policy.rules().get(0);
        policy.updateRule(rule.id(), rule.displayName(), rule.target(), rule.category(), BlockingAction.BLOCK, true);
        var result = useCase.execute("example.com"); result.failed().run(); result.denied().run();
        assertEquals(3, events.size()); assertInstanceOf(DistractionBlockFailed.class, events.get(2));
    }
}
