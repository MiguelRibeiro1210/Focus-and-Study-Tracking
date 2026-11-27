package com.focusdesktop.infrastructure.proxy;

import com.focusdesktop.application.port.WebsiteProtectionPreferences.RoutingMethod;
import org.springframework.beans.factory.annotation.Autowired;
import com.focusdesktop.application.port.WebsiteProtectionPreferences;
import com.focusdesktop.application.usecase.EvaluateWebsiteRequestUseCase;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebsiteProtectionRuntime implements AutoCloseable {
    public record Status(boolean enabled, String state, boolean routingVerified, String verificationUrl,
                         String reason, com.focusdesktop.application.usecase.ApplicationProtectionCoordinator.Notice notice,
                         boolean runtimeEnabled, RoutingMethod routingMethod, boolean braveAvailable, boolean braveLaunchPending,
                         boolean automaticAvailable) { }
    private final WebsiteProtectionPreferences preferences;
    private final EvaluateWebsiteRequestUseCase gate;
    private final Path journal;
    private final boolean managed;
    private final BraveBrowserLauncher brave;
    private final Runnable refreshBrowsers;
    private Instant braveLaunchUntil;
    private WebsiteProxy proxy;
    private BraveProxyEndpoint braveEndpoint;
    private Process guardian;
    private java.net.Socket guardianControl;
    private ElevatedJavaResources elevatedResources;
    private boolean administratorApplied;
    private boolean administratorRecoveryNeeded;
    private boolean guardianStopping;
    private BufferedWriter input;
    private BufferedReader output;
    private ExecutorService reads;
    private volatile String state = "DISABLED";
    private String reason = "Website protection is disabled.";
    private Instant retryAfter = Instant.EPOCH;

    @Autowired
    public WebsiteProtectionRuntime(WebsiteProtectionPreferences preferences, EvaluateWebsiteRequestUseCase gate,
            @Value("${focusdesktop.data-dir}") String dataDir,
            @Value("${focusdesktop.blocking.monitor-enabled:false}") boolean managed) {
        this(preferences, gate, dataDir, managed, new BraveBrowserLauncher(), new BrowserPolicyRefresh());
    }
    WebsiteProtectionRuntime(WebsiteProtectionPreferences preferences, EvaluateWebsiteRequestUseCase gate,
                             String dataDir, boolean managed, BraveBrowserLauncher brave) {
        this(preferences, gate, dataDir, managed, brave, () -> {});
    }
    WebsiteProtectionRuntime(WebsiteProtectionPreferences preferences, EvaluateWebsiteRequestUseCase gate,
                             String dataDir, boolean managed, BraveBrowserLauncher brave, Runnable refreshBrowsers) {
        this.preferences = preferences; this.gate = gate; this.managed = managed; this.brave = brave;
        this.refreshBrowsers = refreshBrowsers;
        journal = Path.of(dataDir).toAbsolutePath().resolve("website-proxy-settings.json");
    }
    public synchronized Status configure(boolean enabled, String consent) {
        return configure(enabled, consent, preferences.get().routingMethod());
    }
    public synchronized Status configure(boolean enabled, String consent, RoutingMethod method) {
        Objects.requireNonNull(method, "routingMethod");
        if (enabled && !"CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC".equals(consent)
                && !(method == RoutingMethod.BRAVE && "FORWARD_SYSTEM_PROXY_TRAFFIC".equals(consent)))
            throw new IllegalArgumentException("Explicit forwarding consent is required");
        if (administratorRecoveryNeeded) {
            try {
                startGuardian("RECOVER"); administratorRecoveryNeeded = false; restoreAndClose();
            } catch (Exception exception) {
                administratorRecoveryNeeded = false;
                try { restoreAndClose(); } catch (Exception ignored) { }
                administratorRecoveryNeeded = true;
                state = "RESTORING"; reason = "Owned connection settings still need restoration. Approve the OS permission prompt and retry restoration.";
                return status();
            }
        }
        if (method != preferences.get().routingMethod()) {
            try { restoreAndClose(); }
            catch (Exception exception) {
                state = "RESTORING"; reason = "Wait for owned network settings to be restored before changing routing.";
                return status();
            }
        }
        preferences.setEnabled(enabled, consent, method);
        retryAfter = Instant.EPOCH;
        reconcile();
        return status();
    }
    public synchronized Status status() {
        var preference = preferences.get();
        boolean enabled = preference.enabled();
        boolean verified = enabled && proxy != null && proxy.routingObserved() && state.equals("READY");
        return new Status(enabled, enabled && verified ? "VERIFIED" : state, verified,
                enabled && proxy != null ? proxy.verificationUrl() : null,
                !managed ? "Protection runtime is disabled. Use Tauri or explicitly enable protection in the development backend." : reason,
                gate.notice(), managed, preference.routingMethod(), brave.available(), braveLaunchUntil != null, brave.systemProxySupported());
    }
    @Scheduled(fixedDelay = 500, initialDelay = 1000, scheduler = "websiteTaskScheduler")
    public synchronized void reconcile() {
        try {
            boolean enabled = preferences.get().enabled();
            if (!managed) {
                state = enabled ? "UNAVAILABLE" : "DISABLED";
                reason = enabled ? "Website protection requires the managed desktop application." : "Website protection is disabled.";
                return;
            }
            if (!enabled) { restoreAndClose(); return; }
            if (now().isBefore(retryAfter)) return;
            if (preferences.get().routingMethod() == RoutingMethod.BRAVE) {
                if (!brave.available()) {
                    state = "UNAVAILABLE"; reason = "Native Linux Brave is not available. Flatpak/Snap installations are not supported by this setup.";
                    return;
                }
                if (proxy == null) {
                    restoreAndClose();
                    startProxy(true);
                    state = "READY";
                    reason = "Connect Brave once. Session changes reuse the connection without closing your browser.";
                    if (braveEndpoint.saved() && brave.running()) {
                        try {
                            brave.launch(proxy.pacUrl(), proxy.verificationUrl());
                            reason = "Checking the saved Brave connection. If it stays unverified, check the connection or repeat first-time setup; Brave may temporarily bypass an unavailable proxy.";
                        } catch (IOException exception) {
                            reason = "The saved Brave connection could not be checked. Open the connection check before repeating setup.";
                        }
                    }
                }
                processBraveLaunch();
                proxy.reconcile();
                return;
            }
            if (!brave.systemProxySupported()) {
                state = "UNAVAILABLE";
                reason = "Automatic browser connection requires PolicyKit (pkexec) and an authentication agent on Linux. Install them and retry setup.";
                return;
            }
            if (guardian == null || !guardian.isAlive()) {
                restoreAndClose();
                state = "STARTING";
                startProxy(true);
                startGuardian(proxy.pacUrl());
                state = "READY";
                reason = "Automatic connection settings are applied. Check each browser you use. Firefox may need one initial restart to load its policy; normal relaunches retain the connection.";
                refreshBrowsers.run();
            }
            input.write("PING\n"); input.flush();
            String heartbeat = read(15);
            if ("CHANGED".equals(heartbeat)) {
                preferences.setEnabled(false, null);
                restoreAndClose();
                reason = "Website protection was disabled because your proxy settings changed. Your replacement settings were preserved.";
                return;
            }
            if (!"OK".equals(heartbeat)) throw new IOException("Guardian unavailable");
            proxy.reconcile();
        } catch (Exception exception) {
            retryAfter = preferences.get().routingMethod() == RoutingMethod.SYSTEM_PROXY && useAdministratorGuardian()
                    ? Instant.MAX : now().plusSeconds(10);
            try { restoreAndClose(); } catch (Exception ignored) { }
            state = administratorRecoveryNeeded || Files.exists(journal) ? "RESTORING" : "UNAVAILABLE";
            reason = preferences.get().routingMethod() == RoutingMethod.BRAVE && !Files.exists(journal)
                    ? "The saved Brave connection could not start. Its local port may be occupied or its saved configuration unavailable. The connection address was preserved; protection is not confirmed."
                    : "Automatic connection could not be confirmed. Approve the OS permission prompt and check for existing custom proxy policies. On Linux, PolicyKit and an authentication agent are required. Retry setup explicitly; existing proxy settings are preserved.";
        }
    }
    private void startProxy(boolean fallback) throws IOException {
        java.util.function.Function<String, WebsiteProxy.Decision> policy = host -> {
            if (!state.equals("READY")) return WebsiteProxy.Decision.allow();
            var decision = gate.execute(host);
            return new WebsiteProxy.Decision(decision.block(), decision.denied(), decision.failed());
        };
        if (fallback) {
            braveEndpoint = new BraveProxyEndpoint(journal.resolveSibling("brave-proxy-endpoint"));
            proxy = braveEndpoint.start(policy);
        } else {
            proxy = new WebsiteProxy(policy);
            proxy.start();
        }
    }
    public synchronized Status requestBraveSetup() {
        if (!managed || !preferences.get().enabled() || preferences.get().routingMethod() != RoutingMethod.BRAVE
                || proxy == null || !state.equals("READY") || !brave.available())
            throw new IllegalArgumentException("Enable available Brave routing before requesting setup");
        if (proxy.routingObserved()) return status();
        if (braveLaunchUntil == null) {
            proxy.resetRoutingProof();
            braveLaunchUntil = now().plusSeconds(60);
            reason = "Close all Brave windows within 60 seconds, including this development UI. FocusDesktop will reopen your existing profile; it will not force-close Brave.";
        }
        return status();
    }
    private void processBraveLaunch() {
        if (braveLaunchUntil == null) return;
        if (!now().isBefore(braveLaunchUntil)) {
            braveLaunchUntil = null;
            reason = "Brave setup timed out. Close all Brave windows and background processes yourself, then retry Set up Brave.";
        } else if (!brave.running()) {
            braveLaunchUntil = null;
            try {
                brave.launch(proxy.pacUrl(), proxy.verificationUrl());
                reason = "Brave was opened with proxy routing. Wait for the verification page before relying on protection.";
            } catch (IOException exception) {
                reason = "Brave could not be opened. Check the native installation and retry Set up Brave.";
            }
        }
    }
    Instant now() { return Instant.now(); }
    private void startGuardian(String pac) throws Exception {
        guardianStopping = false;
        Files.createDirectories(journal.getParent());
        if (useAdministratorGuardian()) { startAdministratorGuardian(pac); return; }
        guardian = new ProcessBuilder(launcher(pac)).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        input = new BufferedWriter(new OutputStreamWriter(guardian.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
        output = new BufferedReader(new InputStreamReader(guardian.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        reads = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task, "proxy-settings-response"); thread.setDaemon(true); return thread; });
        if (!"READY".equals(read(60))) throw new IOException("Proxy settings unavailable");
    }
    boolean useAdministratorGuardian() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return os.contains("linux") || os.contains("mac");
    }
    private void startAdministratorGuardian(String pac) throws Exception {
        Path credential = Files.createTempFile(journal.getParent(), "connection-credential-", ".tmp");
        try (var server = new java.net.ServerSocket()) {
            server.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
            server.setSoTimeout(500);
            String secret = UUID.randomUUID().toString();
            Files.writeString(credential, secret);
            Files.setPosixFilePermissions(credential, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            var parent = ProcessHandle.current();
            guardian = new ProcessBuilder(administratorLauncher(List.of(Integer.toString(server.getLocalPort()), credential.toString(), Long.toString(parent.pid()),
                            Long.toString(parent.info().startInstant().orElseThrow().toEpochMilli()), pac)))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (guardianControl == null && guardian.isAlive() && System.nanoTime() < deadline) {
                try {
                    var candidate = server.accept(); candidate.setSoTimeout(5000);
                    try {
                        byte[] proof = candidate.getInputStream().readNBytes(37);
                        if (java.security.MessageDigest.isEqual(proof, (secret + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                            guardianControl = candidate;
                    } finally { if (guardianControl != candidate) candidate.close(); }
                } catch (java.net.SocketTimeoutException ignored) { }
            }
            if (guardianControl == null) throw new IOException("Connection authorization unavailable");
            guardianControl.setSoTimeout(0);
            input = new BufferedWriter(new OutputStreamWriter(guardianControl.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            input.write("AUTH\n"); input.flush();
            administratorApplied = true;
            output = new BufferedReader(new InputStreamReader(guardianControl.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            reads = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task, "administrator-proxy-response"); thread.setDaemon(true); return thread; });
            if (!"READY".equals(read(60))) throw new IOException("Browser connection unavailable");
        } finally { Files.deleteIfExists(credential); }
    }
    List<String> administratorLauncher(List<String> arguments) {
        try {
            elevatedResources = ElevatedJavaResources.prepare(ProxyGuardianCommand.javaCommand(PrivilegedProxyGuardian.class, arguments), journal.getParent());
            return ProxyGuardianCommand.elevated(System.getProperty("os.name"), elevatedResources.command());
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    List<String> launcher(String pac) {
        var args = new ArrayList<String>();
        args.add(ProcessHandle.current().info().command().orElseThrow());
        String jar = System.getProperty("focusdesktop.backend-jar");
        String classpath = System.getProperty("java.class.path");
        if (jar == null && classpath.endsWith(".jar") && !classpath.contains(File.pathSeparator)) jar = classpath;
        if (jar != null) {
            args.add("-Dloader.main=" + ProxySettingsGuardian.class.getName()); args.add("-cp"); args.add(jar);
            args.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else { args.add("-cp"); args.add(classpath); args.add(ProxySettingsGuardian.class.getName()); }
        args.add(journal.toString());
        args.add(Long.toString(ProcessHandle.current().pid()));
        args.add(Long.toString(ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli()));
        args.add(pac); return args;
    }
    private String read(int seconds) throws Exception { return reads.submit(output::readLine).get(seconds, TimeUnit.SECONDS); }
    private void restoreAndClose() throws Exception {
        boolean hadGuardian = guardian != null;
        braveLaunchUntil = null;
        state = "RESTORING";
        // Revoked consent must stop forwarding even while authorization or
        // restoration is pending. Every automatic PAC has DIRECT fallback.
        if (proxy != null) { proxy.close(); proxy = null; }
        if (braveEndpoint != null) { braveEndpoint.close(); braveEndpoint = null; }
        if (administratorRecoveryNeeded) throw new IOException("Administrator restoration requires an explicit retry");
        if (guardian != null && guardian.isAlive()) {
            if (input == null && guardianControl == null) guardian.destroy();
            if (input != null && !guardianStopping) {
                guardianStopping = true;
                try {
                    input.write("QUIT\n"); input.flush();
                    if (guardianControl != null) guardianControl.shutdownOutput(); else input.close();
                } catch (IOException ignored) { }
            }
            else if (input == null && guardianControl != null) guardianControl.close();
            if (!guardian.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Waiting for proxy restoration");
        }
        if (administratorApplied) {
            String confirmation = null;
            try { confirmation = read(5); } catch (Exception ignored) { }
            administratorApplied = false;
            if (!Set.of("RESTORED", "NO_CHANGES").contains(confirmation == null ? "" : confirmation)) {
                administratorRecoveryNeeded = true;
                closeGuardianStreams();
                throw new IOException("Administrator restoration was not confirmed");
            }
        }
        closeGuardianStreams();
        if (Files.exists(journal)) {
            startGuardian("RECOVER"); input.close();
            if (!guardian.waitFor(10, TimeUnit.SECONDS) || Files.exists(journal)) throw new IOException("Proxy recovery pending");
            closeGuardianStreams();
        }
        state = "DISABLED"; reason = "Website protection is disabled; owned proxy settings have been restored.";
        if (hadGuardian && preferences.get().routingMethod() == RoutingMethod.SYSTEM_PROXY) refreshBrowsers.run();
    }
    private void closeGuardianStreams() {
        try { if (guardianControl != null) guardianControl.close(); } catch (IOException ignored) { }
        try { if (input != null) input.close(); } catch (IOException ignored) { }
        if (reads != null) reads.shutdownNow();
        if (elevatedResources != null && (guardian == null || !guardian.isAlive())) {
            try { elevatedResources.close(); elevatedResources = null; } catch (IOException ignored) { }
        }
        // Do not close a reader with a pending readLine or kill a restoring guardian.
        guardian = null; guardianControl = null; input = null; output = null; reads = null;
    }
    @PreDestroy public synchronized void close() {
        try { restoreAndClose(); } catch (Exception exception) { state = "RESTORING"; }
    }
}
