package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.domain.model.WebsiteTarget;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Native Brave, isolated profile, locally resolved controlled origins; never uses the user's profile. */
@EnabledIfEnvironmentVariable(named = "FOCUSDESKTOP_BRAVE", matches = ".+")
class BraveProxyIntegrationTest {
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path directory;
    private final AtomicInteger originRequests = new AtomicInteger();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void braveBlocksYoutubeHttpAndHttpsWithoutDirectBypassAndAllowsPauseAndShutdown() throws Exception {
        var blocked = new AtomicBoolean(true); var denials = new AtomicInteger();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(tls()));
        http.createContext("/", this::origin); https.createContext("/", this::origin);
        http.start(); https.start();
        var target = new WebsiteTarget("youtube.com");
        try (var proxy = new WebsiteProxy(host -> new WebsiteProxy.Decision(blocked.get() && (target.matchesHost(host) || host.equals("blocked.test")),
                denials::incrementAndGet, () -> {}), (host, port) -> {
                    if (!host.equals("allowed.test") && !host.equals("blocked.test") && !target.matchesHost(host)) throw new IOException("Controlled origins only");
                    return new Socket("127.0.0.1", port == 443 ? https.getAddress().getPort() : port);
                }, true)) {
            proxy.start();
            try (var browser = browser(proxy.pacUrl())) {
                browser.navigate(proxy.verificationUrl()); browser.awaitText("routing verified"); assertTrue(proxy.routingObserved());
                String allowed = "http://allowed.test:" + http.getAddress().getPort();
                String youtube = "http://www.youtube.com:" + http.getAddress().getPort();
                String secure = "https://music.youtube.com:" + https.getAddress().getPort();
                browser.navigate(allowed + "/allowed"); browser.awaitText("CONTROLLED_ORIGIN/allowed");
                browser.navigate("https://allowed.test:" + https.getAddress().getPort() + "/tls"); browser.awaitText("CONTROLLED_ORIGIN/tls");
                int before = originRequests.get();
                browser.navigate("http://blocked.test:" + http.getAddress().getPort() + "/blocked"); browser.awaitText("Website blocked");
                assertEquals(before, originRequests.get(), "Intentional HTTP denial must not fall through DIRECT");
                int youtubeCount = denials.get(); browser.navigate(youtube + "/blocked");
                await(() -> denials.get() > youtubeCount); // Brave upgrades YouTube via its built-in HSTS list.
                int count = denials.get(); browser.navigate(secure + "/blocked");
                await(() -> denials.get() > count); Thread.sleep(500);
                assertEquals(before, originRequests.get(), "Intentional HTTPS CONNECT denial must not fall through DIRECT");
                blocked.set(false); proxy.reconcile();
                browser.navigate(secure + "/paused"); browser.awaitText("CONTROLLED_ORIGIN/paused");
                assertTrue(originRequests.get() > before, "Pause allows HTTPS without restarting Brave");
                blocked.set(true); proxy.reconcile();
                int resumed = denials.get(); browser.navigate(secure + "/focus-again"); await(() -> denials.get() > resumed);
                proxy.close();
                browser.navigate(secure + "/after-shutdown"); browser.awaitText("CONTROLLED_ORIGIN/after-shutdown");
            }
        } finally { http.stop(0); https.stop(0); }
    }

    @Test void abruptProxyProcessDeathDoesNotStrandTheExistingBrowser() throws Exception {
        var origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin.createContext("/", this::origin); origin.start();
        var child = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(), "-cp", System.getProperty("java.class.path"),
                CrashFixture.class.getName(), Integer.toString(origin.getAddress().getPort()), directory.resolve("proxy-used").toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            var line = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task); thread.setDaemon(true); return thread; });
            String pac;
            try { pac = line.submit(() -> new BufferedReader(new InputStreamReader(child.getInputStream())).readLine()).get(10, TimeUnit.SECONDS); }
            finally { line.shutdownNow(); }
            try (var browser = browser(pac)) {
                String url = "http://allowed.test:" + origin.getAddress().getPort();
                browser.navigate(url + "/before-crash"); browser.awaitText("CONTROLLED_ORIGIN/before-crash");
                assertTrue(Files.exists(directory.resolve("proxy-used")), "The pre-crash request must actually use the proxy");
                child.destroyForcibly(); assertTrue(child.waitFor(5, TimeUnit.SECONDS));
                browser.navigate(url + "/after-crash"); browser.awaitText("CONTROLLED_ORIGIN/after-crash");
            }
        } finally { child.destroyForcibly(); origin.stop(0); }
    }
    @Test void sameOpenBraveReconnectsToStableEndpointAndMustProveRoutingAgain() throws Exception {
        String token = UUID.randomUUID().toString();
        WebsiteProxy.Connector noNetwork = (host, port) -> { throw new IOException("No upstream expected"); };
        var first = new WebsiteProxy(host -> new WebsiteProxy.Decision(true, () -> {}, () -> {}), noNetwork, true, 0, token);
        first.start();
        try (first; var browser = browser(first.pacUrl())) {
            browser.navigate(first.verificationUrl()); browser.awaitText("routing verified");
            assertTrue(first.routingObserved());
            int port = first.port(); String pac = first.pacUrl(), oldProof = first.verificationUrl();
            first.close();
            var denied = new AtomicInteger();
            try (var next = new WebsiteProxy(host -> new WebsiteProxy.Decision(true, denied::incrementAndGet, () -> {}), noNetwork, true, port, token)) {
                next.start(); assertEquals(pac, next.pacUrl());
                assertFalse(next.routingObserved()); assertNotEquals(oldProof, next.verificationUrl());
                browser.navigate(oldProof); browser.awaitText("Website blocked");
                assertFalse(next.routingObserved(), "An old verification page cannot prove the new connection");
                browser.navigate(next.verificationUrl()); browser.awaitText("routing verified");
                assertTrue(next.routingObserved());
                int before = denied.get();
                browser.navigate("http://blocked.test/after-reconnect"); browser.awaitText("Website blocked");
                assertTrue(denied.get() > before, "Same browser must enforce rules after reconnection");
            }
        }
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "FOCUSDESKTOP_POLICY_SANDBOX", matches = "1")
    void policiesSurviveNormalRelaunchWithoutProxyFlags() throws Exception {
        Path policyRoot = Files.createDirectory(directory.resolve("policy-root"));
        Path binary = policyRoot.resolve("usr/bin/brave"); Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native browser fixture"); assertTrue(binary.toFile().setExecutable(true));
        Path etc = policyRoot.resolve("etc"); Files.createDirectories(etc.resolve("brave/policies/managed"));
        for (String name : List.of("ld.so.cache", "nsswitch.conf", "passwd", "group", "hosts", "resolv.conf")) {
            Path original = Path.of("/etc", name); if (Files.isRegularFile(original)) Files.copy(original, etc.resolve(name));
        }
        Path profile = Files.createTempDirectory(directory, "policy-profile-");
        var isolated = List.of("/usr/bin/bwrap", "--unshare-user", "--uid", "0", "--gid", "0", "--unshare-pid",
                "--ro-bind", "/usr", "/usr", "--ro-bind", "/bin", "/bin", "--ro-bind", "/opt", "/opt", "--ro-bind", "/lib", "/lib", "--ro-bind", "/lib64", "/lib64",
                "--proc", "/proc", "--dev", "/dev", "--tmpfs", "/tmp", "--bind", profile.toString(), profile.toString(),
                "--ro-bind", etc.toString(), "/etc", "--");
        var denied = new AtomicInteger();
        try (var proxy = new WebsiteProxy(host -> new WebsiteProxy.Decision(true, denied::incrementAndGet, () -> {}), true);
             var settings = new OwnedProxySettings(new LinuxBrowserPolicies(policyRoot), directory.resolve("policy-journal"))) {
            proxy.start();
            settings.apply(proxy.pacUrl());
            try (var browser = browser(null, profile, isolated)) {
                checkRouting(browser, proxy);
                browser.navigate("http://blocked.test/policy-block"); browser.awaitText("Website blocked");
                assertTrue(denied.get() > 0);
                browser.quit();
            }
            proxy.resetRoutingProof();
            try (var reopened = browser(null, profile, isolated)) {
                checkRouting(reopened, proxy);
                int before = denied.get(); reopened.navigate("http://blocked.test/normal-launch"); reopened.awaitText("Website blocked");
                assertTrue(denied.get() > before, "Normal browser relaunch must retain policy routing");
                reopened.quit();
            }
            Path anotherProfile = Files.createTempDirectory(directory, "another-profile-");
            var anotherNamespace = isolated.stream().map(argument -> argument.equals(profile.toString()) ? anotherProfile.toString() : argument).toList();
            proxy.resetRoutingProof();
            try (var another = browser(null, anotherProfile, anotherNamespace)) {
                checkRouting(another, proxy);
                int before = denied.get(); another.navigate("http://blocked.test/another-profile"); another.awaitText("Website blocked");
                assertTrue(denied.get() > before, "Policies must cover another profile without repeating setup");
                another.quit();
            }
        }
        assertFalse(Files.exists(etc.resolve("brave/policies/managed/focusdesktop-proxy.json")));
    }
    private void checkRouting(Browser browser, WebsiteProxy proxy) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        do { browser.navigate(proxy.verificationUrl()); Thread.sleep(250); }
        while (!proxy.routingObserved() && System.nanoTime() < deadline);
        if (!proxy.routingObserved()) {
            browser.navigate("brave://policy/"); Thread.sleep(500);
            var details = browser.command("Runtime.evaluate", Map.of("expression", "(function dump(r){return (r.textContent||'')+Array.from(r.querySelectorAll('*')).filter(n=>n.shadowRoot).map(n=>dump(n.shadowRoot)).join('\\n')})(document)", "returnByValue", true));
            Files.writeString(directory.resolve("policy-debug.json"), details.toPrettyString());
        }
        assertTrue(proxy.routingObserved(), "Native browser did not load persistent proxy policy; isolated policy diagnostics: " + directory);
        browser.awaitText("routing verified");
    }
    public static class CrashFixture {
        public static void main(String[] args) throws Exception {
            try (var proxy = new WebsiteProxy(host -> host.equals("allowed.test") ? WebsiteProxy.Decision.allow()
                    : new WebsiteProxy.Decision(true, () -> {}, () -> {}),
                    (host, port) -> { Files.writeString(Path.of(args[1]), "connected"); return new Socket("127.0.0.1", Integer.parseInt(args[0])); }, true)) {
                proxy.start(); System.out.println(proxy.pacUrl()); System.out.flush();
                while (System.in.read() != -1) { }
            }
        }
    }
    private void origin(HttpExchange request) throws IOException {
        if (request.getRequestURI().getPath().equals("/favicon.ico")) { request.sendResponseHeaders(404, -1); request.close(); return; }
        originRequests.incrementAndGet(); byte[] bytes = ("CONTROLLED_ORIGIN" + request.getRequestURI().getPath()).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.sendResponseHeaders(200, bytes.length); request.getResponseBody().write(bytes); request.close();
    }
    private SSLContext tls() throws Exception {
        Path keys = directory.resolve("test.p12");
        var keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias", "test",
                "-keyalg", "RSA", "-keystore", keys.toString(), "-storepass", "test-only", "-keypass", "test-only", "-dname", "CN=allowed.test", "-validity", "1")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertTrue(keytool.waitFor(15, TimeUnit.SECONDS)); assertEquals(0, keytool.exitValue());
        var store = KeyStore.getInstance("PKCS12"); try (var input = Files.newInputStream(keys)) { store.load(input, "test-only".toCharArray()); }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); managers.init(store, "test-only".toCharArray());
        var ssl = SSLContext.getInstance("TLS"); ssl.init(managers.getKeyManagers(), null, null); return ssl;
    }
    private Browser browser(String pac) throws Exception {
        Path profile = Files.createTempDirectory(directory, "brave-profile-");
        return browser(pac, profile, List.of());
    }
    private Browser browser(String pac, Path profile, List<String> prefix) throws Exception {
        Files.deleteIfExists(profile.resolve("DevToolsActivePort"));
        var command = new ArrayList<>(prefix);
        command.addAll(List.of(Path.of(System.getenv("FOCUSDESKTOP_BRAVE")).toRealPath().toString(), "--headless=new", "--no-first-run", "--no-default-browser-check",
                "--user-data-dir=" + profile, "--remote-debugging-port=0", "--disable-background-networking",
                "--disable-component-update", "--disable-sync", "--disable-quic", "--disable-extensions", "--ignore-certificate-errors",
                "--host-resolver-rules=MAP allowed.test 127.0.0.1, MAP blocked.test 127.0.0.1, MAP *.youtube.com 127.0.0.1, MAP youtube.com 127.0.0.1, EXCLUDE 127.0.0.1, EXCLUDE localhost, MAP * ~NOTFOUND",
                "about:blank"));
        if (pac != null) command.add("--proxy-pac-url=" + pac);
        // Only the private user/mount namespace runs as its mapped uid 0.
        if (!prefix.isEmpty()) command.addAll(List.of("--no-sandbox", "--enable-logging=stderr", "--vmodule=config_dir_policy_loader=2"));
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(profile.resolve("browser.log").toFile());
        Files.createDirectories(profile.resolve("xdg"));
        builder.environment().put("XDG_CONFIG_HOME", profile.resolve("xdg").toString());
        if (!prefix.isEmpty()) builder.environment().put("HOME", profile.toString());
        for (String key : List.of("http_proxy", "https_proxy", "all_proxy", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY")) builder.environment().remove(key);
        var process = builder.start();
        try {
            await(() -> Files.exists(profile.resolve("DevToolsActivePort")) || !process.isAlive());
            assertTrue(Files.exists(profile.resolve("DevToolsActivePort")), "Native browser exited before opening DevTools");
            int port = Integer.parseInt(Files.readAllLines(profile.resolve("DevToolsActivePort")).get(0));
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).build(), HttpResponse.BodyHandlers.ofString());
            String socket = mapper.readTree(response.body()).get(0).get("webSocketDebuggerUrl").asText();
            var browser = new Browser(process); browser.socket = client.newWebSocketBuilder().buildAsync(URI.create(socket), browser).get(5, TimeUnit.SECONDS);
            return browser;
        } catch (Exception | AssertionError error) {
            process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
            String log = Files.readString(profile.resolve("browser.log"));
            throw new AssertionError(error.getMessage() + "\nNative browser diagnostics:\n" + log.substring(0, Math.min(log.length(), 4000)), error);
        }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(30);
        assertTrue(condition.getAsBoolean(), "Native Brave condition timed out");
    }
    private class Browser implements WebSocket.Listener, AutoCloseable {
        private final Process process;
        private final Map<Integer, CompletableFuture<JsonNode>> responses = new ConcurrentHashMap<>();
        private final StringBuilder fragment = new StringBuilder();
        private int nextId;
        private WebSocket socket;
        Browser(Process process) { this.process = process; }
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragment.append(data);
            if (last) {
                try { var value = mapper.readTree(fragment.toString()); var promise = responses.remove(value.path("id").asInt()); if (promise != null) promise.complete(value); }
                catch (IOException ignored) { } finally { fragment.setLength(0); }
            }
            webSocket.request(1); return null;
        }
        JsonNode command(String method, Map<String, Object> params) throws Exception {
            int id = ++nextId; var promise = new CompletableFuture<JsonNode>(); responses.put(id, promise);
            socket.sendText(mapper.writeValueAsString(Map.of("id", id, "method", method, "params", params)), true).get(3, TimeUnit.SECONDS);
            return promise.get(10, TimeUnit.SECONDS);
        }
        void navigate(String url) throws Exception { command("Page.navigate", Map.of("url", url)); }
        void quit() throws Exception {
            socket.sendText(mapper.writeValueAsString(Map.of("id", ++nextId, "method", "Browser.close")), true).get(3, TimeUnit.SECONDS);
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Native browser did not close normally");
        }
        void awaitText(String text) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            String lastText = "";
            do {
                var result = command("Runtime.evaluate", Map.of("expression", "document.body ? document.body.innerText : ''", "returnByValue", true));
                lastText = result.path("result").path("result").path("value").asText();
                if (lastText.contains(text)) return;
                Thread.sleep(50);
            } while (System.nanoTime() < deadline);
            fail("Brave did not display the expected controlled response: " + text + "; actual=" + lastText);
        }
        @Override public void close() throws Exception {
            socket.abort(); process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
