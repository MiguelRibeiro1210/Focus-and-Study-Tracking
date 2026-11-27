package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.focusdesktop.application.port.WebsiteProtectionPreferences;
import com.focusdesktop.application.usecase.EvaluateWebsiteRequestUseCase;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class AdministratorProxyGuardianTest {
    @TempDir Path directory;
    private static final String CONSENT = "CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC";
    private final java.util.concurrent.atomic.AtomicInteger refreshes = new java.util.concurrent.atomic.AtomicInteger();
    @Test void socketLeaseAuthenticatesAppliesAndRestoresWithoutAnyOsSettingsChanges() throws Exception {
        Files.writeString(directory.resolve("value"), "original");
        try (var runtime = runtime(FakeGuardian.class, new java.util.concurrent.atomic.AtomicInteger())) {
            var ready = runtime.configure(true, CONSENT); assertEquals("READY", ready.state());
            assertEquals(1, refreshes.get());
            assertFalse(ready.routingVerified()); assertTrue(Files.readString(directory.resolve("value")).startsWith("http://127.0.0.1:"));
            runtime.reconcile();
            assertEquals("DISABLED", runtime.configure(false, null).state());
            assertEquals("original", Files.readString(directory.resolve("value")));
            assertEquals(2, refreshes.get());
        }
        try (var files = Files.list(directory)) { assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("connection-credential-"))); }
    }
    @Test void deniedAuthorizationDoesNotPromptOnEveryRuntimeTick() throws Exception {
        var launches = new java.util.concurrent.atomic.AtomicInteger();
        try (var runtime = runtime(DeniedGuardian.class, launches)) {
            assertEquals("UNAVAILABLE", runtime.configure(true, CONSENT).state());
            assertNull(runtime.status().verificationUrl());
            runtime.reconcile(); runtime.reconcile(); assertEquals(1, launches.get());
            runtime.configure(true, CONSENT); assertEquals(2, launches.get());
        }
    }
    @Test void missingRestorationProofRequiresAnExplicitRecoveryWithoutResumingForwarding() throws Exception {
        Files.writeString(directory.resolve("value"), "original");
        var launches = new java.util.concurrent.atomic.AtomicInteger();
        try (var runtime = runtime(UnconfirmedGuardian.class, launches)) {
            assertEquals("READY", runtime.configure(true, CONSENT).state());
            assertEquals("RESTORING", runtime.configure(false, null).state());
            assertFalse(runtime.status().enabled()); assertNull(runtime.status().verificationUrl());
            runtime.reconcile(); runtime.reconcile(); assertEquals(1, launches.get());
            assertEquals("DISABLED", runtime.configure(false, null).state());
            assertEquals(2, launches.get()); assertEquals("original", Files.readString(directory.resolve("value")));
        }
    }
    private WebsiteProtectionRuntime runtime(Class<?> helper, java.util.concurrent.atomic.AtomicInteger launches) {
        var browser = mock(BraveBrowserLauncher.class); when(browser.systemProxySupported()).thenReturn(true);
        var preferences = new WebsiteProtectionPreferences() {
            State value = new State(false, null);
            public State get() { return value; }
            public State setEnabled(boolean enabled, String consent) { return setEnabled(enabled, consent, value.routingMethod()); }
            public State setEnabled(boolean enabled, String consent, RoutingMethod method) { return value = new State(enabled, enabled ? Instant.now() : null, method); }
        };
        return new WebsiteProtectionRuntime(preferences, mock(EvaluateWebsiteRequestUseCase.class), directory.toString(), true, browser, refreshes::incrementAndGet) {
            @Override boolean useAdministratorGuardian() { return true; }
            @Override List<String> administratorLauncher(List<String> arguments) {
                launches.incrementAndGet();
                var args = new ArrayList<>(arguments); args.add(directory.toString());
                var command = ProxyGuardianCommand.javaCommand(helper, args);
                try { assertFalse(command.contains(Files.readString(Path.of(arguments.get(1))))); }
                catch (IOException exception) { throw new UncheckedIOException(exception); }
                return command;
            }
        };
    }
    public static class DeniedGuardian { public static void main(String[] args) { System.exit(1); } }
    public static class UnconfirmedGuardian {
        public static void main(String[] args) throws Exception {
            Path value = Path.of(args[5]).resolve("value");
            if (args[4].equals("RECOVER")) { Files.writeString(value, "original"); FakeGuardian.main(args); return; }
            String secret = GuardianCredential.read(Path.of(args[1]), Long.parseLong(args[2]), Long.parseLong(args[3]));
            try (var socket = new Socket("127.0.0.1", Integer.parseInt(args[0]))) {
                var response = new PrintStream(socket.getOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8);
                response.println(secret); socket.getInputStream().readNBytes(5);
                Files.writeString(value, args[4]); response.println("READY");
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String command;
                while ((command = reader.readLine()) != null && !command.equals("QUIT")) response.println("OK");
                // Simulate losing the helper before it restores or acknowledges.
            }
        }
    }
    public static class FakeGuardian {
        public static void main(String[] args) throws Exception {
            String secret = GuardianCredential.read(Path.of(args[1]), Long.parseLong(args[2]), Long.parseLong(args[3]));
            Path directory = Path.of(args[5]), value = directory.resolve("value");
            try (var socket = new Socket("127.0.0.1", Integer.parseInt(args[0]))) {
                var output = new PrintStream(socket.getOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8);
                output.println(secret);
                assertEquals("AUTH\n", new String(socket.getInputStream().readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII));
                ProxySettingsGuardian.run(new OwnedProxySettings.Store() {
                    public List<OwnedProxySettings.Change> prepare(String pac) { return List.of(new OwnedProxySettings.Change("url", read("url"), pac)); }
                    public String read(String key) { try { return Files.readString(value); } catch (IOException exception) { throw new UncheckedIOException(exception); } }
                    public void write(String key, String next) { try { Files.writeString(value, next); } catch (IOException exception) { throw new UncheckedIOException(exception); } }
                }, new String[]{directory.resolve("fake-admin-journal").toString(), args[2], args[3], args[4]}, 3000,
                        socket.getInputStream(), output);
            }
        }
    }
}
