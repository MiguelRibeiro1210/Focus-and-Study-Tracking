package com.focusdesktop.infrastructure.desktop;

import com.focusdesktop.domain.model.ApplicationIds;
import com.focusdesktop.domain.model.TargetPlatform;
import java.nio.file.*;
import java.util.regex.Pattern;

final class LinuxApplicationIdentity {
    static String read(long pid) { return read(Path.of("/proc"), pid); }
    static String read(Path proc, long pid) {
        if (pid <= 0) return null;
        Path process = proc.resolve(Long.toString(pid));
        try {
            Path info = process.resolve("root/.flatpak-info");
            if (Files.isRegularFile(info) && Files.size(info) <= 65_536) {
                var metadata = NativeApplicationCatalog.desktopLike(info, "Application");
                if (metadata.containsKey("name")) return ApplicationIds.validate(TargetPlatform.LINUX, "flatpak:" + metadata.get("name"));
            }
            Path label = process.resolve("attr/current");
            if (Files.isRegularFile(label)) {
                String value;
                try (var input = Files.newInputStream(label)) { value = new String(input.readNBytes(1024), java.nio.charset.StandardCharsets.UTF_8).trim(); }
                var matcher = Pattern.compile("^snap\\.([a-z0-9_-]+\\.[A-Za-z0-9_-]+)(?: \\(enforce\\)| \\(complain\\))?$").matcher(value);
                if (matcher.matches()) return ApplicationIds.validate(TargetPlatform.LINUX, "snap:" + matcher.group(1));
            }
        } catch (java.io.IOException | RuntimeException ignored) { }
        return null;
    }
}
