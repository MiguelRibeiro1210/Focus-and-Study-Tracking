package com.focusdesktop.infrastructure.desktop;

import com.focusdesktop.application.port.ActiveApplicationPort.Unavailable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

@FunctionalInterface
public interface NativeCommands {
    String run(List<String> arguments);

    static String execute(List<String> arguments) {
        return execute(arguments, false);
    }

    static String execute(List<String> arguments, boolean includeDiagnostics) {
        return execute(arguments, includeDiagnostics, 3);
    }

    static String execute(List<String> arguments, boolean includeDiagnostics, int timeoutSeconds) {
        Process process = null;
        ExecutorService reader = Executors.newFixedThreadPool(includeDiagnostics ? 2 : 1, task -> {
            Thread thread = new Thread(task, "foreground-command-output"); thread.setDaemon(true); return thread;
        });
        try {
            process = new ProcessBuilder(arguments).redirectError(includeDiagnostics ? ProcessBuilder.Redirect.PIPE : ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            Process child = process;
            var output = reader.submit(() -> child.getInputStream().readNBytes(1_048_577));
            Future<byte[]> errors = includeDiagnostics ? reader.submit(() -> {
                byte[] captured = child.getErrorStream().readNBytes(4096);
                child.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());
                return captured;
            }) : null;
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw new Unavailable("Desktop query timed out; check desktop access and permissions.");
            byte[] bytes = output.get(1, TimeUnit.SECONDS);
            if (bytes.length > 1_048_576 || process.exitValue() != 0) {
                String detail = errors == null ? "" : "\n" + new String(errors.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8).trim();
                throw new Unavailable("Desktop query failed; check required tools and permissions." + detail);
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new Unavailable("Desktop query was interrupted.");
        } catch (IOException | ExecutionException | TimeoutException exception) {
            throw new Unavailable("Desktop query is unavailable; check required tools and permissions.");
        } finally {
            if (process != null) {
                process.destroyForcibly();
                try { process.getInputStream().close(); process.getErrorStream().close(); } catch (IOException ignored) { }
            }
            reader.shutdownNow();
        }
    }
}
