package com.focusdesktop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BlockingPolicyTest {
    @Test
    void prefersExecutablePathToProcessNameRegardlessOfRuleInsertionOrder() {
        BlockingRule process = rule("Chat", new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.PROCESS_NAME, "chat.exe"), BlockingAction.BLOCK);
        BlockingRule path = rule("Specific installation", new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, "C:\\Apps\\Chat.exe"), BlockingAction.WARN);
        ObservedDistraction observed = ObservedDistraction.application(
                new AppIdentifier(TargetPlatform.WINDOWS, "c:/apps/chat.exe", "CHAT.EXE"));

        for (List<BlockingRule> order : List.of(List.of(process, path), List.of(path, process))) {
            BlockingPolicy policy = BlockingPolicy.create();
            order.forEach(policy::addRule);
            BlockingDecision decision = policy.evaluate(observed).orElseThrow();
            assertEquals(path.id(), decision.ruleId());
            assertEquals(BlockingAction.WARN, decision.action());
        }
    }

    @Test
    void choosesTheMostSpecificEnabledWebsiteDomainAndFallsBackWhenItIsDisabled() {
        BlockingPolicy policy = BlockingPolicy.create();
        BlockingRule parent = rule("Video sites", new WebsiteTarget("youtube.com"), BlockingAction.BLOCK);
        BlockingRule child = rule("Music", new WebsiteTarget("music.youtube.com"), BlockingAction.LOG);
        policy.addRule(parent);
        policy.addRule(child);
        ObservedDistraction observed = ObservedDistraction.website("player.music.youtube.com");

        assertEquals(child.id(), policy.evaluate(observed).orElseThrow().ruleId());
        policy.setRuleEnabled(child.id(), false);
        assertEquals(parent.id(), policy.evaluate(observed).orElseThrow().ruleId());
        policy.setRuleEnabled(parent.id(), false);
        assertTrue(policy.evaluate(observed).isEmpty());
    }

    @Test
    void websiteSpecificityIsIndependentOfInsertionOrderAndIncludesOnlyDescendantDomains() {
        BlockingRule parent = rule("Video", new WebsiteTarget("youtube.com"), BlockingAction.BLOCK);
        BlockingRule child = rule("Music", new WebsiteTarget("music.youtube.com"), BlockingAction.WARN);
        for (List<BlockingRule> order : List.of(List.of(child, parent), List.of(parent, child))) {
            BlockingPolicy policy = BlockingPolicy.create();
            order.forEach(policy::addRule);
            assertEquals(child.id(), policy.evaluate(ObservedDistraction.website("music.youtube.com"))
                    .orElseThrow().ruleId());
            assertEquals(parent.id(), policy.evaluate(ObservedDistraction.website("www.youtube.com"))
                    .orElseThrow().ruleId());
            assertTrue(policy.evaluate(ObservedDistraction.website("notyoutube.com")).isEmpty());
        }
    }

    @Test
    void rejectsCanonicalDuplicateWebsitesRegardlessOfDisplayNameActionOrEnabledState() {
        BlockingPolicy policy = BlockingPolicy.create();
        BlockingRule original = rule("Video", new WebsiteTarget("YouTube.com."), BlockingAction.BLOCK);
        policy.addRule(original);
        BlockingRule duplicate = BlockingRule.create("Different label", new WebsiteTarget("youtube.com"),
                DistractionCategory.OTHER, BlockingAction.LOG, false);

        assertThrows(IllegalArgumentException.class, () -> policy.addRule(duplicate));
        assertEquals(List.of(original), policy.rules());
        assertEquals(BlockingAction.BLOCK, policy.evaluate(ObservedDistraction.website("youtube.com"))
                .orElseThrow().action());
    }

    @Test
    void duplicateApplicationKeysIncludePlatformMatchKindAndCanonicalIdentifier() {
        BlockingPolicy policy = BlockingPolicy.create();
        policy.addRule(rule("Windows chat", new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.PROCESS_NAME, "CHAT.EXE"), BlockingAction.BLOCK));
        assertThrows(IllegalArgumentException.class, () -> policy.addRule(rule("Duplicate",
                new ApplicationTarget(TargetPlatform.WINDOWS, ApplicationMatchKind.PROCESS_NAME, "chat.exe"),
                BlockingAction.WARN)));

        policy.addRule(rule("Linux chat", new ApplicationTarget(TargetPlatform.LINUX,
                ApplicationMatchKind.PROCESS_NAME, "CHAT.EXE"), BlockingAction.LOG));
        policy.addRule(rule("Windows path", new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, "C:\\Apps\\chat.exe"), BlockingAction.WARN));
        assertThrows(IllegalArgumentException.class, () -> policy.addRule(rule("Same path",
                new ApplicationTarget(TargetPlatform.WINDOWS, ApplicationMatchKind.EXECUTABLE_PATH,
                        "c:/apps/other/../CHAT.exe"), BlockingAction.LOG)));
        assertEquals(3, policy.rules().size());
    }

    @Test
    void rejectsAnEditToADuplicateTargetWithoutChangingTheSavedRule() {
        BlockingPolicy policy = BlockingPolicy.create();
        BlockingRule video = rule("Video", new WebsiteTarget("youtube.com"), BlockingAction.BLOCK);
        BlockingRule chat = rule("Chat", new WebsiteTarget("chat.example"), BlockingAction.WARN);
        policy.addRule(video);
        policy.addRule(chat);

        assertThrows(IllegalArgumentException.class, () -> policy.updateRule(chat.id(), "Changed",
                new WebsiteTarget("YOUTUBE.COM."), DistractionCategory.OTHER, BlockingAction.LOG, false));
        BlockingRule unchanged = policy.rules().stream().filter(rule -> rule.id().equals(chat.id()))
                .findFirst().orElseThrow();
        assertEquals("Chat", unchanged.displayName());
        assertEquals(new WebsiteTarget("chat.example"), unchanged.target());
        assertTrue(unchanged.enabled());
        assertEquals(BlockingAction.WARN, unchanged.action());
    }

    @Test
    void editsAndRemovesRulesWithoutRequiringThePolicyToRetainOne() {
        BlockingPolicy policy = BlockingPolicy.create();
        BlockingRule rule = rule("Video", new WebsiteTarget("youtube.com"), BlockingAction.BLOCK);
        policy.addRule(rule);
        policy.updateRule(rule.id(), "  Music only  ", new WebsiteTarget("music.youtube.com"),
                DistractionCategory.OTHER, BlockingAction.WARN, true);
        assertEquals("Music only", policy.rules().get(0).displayName());
        assertTrue(policy.evaluate(ObservedDistraction.website("youtube.com")).isEmpty());
        assertEquals(rule.id(), policy.evaluate(ObservedDistraction.website("music.youtube.com"))
                .orElseThrow().ruleId());
        policy.removeRule(rule.id());
        assertTrue(policy.rules().isEmpty());
        assertTrue(policy.evaluate(ObservedDistraction.website("music.youtube.com")).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(BlockingAction.class)
    void returnsTheConfiguredActionAndTargetWithoutMutatingRules(BlockingAction action) {
        BlockingPolicy policy = BlockingPolicy.create();
        BlockingRule rule = rule("Video", new WebsiteTarget("youtube.com"), action);
        policy.addRule(rule);
        List<BlockingRule> before = List.copyOf(policy.rules());
        ObservedDistraction observed = ObservedDistraction.website("www.youtube.com");

        BlockingDecision first = policy.evaluate(observed).orElseThrow();
        BlockingDecision second = policy.evaluate(observed).orElseThrow();
        assertEquals(first, second);
        assertEquals(rule.id(), first.ruleId());
        assertEquals(rule.target(), first.target());
        assertEquals(DistractionCategory.OTHER, first.category());
        assertEquals(action, first.action());
        assertEquals(before, policy.rules());
        assertTrue(policy.rules().get(0).enabled());
    }

    @Test
    void appAndWebsiteRulesDoNotMatchObservationsOfTheOtherType() {
        BlockingPolicy appsOnly = BlockingPolicy.create();
        appsOnly.addRule(rule("Browser", new ApplicationTarget(TargetPlatform.LINUX,
                ApplicationMatchKind.PROCESS_NAME, "browser"), BlockingAction.BLOCK));
        assertTrue(appsOnly.evaluate(ObservedDistraction.website("youtube.com")).isEmpty());

        BlockingPolicy websitesOnly = BlockingPolicy.create();
        websitesOnly.addRule(rule("Video", new WebsiteTarget("youtube.com"), BlockingAction.LOG));
        assertTrue(websitesOnly.evaluate(ObservedDistraction.application(
                new AppIdentifier(TargetPlatform.LINUX, "/opt/browser", "browser"))).isEmpty());
        assertFalse(appsOnly.evaluate(ObservedDistraction.application(
                new AppIdentifier(TargetPlatform.LINUX, "/opt/browser", "browser"))).isEmpty());
    }

    @Test
    void invalidNamesAndExternalCollectionEditsCannotCorruptThePolicy() {
        BlockingPolicy policy = BlockingPolicy.create();
        assertThrows(IllegalArgumentException.class, () -> rule(" ", new WebsiteTarget("youtube.com"),
                BlockingAction.BLOCK));
        BlockingRule rule = rule("Video", new WebsiteTarget("youtube.com"), BlockingAction.BLOCK);
        policy.addRule(rule);
        assertThrows(UnsupportedOperationException.class, () -> policy.rules().clear());
        assertEquals(1, policy.rules().size());
    }

    private static BlockingRule rule(String name, DistractionTarget target, BlockingAction action) {
        return BlockingRule.create(name, target, DistractionCategory.OTHER, action, true);
    }
}
