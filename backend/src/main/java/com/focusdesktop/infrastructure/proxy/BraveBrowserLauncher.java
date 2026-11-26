package com.focusdesktop.infrastructure.proxy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Native Linux installation only; never accepts executable paths or shell text from the API. */
class BraveBrowserLauncher {
    boolean available() { return executable().isPresent(); }
    boolean systemProxySupported() {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) return true;
        return Arrays.stream(System.getenv().getOrDefault("XDG_CURRENT_DESKTOP", "").split(":"))
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("gnome"));
    }
    Optional<Path> executable() {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) return Optional.empty();
        return List.of("/usr/bin/brave", "/usr/bin/brave-browser", "/opt/brave.com/brave/brave").stream()
                .map(Path::of).filter(Files::isExecutable).findFirst();
    }
    boolean running() {
        String user = ProcessHandle.current().info().user().orElse(null);
        if (user == null) return true; // Unknown ownership cannot authorize a conflicting profile launch.
        try (var processes = ProcessHandle.allProcesses()) {
            return processes.anyMatch(process -> process.isAlive() && process.info().user().filter(user::equals).isPresent()
                    && process.info().command().map(command -> {
                        String name = Path.of(command).getFileName().toString();
                        return Set.of("brave", "brave-browser", "brave-browser-stable").contains(name);
                    }).orElse(false));
        }
    }
    void launch(String pac, String verification) throws IOException {
        Path binary = executable().orElseThrow(() -> new IOException("Native Brave unavailable"));
        var command = command(binary, pac, verification);
        var process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (process.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS) && process.exitValue() != 0)
                throw new IOException("Brave startup failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IOException("Brave startup interrupted");
        }
    }
    static List<String> command(Path binary, String pac, String verification) {
        URI url = URI.create(pac), challenge = URI.create(verification);
        if (!"http".equals(url.getScheme()) || !"127.0.0.1".equals(url.getHost()) || url.getPort() < 1
                || url.getRawUserInfo() != null || url.getQuery() != null || url.getFragment() != null
                || !url.getPath().matches("/pac/[0-9a-f-]{36}")
                || !"http".equals(challenge.getScheme()) || !"focusdesktop.invalid".equals(challenge.getHost())
                || challenge.getRawUserInfo() != null || challenge.getPort() != -1 || challenge.getQuery() != null
                || challenge.getFragment() != null || !challenge.getPath().matches("/verify/[0-9a-f-]{36}"))
            throw new IllegalArgumentException("Invalid local Brave routing configuration");
        return List.of(binary.toString(), "--proxy-pac-url=" + pac, "--new-window", verification);
    }
}
