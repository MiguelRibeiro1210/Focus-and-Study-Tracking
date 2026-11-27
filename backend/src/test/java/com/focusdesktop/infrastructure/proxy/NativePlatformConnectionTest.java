package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.focusdesktop.application.port.WebsiteProtectionPreferences;
import com.focusdesktop.application.usecase.EvaluateWebsiteRequestUseCase;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

/** Ephemeral CI machines only: real user registry / administrator network settings. */
@EnabledOnOs({OS.WINDOWS, OS.MAC})
@EnabledIfEnvironmentVariable(named = "FOCUSDESKTOP_NATIVE_CONNECTION_TEST", matches = "1")
class NativePlatformConnectionTest {
    @TempDir Path directory;
    @Test void normalBrowserLaunchAndRelaunchUseAutomaticRoutingAndRestoreAfterward() throws Exception {
        String executable = System.getenv("FOCUSDESKTOP_NATIVE_BROWSER");
        assertNotNull(executable, "CI must supply a native Chromium browser"); assertTrue(Files.isExecutable(Path.of(executable)));
        var preferences = new WebsiteProtectionPreferences() {
            State value = new State(false, null);
            public State get() { return value; }
            public State setEnabled(boolean enabled, String consent) { return setEnabled(enabled, consent, value.routingMethod()); }
            public State setEnabled(boolean enabled, String consent, RoutingMethod method) { return value = new State(enabled, enabled ? Instant.now() : null, method); }
        };
        var count = new AtomicInteger(); var gate = mock(EvaluateWebsiteRequestUseCase.class);
        when(gate.execute(anyString())).thenReturn(new EvaluateWebsiteRequestUseCase.Decision(true, count::incrementAndGet, () -> {}));
        var browser = mock(BraveBrowserLauncher.class); when(browser.systemProxySupported()).thenReturn(true);
        Path profile = Files.createDirectory(directory.resolve("browser-profile"));
        try (var runtime = new WebsiteProtectionRuntime(preferences, gate, directory.toString(), true, browser) {
            @Override List<String> administratorLauncher(List<String> arguments) {
                // CI runners authorize sudo without a GUI; production uses the OS prompt.
                var command = new ArrayList<>(List.of("/usr/bin/sudo", "-n"));
                command.addAll(ProxyGuardianCommand.javaCommand(PrivilegedProxyGuardian.class, arguments)); return command;
            }
        }) {
            var ready = runtime.configure(true, "CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC");
            assertEquals("READY", ready.state(), ready.reason()); assertFalse(ready.routingVerified());
            browse(executable, profile, ready.verificationUrl(), "verify", runtime);
            assertTrue(runtime.status().routingVerified(), "Normal native browser launch must use automatic settings");
            int before = count.get();
            browse(executable, profile, "http://blocked.example/normal-relaunch", "blocked", runtime);
            assertTrue(count.get() > before, "The same profile must block after normal process exit and relaunch");
            runtime.reconcile(); assertEquals("DISABLED", runtime.configure(false, null).state());
        }
    }
    private void browse(String executable, Path profile, String url, String label, WebsiteProtectionRuntime runtime) throws Exception {
        var process = new ProcessBuilder(executable, "--headless=new", "--no-first-run", "--no-default-browser-check",
                "--user-data-dir=" + profile, "--disable-background-networking", "--disable-component-update", "--disable-sync",
                "--disable-extensions", "--disable-quic", "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1, EXCLUDE localhost",
                "--screenshot=" + directory.resolve(label + ".png"), url)
                .redirectErrorStream(true).redirectOutput(directory.resolve(label + ".log").toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!process.waitFor(500, TimeUnit.MILLISECONDS) && System.nanoTime() < deadline) runtime.reconcile();
            assertFalse(process.isAlive(), "Native browser did not finish its isolated headless launch");
            assertEquals(0, process.exitValue());
        } finally {
            if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
