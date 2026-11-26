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
        run(new NativeProxySettings(), args, 15000);
    }
    static void run(OwnedProxySettings.Store store, String[] args, long leaseMs) throws Exception {
        long parent = Long.parseLong(args[1]); long started = Long.parseLong(args[2]);
        var reads = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task, "proxy-settings-input"); thread.setDaemon(true); return thread; });
        var reader = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
        try (var settings = new OwnedProxySettings(store, Path.of(args[0]))) {
            Thread hook = new Thread(() -> { try { settings.restore(); } catch (Exception ignored) { } });
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                settings.restore();
                if (!args[3].equals("RECOVER")) settings.apply(args[3]);
                System.out.println("READY"); System.out.flush();
                while (ProcessHandle.of(parent).filter(process -> process.isAlive()
                        && process.info().startInstant().map(time -> time.toEpochMilli() == started).orElse(false)).isPresent()) {
                    String command;
                    try { command = reads.submit(reader::readLine).get(leaseMs, TimeUnit.MILLISECONDS); }
                    catch (TimeoutException exception) { break; }
                    if (command == null || command.equals("QUIT")) break;
                    if (!command.equals("PING")) throw new IOException("Invalid command");
                    if (!args[3].equals("RECOVER") && !settings.isApplied()) {
                        System.out.println("CHANGED"); System.out.flush(); break;
                    }
                    System.out.println("OK"); System.out.flush();
                }
            } finally {
                settings.restore();
                Runtime.getRuntime().removeShutdownHook(hook);
            }
        } catch (Exception exception) {
            System.out.println("UNAVAILABLE"); System.out.flush();
            System.exit(1);
        } finally { reads.shutdownNow(); }
    }
}
