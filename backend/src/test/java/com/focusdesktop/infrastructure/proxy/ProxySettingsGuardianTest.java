package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxySettingsGuardianTest {
    @TempDir Path directory;
    @Test void restoresOwnedSettingsOnEofAndLeaseExpiryWithoutTouchingLiveOsSettings() throws Exception {
        for (boolean eof : List.of(true, false)) {
            Path value = directory.resolve("value"); Files.writeString(value, "original");
            var parent = ProcessHandle.current();
            var process = new ProcessBuilder(parent.info().command().orElseThrow(), "-cp", System.getProperty("java.class.path"),
                    FakeGuardian.class.getName(), directory.resolve("journal.json").toString(), Long.toString(parent.pid()),
                    Long.toString(parent.info().startInstant().orElseThrow().toEpochMilli()), "local-pac", value.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            var reads = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task); thread.setDaemon(true); return thread; });
            try {
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                assertEquals("READY", reads.submit(reader::readLine).get(5, TimeUnit.SECONDS));
                assertEquals("local-pac", Files.readString(value));
                if (eof) process.getOutputStream().close();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
                assertEquals("original", Files.readString(value));
                assertFalse(Files.exists(directory.resolve("journal.json")));
            } finally { process.destroyForcibly(); reads.shutdownNow(); }
        }
    }
    public static class FakeGuardian {
        public static void main(String[] args) throws Exception {
            Path value = Path.of(args[4]);
            ProxySettingsGuardian.run(new OwnedProxySettings.Store() {
                public List<OwnedProxySettings.Change> prepare(String pac) { return List.of(new OwnedProxySettings.Change("url", read("url"), pac)); }
                public String read(String key) { try { return Files.readString(value); } catch (IOException e) { throw new UncheckedIOException(e); } }
                public void write(String key, String next) { try { Files.writeString(value, next); } catch (IOException e) { throw new UncheckedIOException(e); } }
            }, args, args.length > 5 ? Long.parseLong(args[5]) : 1500);
        }
    }
}
