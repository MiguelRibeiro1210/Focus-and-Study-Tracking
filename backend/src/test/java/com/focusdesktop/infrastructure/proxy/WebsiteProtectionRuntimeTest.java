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
            var ready = runtime.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC");
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
            preferences.setEnabled(false, null); // External reset before the next runtime tick.
            assertFalse(runtime.status().routingVerified());
            assertEquals("DISABLED", runtime.configure(false, null).state());
            assertEquals("original", Files.readString(value));
        }
    }
    @Test void aReplacementProxyDisablesProtectionWithoutOverwritingTheUsersSettings() throws Exception {
        var preferences = new MemoryPreferences();
        Path value = directory.resolve("value"); Files.writeString(value, "original");
        try (var runtime = fixture(preferences, mock(EvaluateWebsiteRequestUseCase.class), value)) {
            assertEquals("READY", runtime.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC").state());
            Files.writeString(value, "user-replacement");
            runtime.reconcile();
            assertFalse(runtime.status().enabled());
            assertEquals("user-replacement", Files.readString(value));
            assertFalse(Files.exists(directory.resolve("website-proxy-settings.json")));
        }
    }

    @Test void braveWaitsForVoluntaryExitAndCancelsOnDisableWithoutChangingNativeSettings() throws Exception {
        var preferences = new MemoryPreferences();
        var browser = mock(BraveBrowserLauncher.class);
        when(browser.available()).thenReturn(true); when(browser.running()).thenReturn(true);
        try (var runtime = new WebsiteProtectionRuntime(preferences, mock(EvaluateWebsiteRequestUseCase.class), directory.toString(), true, browser)) {
            var ready = runtime.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC", WebsiteProtectionPreferences.RoutingMethod.BRAVE);
            assertEquals("READY", ready.state()); assertFalse(ready.routingVerified());
            assertTrue(runtime.requestBraveSetup().braveLaunchPending());
            runtime.reconcile(); verify(browser, never()).launch(anyString(), anyString());
            when(browser.running()).thenReturn(false); runtime.reconcile();
            verify(browser).launch(contains("/pac/"), contains("/verify/"));
            assertFalse(runtime.status().braveLaunchPending()); assertFalse(runtime.status().routingVerified());
            runtime.requestBraveSetup(); runtime.configure(false, null); runtime.reconcile();
            assertFalse(runtime.status().braveLaunchPending());
            assertFalse(Files.exists(directory.resolve("website-proxy-settings.json")));
            verify(browser, times(1)).launch(anyString(), anyString());
        }
    }
    @Test void braveSetupExpiresAndLaunchFailuresRemainUnverified() throws Exception {
        var preferences = new MemoryPreferences(); var browser = mock(BraveBrowserLauncher.class);
        when(browser.available()).thenReturn(true); when(browser.running()).thenReturn(true);
        var time = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-26T00:00:00Z"));
        try (var runtime = new WebsiteProtectionRuntime(preferences, mock(EvaluateWebsiteRequestUseCase.class), directory.toString(), true, browser) {
            @Override Instant now() { return time.get(); }
        }) {
            runtime.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC", WebsiteProtectionPreferences.RoutingMethod.BRAVE);
            runtime.requestBraveSetup(); time.set(time.get().plusSeconds(60)); runtime.reconcile();
            assertFalse(runtime.status().braveLaunchPending()); assertTrue(runtime.status().reason().contains("timed out"));
            verify(browser, never()).launch(anyString(), anyString());
            when(browser.running()).thenReturn(false); doThrow(new java.io.IOException()).when(browser).launch(anyString(), anyString());
            runtime.requestBraveSetup(); runtime.reconcile();
            assertTrue(runtime.status().reason().contains("could not be opened")); assertFalse(runtime.status().routingVerified());
            runtime.reconcile(); verify(browser, times(1)).launch(anyString(), anyString());
        }
    }
    @Test void passiveDevelopmentAndUnsupportedSystemRoutingExplainWhyRulesCannotProtect() {
        var preferences = new MemoryPreferences(); var browser = mock(BraveBrowserLauncher.class);
        try (var passive = new WebsiteProtectionRuntime(preferences, mock(EvaluateWebsiteRequestUseCase.class), directory.toString(), false, browser)) {
            var status = passive.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC");
            assertFalse(status.runtimeEnabled()); assertEquals("UNAVAILABLE", status.state());
            assertThrows(IllegalArgumentException.class, passive::requestBraveSetup);
        }
        try (var runtime = new WebsiteProtectionRuntime(preferences, mock(EvaluateWebsiteRequestUseCase.class), directory.toString(), true, browser)) {
            runtime.reconcile(); assertEquals("UNAVAILABLE", runtime.status().state());
            assertTrue(runtime.status().reason().contains("Hyprland"));
            runtime.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC", WebsiteProtectionPreferences.RoutingMethod.BRAVE);
            assertEquals("UNAVAILABLE", runtime.status().state()); assertThrows(IllegalArgumentException.class, runtime::requestBraveSetup);
        }
    }
    @Test void switchingFromSystemToBraveRestoresOwnedSettingsBeforeStartingNewRouting() throws Exception {
        var preferences = new MemoryPreferences(); var browser = mock(BraveBrowserLauncher.class);
        when(browser.available()).thenReturn(true); when(browser.systemProxySupported()).thenReturn(true);
        Path value = directory.resolve("value"); Files.writeString(value, "original");
        try (var runtime = fixture(preferences, mock(EvaluateWebsiteRequestUseCase.class), value, browser)) {
            runtime.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC"); assertNotEquals("original", Files.readString(value));
            assertThrows(IllegalArgumentException.class, () -> runtime.configure(true, "wrong", WebsiteProtectionPreferences.RoutingMethod.BRAVE));
            assertNotEquals("original", Files.readString(value));
            runtime.configure(true, "FORWARD_SYSTEM_PROXY_TRAFFIC", WebsiteProtectionPreferences.RoutingMethod.BRAVE);
            assertEquals("original", Files.readString(value)); assertEquals("READY", runtime.status().state());
        }
    }
    private WebsiteProtectionRuntime fixture(MemoryPreferences preferences, EvaluateWebsiteRequestUseCase gate, Path value) {
        var browser = mock(BraveBrowserLauncher.class);
        when(browser.systemProxySupported()).thenReturn(true);
        return fixture(preferences, gate, value, browser);
    }
    private WebsiteProtectionRuntime fixture(MemoryPreferences preferences, EvaluateWebsiteRequestUseCase gate, Path value, BraveBrowserLauncher browser) {
        return new WebsiteProtectionRuntime(preferences, gate, directory.toString(), true, browser) {
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
            return setEnabled(enabled, consent, state.routingMethod());
        }
        public State setEnabled(boolean enabled, String consent, RoutingMethod method) {
            if (enabled && !"FORWARD_SYSTEM_PROXY_TRAFFIC".equals(consent)) throw new IllegalArgumentException();
            return state = new State(enabled, enabled ? Instant.now() : null, method);
        }
    }
}
