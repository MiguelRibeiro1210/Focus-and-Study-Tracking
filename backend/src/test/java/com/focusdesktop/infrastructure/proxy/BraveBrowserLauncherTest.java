package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BraveBrowserLauncherTest {
    @Test void buildsOnlyFixedArgumentsForTheExistingProfileAndRejectsExternalConfiguration() {
        String token = "00000000-0000-0000-0000-000000000001";
        String pac = "http://127.0.0.1:12345/pac/" + token;
        String verify = "http://focusdesktop.invalid/verify/" + token;
        assertEquals(List.of("/usr/bin/brave", "--proxy-pac-url=" + pac, "--new-window", verify),
                BraveBrowserLauncher.command(Path.of("/usr/bin/brave"), pac, verify));
        assertThrows(IllegalArgumentException.class, () -> BraveBrowserLauncher.command(Path.of("/usr/bin/brave"), "https://untrusted.example/pac/" + token, verify));
        assertThrows(IllegalArgumentException.class, () -> BraveBrowserLauncher.command(Path.of("/usr/bin/brave"), pac, "http://untrusted.example/"));
    }
}
