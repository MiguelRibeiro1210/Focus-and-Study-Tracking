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
                         boolean runtimeEnabled, RoutingMethod routingMethod, boolean braveAvailable, boolean braveLaunchPending) { }
    private final WebsiteProtectionPreferences preferences;
    private final EvaluateWebsiteRequestUseCase gate;
    private final Path journal;
    private final boolean managed;
    private final BraveBrowserLauncher brave;
    private Instant braveLaunchUntil;
    private WebsiteProxy proxy;
    private BraveProxyEndpoint braveEndpoint;
    private Process guardian;
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
        this(preferences, gate, dataDir, managed, new BraveBrowserLauncher());
    }
    WebsiteProtectionRuntime(WebsiteProtectionPreferences preferences, EvaluateWebsiteRequestUseCase gate,
                             String dataDir, boolean managed, BraveBrowserLauncher brave) {
        this.preferences = preferences; this.gate = gate; this.managed = managed; this.brave = brave;
        journal = Path.of(dataDir).toAbsolutePath().resolve("website-proxy-settings.json");
    }
    public synchronized Status configure(boolean enabled, String consent) {
        return configure(enabled, consent, preferences.get().routingMethod());
    }
    public synchronized Status configure(boolean enabled, String consent, RoutingMethod method) {
        Objects.requireNonNull(method, "routingMethod");
        if (enabled && !"FORWARD_SYSTEM_PROXY_TRAFFIC".equals(consent))
            throw new IllegalArgumentException("Explicit forwarding consent is required");
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
                gate.notice(), managed, preference.routingMethod(), brave.available(), braveLaunchUntil != null);
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
                reason = "Automatic Linux system proxy setup requires GNOME. On Hyprland choose Brave routing instead.";
                return;
            }
            if (guardian == null || !guardian.isAlive()) {
                restoreAndClose();
                state = "STARTING";
                startProxy(false);
                startGuardian(proxy.pacUrl());
                state = "READY";
                reason = "Open the verification link in each browser you use. Only browsers using system proxy settings can be protected.";
            }
            input.write("PING\n"); input.flush();
            String heartbeat = read(5);
            if ("CHANGED".equals(heartbeat)) {
                preferences.setEnabled(false, null);
                restoreAndClose();
                reason = "Website protection was disabled because your proxy settings changed. Your replacement settings were preserved.";
                return;
            }
            if (!"OK".equals(heartbeat)) throw new IOException("Guardian unavailable");
            proxy.reconcile();
        } catch (Exception exception) {
            retryAfter = now().plusSeconds(10);
            try { restoreAndClose(); } catch (Exception ignored) { }
            state = Files.exists(journal) ? "RESTORING" : "UNAVAILABLE";
            reason = preferences.get().routingMethod() == RoutingMethod.BRAVE && !Files.exists(journal)
                    ? "The saved Brave connection could not start. Its local port may be occupied or its saved configuration unavailable. The connection address was preserved; protection is not confirmed."
                    : "Website setup or restoration could not be confirmed. Check OS proxy permissions and browser connection settings; existing custom proxies are preserved.";
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
        Files.createDirectories(journal.getParent());
        guardian = new ProcessBuilder(launcher(pac)).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        input = new BufferedWriter(new OutputStreamWriter(guardian.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
        output = new BufferedReader(new InputStreamReader(guardian.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        reads = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task, "proxy-settings-response"); thread.setDaemon(true); return thread; });
        if (!"READY".equals(read(30))) throw new IOException("Proxy settings unavailable");
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
        braveLaunchUntil = null;
        state = "RESTORING";
        if (guardian != null && guardian.isAlive()) {
            input.close();
            if (!guardian.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Waiting for proxy restoration");
        }
        closeGuardianStreams();
        if (Files.exists(journal)) {
            startGuardian("RECOVER"); input.close();
            if (!guardian.waitFor(10, TimeUnit.SECONDS) || Files.exists(journal)) throw new IOException("Proxy recovery pending");
            closeGuardianStreams();
        }
        if (proxy != null) { proxy.close(); proxy = null; }
        if (braveEndpoint != null) { braveEndpoint.close(); braveEndpoint = null; }
        state = "DISABLED"; reason = "Website protection is disabled; owned proxy settings have been restored.";
    }
    private void closeGuardianStreams() {
        try { if (input != null) input.close(); } catch (IOException ignored) { }
        if (reads != null) reads.shutdownNow();
        // Do not close a reader with a pending readLine or kill a restoring guardian.
        guardian = null; input = null; output = null; reads = null;
    }
    @PreDestroy public synchronized void close() {
        try { restoreAndClose(); } catch (Exception exception) { state = "RESTORING"; }
    }
}
