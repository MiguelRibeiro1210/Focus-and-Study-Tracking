package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.focusdesktop.application.port.WebsiteProtectionPreferences;
import com.focusdesktop.application.usecase.EvaluateWebsiteRequestUseCase;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebsiteProtectionRuntimeTest {
    @TempDir Path directory;
    @Test void configuresOwnedSettingsRequiresRealRoutingProofAndRestoresOnDisable() throws Exception {
        var preferences = new MemoryPreferences();
        var gate = mock(EvaluateWebsiteRequestUseCase.class);
        Path value = directory.resolve("value"); Files.writeString(value, "original");
        try (var runtime = fixture(preferences, gate, value)) {
            var ready = runtime.configure(true, "FORWARD_BROWSER_TRAFFIC");
            assertEquals("READY", ready.state()); assertFalse(ready.routingVerified());
            String pac = Files.readString(value);
            assertTrue(pac.startsWith("http://127.0.0.1:"));
            int port = java.net.URI.create(pac).getPort();
            var verify = java.net.URI.create(ready.verificationUrl());
            try (var browser = new Socket("127.0.0.1", port)) {
                browser.setSoTimeout(3000);
                browser.getOutputStream().write(("GET " + ready.verificationUrl() + " HTTP/1.1\r\nHost: "
                        + verify.getHost() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                assertTrue(new String(browser.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).contains("routing verified"));
            }
            assertEquals("VERIFIED", runtime.status().state()); assertTrue(runtime.status().routingVerified());
            verify(gate, never()).execute(anyString());
            assertEquals("DISABLED", runtime.configure(false, null).state());
            assertEquals("original", Files.readString(value));
        }
    }
    @Test void aReplacementProxyDisablesProtectionWithoutOverwritingTheUsersSettings() throws Exception {
        var preferences = new MemoryPreferences();
        Path value = directory.resolve("value"); Files.writeString(value, "original");
        try (var runtime = fixture(preferences, mock(EvaluateWebsiteRequestUseCase.class), value)) {
            assertEquals("READY", runtime.configure(true, "FORWARD_BROWSER_TRAFFIC").state());
            Files.writeString(value, "user-replacement");
            runtime.reconcile();
            assertFalse(runtime.status().enabled());
            assertEquals("user-replacement", Files.readString(value));
            assertFalse(Files.exists(directory.resolve("website-proxy-settings.json")));
        }
    }
    private WebsiteProtectionRuntime fixture(MemoryPreferences preferences, EvaluateWebsiteRequestUseCase gate, Path value) {
        return new WebsiteProtectionRuntime(preferences, gate, directory.toString(), true) {
            @Override List<String> launcher(String pac) {
                var parent = ProcessHandle.current();
                return List.of(parent.info().command().orElseThrow(), "-cp", System.getProperty("java.class.path"),
                        ProxySettingsGuardianTest.FakeGuardian.class.getName(), directory.resolve("website-proxy-settings.json").toString(),
                        Long.toString(parent.pid()), Long.toString(parent.info().startInstant().orElseThrow().toEpochMilli()), pac, value.toString(), "15000");
            }
        };
    }
    private static class MemoryPreferences implements WebsiteProtectionPreferences {
        private State state = new State(false, null);
        public State get() { return state; }
        public State setEnabled(boolean enabled, String consent) {
            if (enabled && !"FORWARD_BROWSER_TRAFFIC".equals(consent)) throw new IllegalArgumentException();
            return state = new State(enabled, enabled ? Instant.now() : null);
        }
    }
}
