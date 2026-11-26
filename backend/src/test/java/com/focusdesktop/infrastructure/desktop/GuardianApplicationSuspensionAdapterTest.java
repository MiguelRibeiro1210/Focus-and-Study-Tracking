package com.focusdesktop.infrastructure.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.port.ActiveApplicationPort;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuardianApplicationSuspensionAdapterTest {
    @TempDir Path directory;

    @Test
    void recoversAConfirmedRestrictionAfterTheGuardianIsKilled() throws Exception {
        var mapper = new ObjectMapper();
        try (var fixture = new Fixture(); var adapter = new ClosingAdapter(directory, mapper)) {
            var observation = PlatformActiveApplicationAdapter.lookupProcess(fixture.process.pid()).orElseThrow();
            Process guardian = new ProcessBuilder(adapter.value.launcher()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                var output = new BufferedReader(new InputStreamReader(guardian.getInputStream()));
                assertEquals("READY", fixture.reader.submit(output::readLine).get(20, TimeUnit.SECONDS));
                var input = new BufferedWriter(new OutputStreamWriter(guardian.getOutputStream()));
                input.write(mapper.writeValueAsString(new ApplicationSuspensionGuardian.Command("SUSPEND", observation.pid(),
                        observation.startedAt().toEpochMilli()))); input.newLine(); input.flush();
                assertEquals("OK", fixture.reader.submit(output::readLine).get(5, TimeUnit.SECONDS));
                guardian.destroyForcibly(); assertTrue(guardian.waitFor(5, TimeUnit.SECONDS));
                adapter.value.recover();
                fixture.ping();
                assertFalse(adapter.value.isSuspended(observation));
            } finally { guardian.destroyForcibly(); }
        }
    }

    @Test
    void confirmsSuspensionAndRestoresTheOwnedChildOnReleaseAndShutdown() throws Exception {
        var mapper = new ObjectMapper();
        try (var fixture = new Fixture(); var adapter = new ClosingAdapter(directory, mapper)) {
            var observation = PlatformActiveApplicationAdapter.lookupProcess(fixture.process.pid()).orElseThrow();
            adapter.value.suspend(observation);
            assertTrue(adapter.value.isSuspended(observation));
            adapter.value.resume(observation);
            assertFalse(adapter.value.isSuspended(observation));
            fixture.ping();
            adapter.value.suspend(observation);
            adapter.value.close();
            fixture.ping();
        }
    }

    @Test
    void refusesStaleProcessIdentityWithoutSuspendingTheFixture() throws Exception {
        try (var fixture = new Fixture(); var adapter = new ClosingAdapter(directory, new ObjectMapper())) {
            var observed = PlatformActiveApplicationAdapter.lookupProcess(fixture.process.pid()).orElseThrow();
            var stale = new ActiveApplicationPort.Observation(observed.pid(), observed.startedAt().minusSeconds(1), observed.identity());
            assertThrows(ActiveApplicationPort.Unavailable.class, () -> adapter.value.suspend(stale));
            assertFalse(adapter.value.isSuspended(observed)); fixture.ping();
        }
    }

    private static final class ClosingAdapter implements AutoCloseable {
        private final GuardianApplicationSuspensionAdapter value;
        private ClosingAdapter(Path directory, ObjectMapper mapper) { value = new GuardianApplicationSuspensionAdapter(directory.toString(), mapper); }
        @Override public void close() { value.close(); }
    }
    private static final class Fixture implements AutoCloseable {
        private final Process process;
        private final BufferedReader output;
        private final BufferedWriter input;
        private final ExecutorService reader = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task); thread.setDaemon(true); return thread; });
        private Fixture() throws Exception {
            process = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(), "-cp", System.getProperty("java.class.path"),
                    IdleChild.class.getName()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            output = new BufferedReader(new InputStreamReader(process.getInputStream()));
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            assertEquals("READY", read());
        }
        private void ping() throws Exception { input.write("PING\n"); input.flush(); assertEquals("PONG", read()); }
        private String read() throws Exception { return reader.submit(output::readLine).get(5, TimeUnit.SECONDS); }
        @Override public void close() throws Exception { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); reader.shutdownNow(); }
    }
    public static class IdleChild {
        public static void main(String[] args) throws Exception {
            System.out.println("READY"); System.out.flush();
            var reader = new BufferedReader(new InputStreamReader(System.in));
            while (reader.readLine() != null) { System.out.println("PONG"); System.out.flush(); }
        }
    }
}
