package com.focusdesktop.infrastructure.desktop;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public final class NativePowerShell {
    private NativePowerShell() { }
    public static List<String> command(String script) {
        return List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                Base64.getEncoder().encodeToString(("$ErrorActionPreference='Stop'; [Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false); " + script).getBytes(StandardCharsets.UTF_16LE)));
    }
    public static String resource(String name) {
        try (var stream = NativePowerShell.class.getResourceAsStream("/desktop/" + name)) {
            if (stream == null) throw new IllegalStateException("Native application metadata helper is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot read application metadata helper", exception); }
    }
}
