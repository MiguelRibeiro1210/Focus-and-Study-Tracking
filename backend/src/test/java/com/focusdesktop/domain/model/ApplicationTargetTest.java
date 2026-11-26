package com.focusdesktop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationTargetTest {
    @Test
    void comparesWindowsPathsLexicallyWithoutDependingOnTheHostOsOrInstalledFiles() {
        ApplicationTarget target = new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, "C:\\Apps\\Games\\..\\Chat\\Chat.EXE");
        AppIdentifier observed = new AppIdentifier(TargetPlatform.WINDOWS,
                "c:/apps/chat/chat.exe", "chat.exe");

        assertTrue(target.matches(observed));
        assertEquals(target, new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, "c:/apps/chat/chat.exe"));
        assertEquals("C:\\Apps\\Games\\..\\Chat\\Chat.EXE", target.value());
        assertFalse(target.matches(new AppIdentifier(TargetPlatform.WINDOWS,
                "C:\\Other\\Chat.exe", "chat.exe")));
    }

    @Test
    void acceptsAbsoluteWindowsNetworkPaths() {
        ApplicationTarget target = new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, "\\\\server\\share\\Chat.exe");
        assertTrue(target.matches(new AppIdentifier(TargetPlatform.WINDOWS,
                "\\\\SERVER\\share\\chat.EXE", "chat.exe")));
    }

    @ParameterizedTest
    @EnumSource(value = TargetPlatform.class, names = {"LINUX", "MACOS"})
    void comparesPosixPathsLexicallyAndWithCase(TargetPlatform platform) {
        ApplicationTarget target = new ApplicationTarget(platform,
                ApplicationMatchKind.EXECUTABLE_PATH, "/opt/apps/../Chat/chat");

        assertTrue(target.matches(new AppIdentifier(platform, "/opt/Chat/chat", "chat")));
        assertFalse(target.matches(new AppIdentifier(platform, "/opt/chat/chat", "chat")));
        assertFalse(target.matches(new AppIdentifier(platform, "/opt/Chat/chat-helper", "chat")));
    }

    @Test
    void processNamesMatchExactlyAndUseTheDeclaredPlatformCaseRules() {
        ApplicationTarget windows = new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.PROCESS_NAME, "CHAT.EXE");
        assertTrue(windows.matches(new AppIdentifier(TargetPlatform.WINDOWS, null, "chat.exe")));
        assertFalse(windows.matches(new AppIdentifier(TargetPlatform.WINDOWS, null, "chat-helper.exe")));
        assertFalse(windows.matches(new AppIdentifier(TargetPlatform.LINUX, null, "CHAT.EXE")));

        for (TargetPlatform platform : new TargetPlatform[] {TargetPlatform.LINUX, TargetPlatform.MACOS}) {
            ApplicationTarget target = new ApplicationTarget(platform, ApplicationMatchKind.PROCESS_NAME, "Chat");
            assertTrue(target.matches(new AppIdentifier(platform, null, "Chat")));
            assertFalse(target.matches(new AppIdentifier(platform, null, "chat")));
        }
    }

    @Test
    void aMissingPathDoesNotFallBackToTheObservedProcessName() {
        ApplicationTarget target = new ApplicationTarget(TargetPlatform.LINUX,
                ApplicationMatchKind.EXECUTABLE_PATH, "/opt/chat");
        assertFalse(target.matches(new AppIdentifier(TargetPlatform.LINUX, null, "chat")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "chat.exe", "Apps/chat.exe", "C:chat.exe"})
    void requiresAbsoluteExecutablePathsForTheDeclaredPlatform(String value) {
        assertThrows(IllegalArgumentException.class, () -> new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, value));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationTarget(TargetPlatform.LINUX,
                ApplicationMatchKind.EXECUTABLE_PATH, value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "C:\\Apps\\chat.exe", "/opt/chat", "apps/chat", "*.exe", "chat?"})
    void rejectsPathsAndPatternsWhenAProcessNameIsRequired(String value) {
        assertThrows(IllegalArgumentException.class, () -> new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.PROCESS_NAME, value));
    }

    @Test
    void lexicalNormalizationCannotHideAnInvalidWindowsPathComponent() {
        assertThrows(IllegalArgumentException.class, () -> new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, "C:\\bad:name\\..\\Chat.exe"));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationTarget(TargetPlatform.WINDOWS,
                ApplicationMatchKind.EXECUTABLE_PATH, "\\\\server\\share\\..\\other\\Chat.exe"));
    }
}
