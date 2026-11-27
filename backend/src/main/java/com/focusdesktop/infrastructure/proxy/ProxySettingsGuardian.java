package com.focusdesktop.infrastructure.proxy;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.*;

/** Independent lease owner; no network listener, TLS processing or telemetry. */
public final class ProxySettingsGuardian {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-check")) {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"version\":1}");
            System.out.println("Packaged website settings guardian is available."); return;
        }
        run(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? new WindowsBrowserPolicies() : new NativeProxySettings(), args, 15000);
    }
    static void run(OwnedProxySettings.Store store, String[] args, long leaseMs) throws Exception {
        run(store, args, leaseMs, System.in, System.out);
    }
    static void run(OwnedProxySettings.Store store, String[] args, long leaseMs, InputStream source, PrintStream response) throws Exception {
        long parent = Long.parseLong(args[1]); long started = Long.parseLong(args[2]);
        var reads = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task, "proxy-settings-input"); thread.setDaemon(true); return thread; });
        var reader = new BufferedReader(new InputStreamReader(source, java.nio.charset.StandardCharsets.UTF_8));
        boolean acquired = false, restored = false;
        try (var settings = new OwnedProxySettings(store, Path.of(args[0]))) {
            acquired = true;
            Thread hook = new Thread(() -> { try { settings.restore(); } catch (Exception ignored) { } });
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                settings.restore();
                if (!args[3].equals("RECOVER")) settings.apply(args[3]);
                response.println("READY"); response.flush();
                while (ProcessHandle.of(parent).filter(process -> process.isAlive()
                        && process.info().startInstant().map(time -> time.toEpochMilli() == started).orElse(false)).isPresent()) {
                    String command;
                    try { command = reads.submit(reader::readLine).get(leaseMs, TimeUnit.MILLISECONDS); }
                    catch (TimeoutException exception) { break; }
                    if (command == null || command.equals("QUIT")) break;
                    if (!command.equals("PING")) throw new IOException("Invalid command");
                    if (!args[3].equals("RECOVER") && !settings.isApplied()) {
                        response.println("CHANGED"); response.flush(); break;
                    }
                    response.println("OK"); response.flush();
                }
            } finally {
                settings.restore();
                restored = true;
                Runtime.getRuntime().removeShutdownHook(hook);
            }
        } catch (Exception exception) {
            response.println("UNAVAILABLE");
            if (restored) response.println("RESTORED");
            else if (!acquired) response.println("NO_CHANGES");
            response.flush();
            System.exit(1);
        } finally { reads.shutdownNow(); }
        response.println("RESTORED"); response.flush();
    }
}
