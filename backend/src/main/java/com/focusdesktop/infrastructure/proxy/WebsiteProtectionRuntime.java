package com.focusdesktop.infrastructure.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                         String reason, com.focusdesktop.application.usecase.ApplicationProtectionCoordinator.Notice notice) { }
    private final WebsiteProtectionPreferences preferences;
    private final EvaluateWebsiteRequestUseCase gate;
    private final Path journal;
    private final boolean managed;
    private WebsiteProxy proxy;
    private Process guardian;
    private BufferedWriter input;
    private BufferedReader output;
    private ExecutorService reads;
    private volatile String state = "DISABLED";
    private String reason = "Website protection is disabled.";
    private Instant retryAfter = Instant.EPOCH;

    public WebsiteProtectionRuntime(WebsiteProtectionPreferences preferences, EvaluateWebsiteRequestUseCase gate,
            @Value("${focusdesktop.data-dir}") String dataDir,
            @Value("${focusdesktop.blocking.monitor-enabled:false}") boolean managed) {
        this.preferences = preferences; this.gate = gate; this.managed = managed;
        journal = Path.of(dataDir).toAbsolutePath().resolve("website-proxy-settings.json");
    }
    public synchronized Status configure(boolean enabled, String consent) {
        preferences.setEnabled(enabled, consent);
        retryAfter = Instant.EPOCH;
        reconcile();
        return status();
    }
    public synchronized Status status() {
        boolean enabled = preferences.get().enabled();
        boolean verified = proxy != null && proxy.routingObserved() && state.equals("READY");
        return new Status(enabled, enabled && verified ? "VERIFIED" : state, verified,
                enabled && proxy != null ? proxy.verificationUrl() : null, reason, gate.notice());
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
            if (Instant.now().isBefore(retryAfter)) return;
            if (guardian == null || !guardian.isAlive()) {
                restoreAndClose();
                state = "STARTING";
                proxy = new WebsiteProxy(host -> {
                    // A missing native integration never silently claims enforcement.
                    if (!state.equals("READY")) return WebsiteProxy.Decision.allow();
                    var decision = gate.execute(host);
                    return new WebsiteProxy.Decision(decision.block(), decision.denied(), decision.failed());
                });
                proxy.start();
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
            state = Files.exists(journal) ? "RESTORING" : "UNAVAILABLE";
            reason = "Website setup or restoration could not be confirmed. Check OS proxy permissions and browser connection settings; existing custom proxies are preserved.";
            retryAfter = Instant.now().plusSeconds(10);
            try { restoreAndClose(); } catch (Exception ignored) { }
            if (state.equals("DISABLED")) state = "UNAVAILABLE";
        }
    }
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
