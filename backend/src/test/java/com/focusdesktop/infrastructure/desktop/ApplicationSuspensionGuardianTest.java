package com.focusdesktop.infrastructure.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class ApplicationSuspensionGuardianTest {
    @TempDir Path directory;

    @Test
    void anotherGuardianCannotEraseTheActiveOwnersRecoveryJournal() throws Exception {
        try (var fixture = new Fixture(directory, 15000)) {
            assertEquals("OK", fixture.request("SUSPEND", fixture.started));
            var second = new ProcessBuilder(fixture.guardianCommand).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                assertTrue(second.waitFor(5, TimeUnit.SECONDS));
                assertNotEquals(0, second.exitValue());
                assertTrue(Files.exists(directory.resolve("application-suspensions.json")));
                assertEquals("SUSPENDED", fixture.request("STATE", fixture.started));
            } finally { second.destroyForcibly(); }
        }
    }

    @Test
    void suspendsOnlyAnOwnedFixtureAndRestoresOnParentEof() throws Exception {
        try (var fixture = new Fixture(directory, 15000)) {
            assertEquals("OK", fixture.request("SUSPEND", fixture.started));
            assertTrue(fixture.stopped());
            fixture.input.close();
            assertTrue(fixture.guardian.waitFor(5, TimeUnit.SECONDS));
            assertFalse(fixture.stopped());
        }
    }

    @Test
    void expiresLeaseAndRejectsReusedIdentityAndAlreadyStoppedProcesses() throws Exception {
        try (var fixture = new Fixture(directory, 2000)) {
            assertEquals("EXITED", fixture.request("SUSPEND", fixture.started - 1));
            fixture.signal("STOP");
            assertEquals("ALREADY_STOPPED", fixture.request("SUSPEND", fixture.started));
            assertEquals("OK", fixture.request("RESUME", fixture.started));
            assertTrue(fixture.stopped()); // It does not own this external stop.
            fixture.signal("CONT");
            assertEquals("OK", fixture.request("SUSPEND", fixture.started));
            assertTrue(fixture.guardian.waitFor(5, TimeUnit.SECONDS));
            assertFalse(fixture.stopped());
        }
    }

    @Test
    void recoversJournalAfterForcedGuardianExitWithoutTouchingDifferentInstances() throws Exception {
        try (var fixture = new Fixture(directory, 15000)) {
            assertEquals("OK", fixture.request("SUSPEND", fixture.started));
            fixture.guardian.destroyForcibly(); assertTrue(fixture.guardian.waitFor(5, TimeUnit.SECONDS));
            assertTrue(fixture.stopped());
            fixture.startGuardian(15000);
            assertFalse(fixture.stopped());
            assertFalse(Files.exists(directory.resolve("application-suspensions.json")));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Path directory;
        private final Process child;
        private final long started;
        private Process guardian;
        private List<String> guardianCommand;
        private BufferedWriter input;
        private BufferedReader output;
        private final ExecutorService reads = Executors.newSingleThreadExecutor(task -> { Thread t = new Thread(task); t.setDaemon(true); return t; });
        private Fixture(Path directory, long lease) throws Exception {
            this.directory = directory;
            child = new ProcessBuilder("/bin/sleep", "60").start();
            started = child.info().startInstant().orElseThrow().toEpochMilli();
            try { startGuardian(lease); } catch (Exception exception) { child.destroyForcibly(); throw exception; }
        }
        private void startGuardian(long lease) throws Exception {
            var parent = ProcessHandle.current();
            guardianCommand = List.of(parent.info().command().orElseThrow(), "-cp", System.getProperty("java.class.path"),
                    ApplicationSuspensionGuardian.class.getName(), directory.resolve("application-suspensions.json").toString(),
                    Long.toString(parent.pid()), Long.toString(parent.info().startInstant().orElseThrow().toEpochMilli()), Long.toString(lease));
            guardian = new ProcessBuilder(guardianCommand)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            input = new BufferedWriter(new OutputStreamWriter(guardian.getOutputStream()));
            output = new BufferedReader(new InputStreamReader(guardian.getInputStream()));
            assertEquals("READY", read());
        }
        private String request(String action, long start) throws Exception {
            input.write(new ObjectMapper().writeValueAsString(new ApplicationSuspensionGuardian.Command(action, child.pid(), start)));
            input.newLine(); input.flush(); return read();
        }
        private String read() throws Exception { return reads.submit(output::readLine).get(5, TimeUnit.SECONDS); }
        private void signal(String value) { NativeCommands.execute(List.of("/bin/kill", "-" + value, Long.toString(child.pid()))); }
        private boolean stopped() throws Exception {
            if (!child.isAlive()) return false;
            if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
                String stat = Files.readString(Path.of("/proc", Long.toString(child.pid()), "stat"));
                return stat.charAt(stat.lastIndexOf(") ") + 2) == 'T';
            }
            return NativeCommands.execute(List.of("/bin/ps", "-o", "stat=", "-p", Long.toString(child.pid()))).startsWith("T");
        }
        @Override public void close() throws Exception {
            try { if (input != null) input.close(); if (guardian != null) guardian.waitFor(5, TimeUnit.SECONDS); }
            finally { if (child.isAlive()) signal("CONT"); child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); reads.shutdownNow(); }
        }
    }
}
