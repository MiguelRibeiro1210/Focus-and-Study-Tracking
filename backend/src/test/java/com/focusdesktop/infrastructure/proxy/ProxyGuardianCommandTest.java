package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyGuardianCommandTest {
    @TempDir Path directory;
    @Test void permissionPromptsUseFixedProgramsAndQuoteMacArgumentsAsLiteralShellWords() {
        var java = List.of("/Application Space/java", "-cp", "quote' dollar$ `backtick` \"path\".jar", "Main");
        assertEquals(List.of("/usr/bin/pkexec", java.get(0), "-cp", java.get(2), "Main"), ProxyGuardianCommand.elevated("Linux", java));
        var mac = ProxyGuardianCommand.elevated("Mac OS X", java);
        assertEquals(List.of("/usr/bin/osascript", "-e"), mac.subList(0, 2));
        assertTrue(mac.get(2).contains("do shell script \"'/Application Space/java'"));
        assertTrue(mac.get(2).endsWith("with administrator privileges\nend timeout"));
        assertThrows(IllegalArgumentException.class, () -> ProxyGuardianCommand.elevated("Windows", java));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs({org.junit.jupiter.api.condition.OS.LINUX, org.junit.jupiter.api.condition.OS.MAC})
    void credentialIsPrivateBoundToParentIdentityAndNeverPassedAsCommandText() throws Exception {
        Path file = directory.resolve("credential"); String secret = UUID.randomUUID().toString(); Files.writeString(file, secret);
        Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var parent = ProcessHandle.current(); long start = parent.info().startInstant().orElseThrow().toEpochMilli();
        assertEquals(secret, GuardianCredential.read(file, parent.pid(), start));
        assertThrows(java.io.IOException.class, () -> GuardianCredential.read(file, parent.pid(), start + 1));
        Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(java.io.IOException.class, () -> GuardianCredential.read(file, parent.pid(), start));
    }
}
