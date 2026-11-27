package com.focusdesktop.infrastructure.proxy;

import com.focusdesktop.infrastructure.desktop.NativeCommands;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** User-context local-policy refresh only. Never called by the privileged guardian. */
final class BrowserPolicyRefresh implements Runnable {
    private static final Set<String> NAMES = Set.of("brave", "brave-browser", "brave-browser-stable", "chrome", "google-chrome", "google-chrome-stable",
            "chromium", "chromium-browser", "microsoft-edge", "microsoft-edge-stable", "brave.exe", "chrome.exe", "msedge.exe", "chromium.exe");
    private final Map<String, Boolean> supported = new HashMap<>();
    @Override public void run() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) return; // macOS uses system PAC notifications.
        String owner = ProcessHandle.current().info().user().orElse(null);
        if (owner == null) return;
        var refreshed = new HashSet<List<String>>();
        try (var processes = ProcessHandle.allProcesses()) {
            processes.filter(ProcessHandle::isAlive).map(ProcessHandle::info)
                    .filter(info -> info.user().filter(owner::equals).isPresent() && info.command().isPresent() && info.arguments().isPresent())
                    .forEach(info -> {
                        try {
                            Path binary = Path.of(info.command().orElseThrow());
                            var args = Arrays.asList(info.arguments().orElseThrow());
                            if (!NAMES.contains(binary.getFileName().toString().toLowerCase(Locale.ROOT))
                                    || args.stream().anyMatch(arg -> arg.startsWith("--type=")) || !supports(binary)) return;
                            var command = command(binary, args);
                            if (refreshed.add(command)) NativeCommands.execute(command);
                        } catch (RuntimeException | IOException ignored) { /* A vanished/older browser remains subject to verification. */ }
                    });
        } catch (RuntimeException ignored) { /* Refresh is best-effort; routing proof remains mandatory. */ }
    }
    static List<String> command(Path binary, List<String> currentArguments) {
        if (!binary.isAbsolute() || !NAMES.contains(binary.getFileName().toString().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Unknown browser executable");
        var command = new ArrayList<>(List.of(binary.toString(), "--refresh-platform-policy"));
        for (int index = 0; index < currentArguments.size(); index++) {
            String arg = currentArguments.get(index), profile = null;
            if (arg.startsWith("--user-data-dir=")) profile = arg.substring(16);
            else if (arg.equals("--user-data-dir") && index + 1 < currentArguments.size()) profile = currentArguments.get(++index);
            if (profile != null) {
                if (profile.length() > 4096 || profile.chars().anyMatch(Character::isISOControl) || !Path.of(profile).isAbsolute())
                    throw new IllegalArgumentException("Invalid browser data directory");
                command.add("--user-data-dir=" + profile); break;
            }
        }
        return List.copyOf(command);
    }
    private boolean supports(Path binary) throws IOException {
        var files = new ArrayList<>(List.of(binary));
        if (binary.toString().toLowerCase(Locale.ROOT).endsWith(".exe"))
            for (String name : List.of("chrome.dll", "msedge.dll")) {
                Path library = binary.resolveSibling(name); if (Files.isRegularFile(library)) files.add(library);
            }
        for (Path file : files) {
            String key = file + ":" + Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
            Boolean result = supported.get(key);
            if (result == null) { result = containsSwitch(file); supported.put(key, result); }
            if (result) return true;
        }
        return false;
    }
    private boolean containsSwitch(Path file) throws IOException {
        byte[] pattern = "refresh-platform-policy".getBytes(StandardCharsets.US_ASCII);
        int matched = 0;
        try (var input = new BufferedInputStream(Files.newInputStream(file), 131072)) {
            byte[] buffer = new byte[131072]; int count;
            while ((count = input.read(buffer)) != -1) {
                for (int index = 0; index < count; index++) {
                    byte value = buffer[index];
                    matched = value == pattern[matched] ? matched + 1 : value == pattern[0] ? 1 : 0;
                    if (matched == pattern.length) return true;
                }
            }
        }
        return false;
    }
}
