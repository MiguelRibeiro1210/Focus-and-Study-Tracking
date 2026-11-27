package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.focusdesktop.application.port.WebsiteProtectionPreferences;
import com.focusdesktop.application.usecase.EvaluateWebsiteRequestUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
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
        var catalog = new com.focusdesktop.infrastructure.desktop.NativeApplicationCatalog(System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv(), args -> {
            try { return com.focusdesktop.infrastructure.desktop.NativeCommands.execute(args, true, 12); }
            catch (RuntimeException exception) { throw new AssertionError("Native application catalog helper failed", exception); }
        }, new ObjectMapper()).scan();
        assertTrue(catalog.applications().stream().anyMatch(app -> app.selectable() && app.target() != null
                        && ("macos:com.google.Chrome".equals(app.target().value()) || app.target().value().equalsIgnoreCase(executable))),
                "Native catalog must discover the installed CI browser; warnings=" + catalog.warnings());
        if (System.getProperty("os.name").startsWith("Windows")) {
            String identity = com.focusdesktop.infrastructure.desktop.NativeCommands.execute(com.focusdesktop.infrastructure.desktop.NativePowerShell.command(
                    "$FocusProcessId=" + ProcessHandle.current().pid() + "; " + com.focusdesktop.infrastructure.desktop.NativePowerShell.resource("foreground-application.ps1")), true);
            assertTrue(identity.isEmpty(), "The test JVM is not a packaged Windows application");
        }
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
        Path helperLog = directory.resolve("connection-helper.log");
        try (var runtime = new WebsiteProtectionRuntime(preferences, gate, directory.toString(), true, browser) {
            @Override ProcessBuilder guardianProcess(List<String> arguments) {
                return super.guardianProcess(arguments).redirectError(ProcessBuilder.Redirect.appendTo(helperLog.toFile()));
            }
            @Override List<String> administratorLauncher(List<String> arguments) {
                // CI runners authorize sudo without a GUI; production uses the OS prompt.
                var command = new ArrayList<>(List.of("/usr/bin/sudo", "-n"));
                command.addAll(ProxyGuardianCommand.javaCommand(PrivilegedProxyGuardian.class, arguments)); return command;
            }
        }) {
            var ready = runtime.configure(true, "CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC");
            assertEquals("READY", ready.state(), () -> ready.reason() + "\n" + diagnostic(helperLog)); assertFalse(ready.routingVerified());
            browse(executable, profile, ready.verificationUrl(), "verify", "routing verified", runtime);
            assertTrue(runtime.status().routingVerified(), "Normal native browser launch must use automatic settings");
            int before = count.get();
            browse(executable, profile, "http://blocked.example/normal-relaunch", "blocked", "Website blocked", runtime);
            assertTrue(count.get() > before, "The same profile must block after normal process exit and relaunch");
            runtime.reconcile();
            var disabled = runtime.configure(false, null);
            assertEquals("DISABLED", disabled.state(), () -> disabled.reason() + "\n" + diagnostic(helperLog));
        }
    }
    private static String diagnostic(Path log) {
        try { return Files.readString(log); }
        catch (java.io.IOException exception) { return "Helper diagnostics unavailable: " + exception.getMessage(); }
    }
    private void browse(String executable, Path profile, String url, String label, String expectedText, WebsiteProtectionRuntime runtime) throws Exception {
        Path portFile = profile.resolve("DevToolsActivePort"); Files.deleteIfExists(portFile);
        Path log = directory.resolve(label + ".log");
        var process = new ProcessBuilder(executable, "--headless=new", "--no-first-run", "--no-default-browser-check",
                "--user-data-dir=" + profile, "--disable-background-networking", "--disable-component-update", "--disable-sync",
                "--disable-extensions", "--disable-quic", "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1, EXCLUDE localhost",
                "--remote-debugging-port=0", "about:blank")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        DevTools tools = null;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!Files.exists(portFile) && process.isAlive() && System.nanoTime() < deadline) { runtime.reconcile(); Thread.sleep(100); }
            assertTrue(Files.exists(portFile), () -> "Native browser did not open DevTools\n" + diagnostic(log));
            int port = Integer.parseInt(Files.readAllLines(portFile).get(0));
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var pages = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).build(), HttpResponse.BodyHandlers.ofString());
            String endpoint = null;
            for (var page : new ObjectMapper().readTree(pages.body()))
                if (page.path("type").asText().equals("page")) { endpoint = page.path("webSocketDebuggerUrl").asText(); break; }
            assertNotNull(endpoint, "Native browser must expose its isolated page target");
            tools = new DevTools();
            tools.socket = client.newWebSocketBuilder().buildAsync(URI.create(endpoint), tools).get(5, TimeUnit.SECONDS);
            tools.command("Page.navigate", Map.of("url", url));
            String text = "";
            while (System.nanoTime() < deadline) {
                runtime.reconcile();
                text = tools.command("Runtime.evaluate", Map.of("expression", "document.body ? document.body.innerText : ''", "returnByValue", true))
                        .path("result").path("result").path("value").asText();
                if (text.contains(expectedText)) break;
                Thread.sleep(100);
            }
            assertTrue(text.contains(expectedText), "Native browser did not display " + expectedText + "; actual=" + text + "\n" + diagnostic(log));
            tools.socket.sendText(tools.mapper.writeValueAsString(Map.of("id", ++tools.nextId, "method", "Browser.close")), true).get(3, TimeUnit.SECONDS);
            long closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!process.waitFor(250, TimeUnit.MILLISECONDS) && System.nanoTime() < closeDeadline) runtime.reconcile();
            assertFalse(process.isAlive(), () -> "Native browser did not close normally\n" + diagnostic(log));
            assertEquals(0, process.exitValue());
        } finally {
            if (tools != null && tools.socket != null) tools.socket.abort();
            if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    private static final class DevTools implements WebSocket.Listener {
        private final ObjectMapper mapper = new ObjectMapper();
        private final Map<Integer, CompletableFuture<JsonNode>> responses = new ConcurrentHashMap<>();
        private final StringBuilder fragment = new StringBuilder();
        private WebSocket socket;
        private int nextId;
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            fragment.append(data);
            if (last) {
                try {
                    var value = mapper.readTree(fragment.toString());
                    var response = responses.remove(value.path("id").asInt());
                    if (response != null) response.complete(value);
                } catch (java.io.IOException exception) { responses.values().forEach(response -> response.completeExceptionally(exception)); }
                finally { fragment.setLength(0); }
            }
            socket.request(1); return null;
        }
        JsonNode command(String method, Map<String, Object> parameters) throws Exception {
            int id = ++nextId; var response = new CompletableFuture<JsonNode>(); responses.put(id, response);
            socket.sendText(mapper.writeValueAsString(Map.of("id", id, "method", method, "params", parameters)), true).get(3, TimeUnit.SECONDS);
            var result = response.get(5, TimeUnit.SECONDS);
            assertFalse(result.has("error"), result.toString()); return result;
        }
    }
}
