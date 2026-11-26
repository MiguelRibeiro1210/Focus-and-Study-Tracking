package com.focusdesktop.infrastructure.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;

/** Independent Unix guardian. Never starts Spring or exposes a network listener. */
public final class ApplicationSuspensionGuardian {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CRITICAL = Set.of("systemd", "gnome-shell", "plasmashell", "kwin_wayland",
            "hyprland", "sway", "xorg", "xwayland", "loginwindow", "windowserver", "focusdesktop", "focusdesktop.exe");
    private final Path journal;
    private final Map<Long, Instance> owned = new LinkedHashMap<>();
    private volatile boolean ownsJournal;

    private ApplicationSuspensionGuardian(Path journal) { this.journal = journal; }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-check")) {
            try (var script = ApplicationSuspensionGuardian.class.getResourceAsStream("/desktop/windows-suspension-guardian.ps1")) {
                if (script == null) throw new IllegalStateException("Missing Windows guardian resource");
                JSON.readTree("{\"version\":1}");
                System.out.println("Packaged application guardian is available."); return;
            }
        }
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) throw new IllegalStateException("Unix guardian only");
        Path journal = Path.of(args[0]);
        long parentPid = Long.parseLong(args[1]); long parentStarted = Long.parseLong(args[2]);
        long lease = args.length > 3 ? Math.max(500, Math.min(15000, Long.parseLong(args[3]))) : 15000;
        var guardian = new ApplicationSuspensionGuardian(journal);
        ExecutorService input = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "guardian-input"); thread.setDaemon(true); return thread; });
        Runtime.getRuntime().addShutdownHook(new Thread(guardian::restoreAllQuietly));
        // The input reader is daemon-owned: closing it while readLine is pending would
        // block restoration on its monitor when a lease expires.
        var reader = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
        Files.createDirectories(journal.getParent());
        try (var lockChannel = FileChannel.open(journal.resolveSibling(journal.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = lockChannel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Another guardian owns recovery");
            guardian.ownsJournal = true;
            try {
                guardian.recover();
                System.out.println("READY"); System.out.flush();
                while (sameInstance(new Instance(parentPid, parentStarted))) {
                    String line;
                    try { line = input.submit(reader::readLine).get(lease, TimeUnit.MILLISECONDS); }
                    catch (TimeoutException exception) { break; }
                    if (line == null) break;
                    try {
                        Command command = JSON.readValue(line, Command.class);
                        if ("QUIT".equals(command.action())) { guardian.restoreAll(); reply("OK"); break; }
                        var instance = new Instance(command.pid(), command.startedAtEpochMs());
                        switch (command.action()) {
                            case "PING" -> reply("OK");
                            case "SUSPEND" -> { guardian.suspend(instance); reply("OK"); }
                            case "RESUME" -> { guardian.resume(instance); reply("OK"); }
                            case "STATE" -> reply(guardian.isOwnedStopped(instance) ? "SUSPENDED" : "RELEASED");
                            case "RELEASE_ALL" -> { guardian.restoreAll(); reply("OK"); }
                            default -> reply("ERROR");
                        }
                    } catch (Refused exception) { reply(exception.getMessage()); }
                    catch (Exception exception) { reply("ERROR"); }
                }
            } finally { guardian.restoreAllQuietly(); guardian.ownsJournal = false; input.shutdownNow(); }
        }
    }

    private synchronized void suspend(Instance instance) throws Exception {
        if (owned.containsValue(instance)) {
            if (isOwnedStopped(instance)) return;
            owned.remove(instance.pid()); persist();
        }
        validateTarget(instance);
        if (stopped(instance.pid())) throw new Refused("ALREADY_STOPPED");
        owned.put(instance.pid(), instance); persist();
        try {
            requireSame(instance); signal("STOP", instance.pid());
            if (!waitForState(instance, true)) throw new IllegalStateException("Suspension was not confirmed");
        } catch (Exception exception) {
            try { resume(instance); } catch (Exception ignored) { }
            throw exception;
        }
    }

    private synchronized void resume(Instance instance) throws Exception {
        if (!instance.equals(owned.get(instance.pid()))) return;
        if (sameInstance(instance) && stopped(instance.pid())) {
            requireSame(instance); signal("CONT", instance.pid());
            if (!waitForState(instance, false)) throw new IllegalStateException("Restoration was not confirmed");
        }
        owned.remove(instance.pid()); persist();
    }

    private synchronized boolean isOwnedStopped(Instance instance) throws Exception {
        if (!instance.equals(owned.get(instance.pid()))) return false;
        if (!sameInstance(instance) || !stopped(instance.pid())) { owned.remove(instance.pid()); persist(); return false; }
        return true;
    }

    private synchronized void recover() throws Exception {
        if (!Files.exists(journal)) return;
        if (Files.isSymbolicLink(journal) || Files.size(journal) > 1_048_576) throw new IOException("Invalid recovery journal");
        Instance[] entries = JSON.readValue(Files.readAllBytes(journal), Instance[].class);
        for (Instance instance : entries) {
            if (instance.pid() > 1 && sameInstance(instance)) {
                validateTarget(instance); owned.put(instance.pid(), instance);
            }
        }
        restoreAll();
    }

    private synchronized void restoreAll() throws Exception {
        Exception failure = null;
        for (Instance instance : List.copyOf(owned.values())) {
            try { resume(instance); } catch (Exception exception) { failure = exception; }
        }
        if (failure != null) throw failure;
        if (owned.isEmpty()) persist();
    }
    private synchronized void restoreAllQuietly() { if (ownsJournal) try { restoreAll(); } catch (Exception ignored) { } }

    private synchronized void persist() throws Exception {
        Files.createDirectories(journal.getParent());
        if (Files.isSymbolicLink(journal)) throw new IOException("Invalid recovery journal");
        if (owned.isEmpty()) { Files.deleteIfExists(journal); return; }
        Path temporary = Files.createTempFile(journal.getParent(), "suspension-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.write(temporary, JSON.writeValueAsBytes(owned.values()));
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void validateTarget(Instance instance) {
        requireSame(instance);
        if (instance.pid() <= 1 || instance.pid() == ProcessHandle.current().pid()
                || ProcessAncestors.selfAndAncestors().stream().anyMatch(parent -> parent.pid() == instance.pid())) throw new Refused("PROTECTED");
        var info = ProcessHandle.of(instance.pid()).orElseThrow().info();
        String user = ProcessHandle.current().info().user().orElseThrow();
        if (!user.equals(info.user().orElseThrow())) throw new Refused("PERMISSION");
        String executable = info.command().orElseThrow();
        String name = executable.substring(executable.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (CRITICAL.contains(name)) throw new Refused("PROTECTED");
    }
    private static boolean sameInstance(Instance instance) {
        return ProcessHandle.of(instance.pid()).filter(ProcessHandle::isAlive)
                .flatMap(process -> process.info().startInstant()).map(start -> start.toEpochMilli() == instance.startedAtEpochMs()).orElse(false);
    }
    private static void requireSame(Instance instance) { if (!sameInstance(instance)) throw new Refused("EXITED"); }
    private static boolean stopped(long pid) throws Exception {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
            String stat = Files.readString(Path.of("/proc", Long.toString(pid), "stat"));
            int state = stat.lastIndexOf(") ") + 2;
            return stat.charAt(state) == 'T' || stat.charAt(state) == 't';
        }
        return NativeCommands.execute(List.of("/bin/ps", "-o", "stat=", "-p", Long.toString(pid))).startsWith("T");
    }
    private static void signal(String signal, long pid) { NativeCommands.execute(List.of("/bin/kill", "-" + signal, Long.toString(pid))); }
    private static boolean waitForState(Instance instance, boolean stopped) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (!sameInstance(instance)) return !stopped;
            if (stopped(instance.pid()) == stopped) return true;
            Thread.sleep(10);
        }
        return false;
    }
    private static void reply(String value) { System.out.println(value); System.out.flush(); }
    public record Command(String action, long pid, long startedAtEpochMs) { }
    public record Instance(long pid, long startedAtEpochMs) { }
    private static class Refused extends RuntimeException { Refused(String code) { super(code); } }
}
