package com.focusdesktop.infrastructure.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.focusdesktop.application.port.ActiveApplicationPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeCommandsTest {
    @Test
    void capturesOwnedCommandOutputAndRejectsFailedCommandsWithoutStderrLeakage() {
        assertEquals("456", NativeCommands.execute(command("success")));
        var failure = assertThrows(ActiveApplicationPort.Unavailable.class, () -> NativeCommands.execute(command("failure")));
        assertFalse(failure.getMessage().contains("PRIVATE"));
    }

    @Test
    void boundsExecutionTimeAndCapturedOutput() {
        long started = System.nanoTime();
        assertThrows(ActiveApplicationPort.Unavailable.class, () -> NativeCommands.execute(command("timeout")));
        assertTrue(java.time.Duration.ofNanos(System.nanoTime() - started).toSeconds() < 6);
        assertThrows(ActiveApplicationPort.Unavailable.class, () -> NativeCommands.execute(command("large")));
    }

    @Test
    void explicitConnectionDiagnosticsCaptureBoundedErrorsAndDrainThePipe() {
        var failure = assertThrows(ActiveApplicationPort.Unavailable.class, () -> NativeCommands.execute(command("failure"), true));
        assertTrue(failure.getMessage().contains("PRIVATE RAW ERROR"));
        var large = assertThrows(ActiveApplicationPort.Unavailable.class, () -> NativeCommands.execute(command("large-error"), true));
        assertTrue(large.getMessage().contains("Desktop query failed"));
        assertTrue(large.getMessage().length() < 4300);
    }

    private static List<String> command(String mode) {
        return List.of(ProcessHandle.current().info().command().orElseThrow(), "-cp", System.getProperty("java.class.path"),
                Fixture.class.getName(), mode);
    }

    public static class Fixture {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "success" -> System.out.print("456");
                case "failure" -> { System.err.print("PRIVATE RAW ERROR"); System.exit(1); }
                case "timeout" -> Thread.sleep(10000);
                case "large" -> System.out.write(new byte[1_048_577]);
                case "large-error" -> { System.err.print("E".repeat(100_000)); System.exit(1); }
                default -> throw new IllegalArgumentException();
            }
        }
    }
}
