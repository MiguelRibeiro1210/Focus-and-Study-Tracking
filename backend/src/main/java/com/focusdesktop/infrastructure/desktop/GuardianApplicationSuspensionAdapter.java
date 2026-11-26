package com.focusdesktop.infrastructure.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.port.ActiveApplicationPort;
import com.focusdesktop.application.port.ApplicationSuspensionPort;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

@Component
public class GuardianApplicationSuspensionAdapter implements ApplicationSuspensionPort {
    private final Path dataDir;
    private final ObjectMapper mapper;
    private Process guardian;
    private BufferedWriter input;
    private BufferedReader output;
    private ExecutorService reader;

    public GuardianApplicationSuspensionAdapter(@Value("${focusdesktop.data-dir}") String dataDir, ObjectMapper mapper) {
        this.dataDir = Path.of(dataDir).toAbsolutePath(); this.mapper = mapper;
    }

    @Override
    public synchronized void suspend(ActiveApplicationPort.Observation observation) {
        ensureStarted(); command("SUSPEND", observation, "OK");
    }
    @Override
    public synchronized void resume(ActiveApplicationPort.Observation observation) {
        if (guardian == null || !guardian.isAlive()) { recover(); return; }
        command("RESUME", observation, "OK");
    }
    @Override
    public synchronized boolean isSuspended(ActiveApplicationPort.Observation observation) {
        if (guardian == null || !guardian.isAlive()) return false;
        String state = request(new ApplicationSuspensionGuardian.Command("STATE", observation.pid(), observation.startedAt().toEpochMilli()));
        if (!Set.of("SUSPENDED", "RELEASED").contains(state)) throw unavailable();
        return "SUSPENDED".equals(state);
    }
    @Override
    public synchronized void recover() {
        Path journal = dataDir.resolve("application-suspensions.json");
        if ((guardian == null || !guardian.isAlive()) && Files.exists(journal)) ensureStarted();
    }
    @Override
    public synchronized void releaseAll() {
        if (guardian == null || !guardian.isAlive()) { recover(); }
        if (guardian != null && guardian.isAlive()) command("RELEASE_ALL", null, "OK");
    }

    private void ensureStarted() {
        if (guardian != null && guardian.isAlive()) return;
        closeStreams();
        try {
            Files.createDirectories(dataDir);
            List<String> arguments = launcher();
            guardian = new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            input = new BufferedWriter(new OutputStreamWriter(guardian.getOutputStream(), StandardCharsets.UTF_8));
            output = new BufferedReader(new InputStreamReader(guardian.getInputStream(), StandardCharsets.UTF_8));
            reader = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "guardian-response"); thread.setDaemon(true); return thread; });
            String ready = readLine(15);
            if ("RECOVERY_REQUIRED".equals(ready)) throw new ActiveApplicationPort.Unavailable(
                    "An interrupted Windows suspension needs manual recovery. Resume affected apps with Windows Resource Monitor, then retry protection.");
            if (!"READY".equals(ready)) throw unavailable();
        } catch (Exception exception) {
            closeStreams();
            if (exception instanceof ActiveApplicationPort.Unavailable failure) throw failure;
            throw unavailable();
        }
    }

    List<String> launcher() throws Exception {
        var parent = ProcessHandle.current();
        String journal = dataDir.resolve("application-suspensions.json").toString();
        String started = Long.toString(parent.info().startInstant().orElseThrow().toEpochMilli());
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            Path script = dataDir.resolve("windows-suspension-guardian.ps1");
            if (Files.isSymbolicLink(script)) throw unavailable();
            try (InputStream resource = getClass().getResourceAsStream("/desktop/windows-suspension-guardian.ps1")) {
                if (resource == null) throw unavailable();
                Files.write(script, resource.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            String protectedPids = ProcessAncestors.selfAndAncestors().stream()
                    .map(process -> Long.toString(process.pid())).collect(java.util.stream.Collectors.joining(","));
            return List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File",
                    script.toString(), "-JournalPath", journal, "-ParentProcessId", Long.toString(parent.pid()), "-ParentStartedMs", started,
                    "-ProtectedPids", protectedPids);
        }
        String java = parent.info().command().orElseThrow();
        String helperMain = ApplicationSuspensionGuardian.class.getName();
        List<String> arguments = new ArrayList<>(List.of(java));
        // Boot passes this explicitly so packaged resources are not mistaken for a filesystem classpath.
        String packagedJar = System.getProperty("focusdesktop.backend-jar");
        String classpath = System.getProperty("java.class.path");
        if (packagedJar == null && classpath.endsWith(".jar") && Files.isRegularFile(Path.of(classpath))) {
            try (var archive = new java.util.jar.JarFile(classpath)) {
                if (archive.getEntry("org/springframework/boot/loader/launch/PropertiesLauncher.class") != null) packagedJar = classpath;
            }
        }
        if (packagedJar != null && !packagedJar.isBlank()) {
            arguments.addAll(List.of("-Dloader.main=" + helperMain, "-cp", packagedJar, "org.springframework.boot.loader.launch.PropertiesLauncher"));
        } else {
            arguments.addAll(List.of("-cp", classpath, helperMain));
        }
        arguments.addAll(List.of(journal, Long.toString(parent.pid()), started));
        return arguments;
    }

    private void command(String action, ActiveApplicationPort.Observation observation, String expected) {
        String result = request(new ApplicationSuspensionGuardian.Command(action, observation == null ? 0 : observation.pid(),
                observation == null ? 0 : observation.startedAt().toEpochMilli()));
        if (!expected.equals(result)) throw new ActiveApplicationPort.Unavailable(switch (result) {
            case "ALREADY_STOPPED" -> "The application was already stopped by another controller; FocusDesktop left it unchanged.";
            case "PROTECTED" -> "This application is part of the desktop or recovery path and cannot be suspended.";
            case "EXITED" -> "The observed application instance has exited or changed; no other instance was suspended.";
            case "PERMISSION" -> "FocusDesktop does not have permission to suspend this application.";
            default -> "Application suspension or restoration failed. Check app permissions or restart FocusDesktop.";
        });
    }
    private String request(ApplicationSuspensionGuardian.Command command) {
        try {
            input.write(mapper.writeValueAsString(command)); input.newLine(); input.flush();
            return readLine(5);
        } catch (Exception exception) { closeStreams(); throw unavailable(); }
    }
    private String readLine(int seconds) throws Exception {
        String value = reader.submit(output::readLine).get(seconds, TimeUnit.SECONDS);
        if (value == null || value.length() > 64) throw unavailable();
        return value;
    }

    @PreDestroy
    public synchronized void close() {
        try { if (guardian != null && guardian.isAlive()) command("QUIT", null, "OK"); }
        finally { closeStreams(); }
    }
    private void closeStreams() {
        // Closing stdin is the independent guardian's restoration signal. Do not
        // forcibly kill it or close a reader whose pending read holds its lock.
        if (input != null) try { input.close(); } catch (IOException ignored) { }
        if (reader != null) reader.shutdownNow();
        guardian = null; input = null; output = null; reader = null;
    }
    private static ActiveApplicationPort.Unavailable unavailable() {
        return new ActiveApplicationPort.Unavailable("Application suspension or restoration is unavailable; check process permissions and native helper support.");
    }
}
