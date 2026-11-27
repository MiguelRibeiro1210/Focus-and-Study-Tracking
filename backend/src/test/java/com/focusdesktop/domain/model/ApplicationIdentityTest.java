package com.focusdesktop.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationIdentityTest {
    @Test void packageMatchesAreExactAndNeverMatchTheirSharedLauncher() throws Exception {
        for (var identity : List.of("flatpak:org.example.Chat", "snap:chat.chat")) {
            var rule = new ApplicationTarget(TargetPlatform.LINUX, ApplicationMatchKind.APPLICATION_ID, identity);
            assertTrue(rule.matches(new AppIdentifier(TargetPlatform.LINUX, "/app/bin/chat", "chat", identity)));
            assertFalse(rule.matches(new AppIdentifier(TargetPlatform.LINUX, "/usr/bin/flatpak", "flatpak")));
            assertFalse(rule.matches(new AppIdentifier(TargetPlatform.LINUX, "/app/bin/chat", "chat", "flatpak:org.other.Chat")));
        }
        var mapper = new ObjectMapper();
        var legacy = mapper.readValue("{\"platform\":\"LINUX\",\"executablePath\":\"/usr/bin/chat\",\"processName\":\"chat\"}", AppIdentifier.class);
        assertNull(legacy.applicationId());
        assertEquals(legacy, new AppIdentifier(TargetPlatform.LINUX, "/usr/bin/chat", "chat"));
        var modern = new AppIdentifier(TargetPlatform.MACOS, "/Applications/Chat.app/Contents/MacOS/Chat", "Chat", "macos:com.example.Chat");
        assertEquals(modern, mapper.readValue(mapper.writeValueAsString(modern), AppIdentifier.class));
    }
    @Test void preservesIdentityCaseAndRejectsWrongNamespacesAndCommands() {
        var target = new ApplicationTarget(TargetPlatform.WINDOWS, ApplicationMatchKind.APPLICATION_ID, "windows:Chat_123!Main");
        assertEquals("windows:Chat_123!Main", target.canonicalValue());
        assertTrue(target.matches(new AppIdentifier(TargetPlatform.WINDOWS, "C:\\Apps\\chat.exe", "chat.exe", "windows:Chat_123!Main")));
        for (String value : List.of("flatpak:org.example.Chat", "windows:Chat_123!Main;run", "windows:../Chat", "windows:Chat*", "windows:Chat_123!Main extra"))
            assertThrows(IllegalArgumentException.class, () -> new ApplicationTarget(TargetPlatform.WINDOWS, ApplicationMatchKind.APPLICATION_ID, value));
        var path = new ApplicationTarget(TargetPlatform.WINDOWS, ApplicationMatchKind.EXECUTABLE_PATH, "C:\\Apps\\chat.exe");
        var process = new ApplicationTarget(TargetPlatform.WINDOWS, ApplicationMatchKind.PROCESS_NAME, "chat.exe");
        assertTrue(path.specificity() > target.specificity()); assertTrue(target.specificity() > process.specificity());
    }
}
