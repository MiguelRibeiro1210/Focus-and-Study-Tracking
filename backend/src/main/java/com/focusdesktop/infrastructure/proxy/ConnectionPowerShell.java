package com.focusdesktop.infrastructure.proxy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Avoid Windows command-line reinterpretation of nested JSON and interop quotes. */
final class ConnectionPowerShell {
    static List<String> command(String script) {
        return List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                Base64.getEncoder().encodeToString(("$ErrorActionPreference='Stop'; " + script).getBytes(StandardCharsets.UTF_16LE)));
    }

    static String script(List<String> command) {
        return new String(Base64.getDecoder().decode(command.get(command.size() - 1)), StandardCharsets.UTF_16LE);
    }
}
