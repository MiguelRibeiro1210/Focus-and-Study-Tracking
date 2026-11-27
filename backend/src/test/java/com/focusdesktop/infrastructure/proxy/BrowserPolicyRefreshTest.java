package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrowserPolicyRefreshTest {
    @Test void refreshForwardsOnlyTheExistingAbsoluteProfileAndNoBrowsingOrProxyArguments() {
        Path binary = Path.of(System.getProperty("os.name").startsWith("Windows") ? "C:\\Browser\\brave.exe" : "/usr/bin/brave");
        Path profile = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("profile with spaces");
        assertEquals(List.of(binary.toString(), "--refresh-platform-policy", "--user-data-dir=" + profile),
                BrowserPolicyRefresh.command(binary, List.of("--proxy-server=external", "https://private.example/path", "--user-data-dir=" + profile)));
        assertEquals(List.of(binary.toString(), "--refresh-platform-policy"), BrowserPolicyRefresh.command(binary, List.of("https://example.com")));
        assertThrows(IllegalArgumentException.class, () -> BrowserPolicyRefresh.command(binary, List.of("--user-data-dir=relative")));
        assertThrows(IllegalArgumentException.class, () -> BrowserPolicyRefresh.command(binary.resolveSibling("arbitrary"), List.of()));
    }
}
