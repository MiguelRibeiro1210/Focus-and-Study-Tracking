package com.focusdesktop.infrastructure.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.port.ApplicationCatalog;
import com.focusdesktop.application.usecase.ListApplicationsUseCase;
import com.focusdesktop.domain.model.TargetPlatform;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationCatalogTest {
    @TempDir Path directory;
    @Test void linuxDiscoversNativeAndPackagedAppsWithoutExecutingLaunchers() throws Exception {
        Path data = Files.createDirectories(directory.resolve("share/applications"));
        Path binary = directory.resolve("Actual Chat"); Files.write(binary, new byte[]{127, 'E', 'L', 'F', 1}); binary.toFile().setExecutable(true);
        Path alias = directory.resolve("chat-link"); Files.createSymbolicLink(alias, binary);
        entry(data, "chat.desktop", "Name=Native Chat\nExec=\"" + alias + "\" %U\n");
        entry(data, "chat-duplicate.desktop", "Name=Duplicate Chat\nExec=\"" + binary + "\"\n");
        entry(data, "browser-app.desktop", "Name=Browser Shortcut\nExec=\"" + binary + "\" --app-id=example\n");
        entry(data, "flatpak.desktop", "Name=Flatpak Chat\nExec=flatpak run org.example.Chat %U\nX-Flatpak=org.example.Chat\n");
        entry(data, "snap.desktop", "Name=Snap Chat\nExec=env BAMF_DESKTOP_FILE_HINT=ignored /snap/bin/chat %U\nX-SnapInstanceName=chat\n");
        entry(data, "unsafe.desktop", "Name=Manual Script\nExec=/bin/sh -c \"touch " + directory.resolve("must-not-exist") + "\"\n");
        entry(data, "hidden.desktop", "Name=Hidden Chat\nExec=ignored\nHidden=true\n");
        var scan = linux().scan();
        assertEquals(binary.toRealPath().toString(), find(scan, "Native Chat", "Duplicate Chat").target().value());
        assertEquals(1, scan.applications().stream().filter(app -> app.target() != null && binary.toString().equals(app.target().value())).count());
        assertEquals("flatpak:org.example.Chat", find(scan, "Flatpak Chat").target().value());
        assertEquals("snap:chat.chat", find(scan, "Snap Chat").target().value());
        assertFalse(find(scan, "Manual Script").selectable());
        assertFalse(find(scan, "Browser Shortcut").selectable());
        assertTrue(scan.applications().stream().noneMatch(app -> app.displayName().equals("Hidden Chat")));
        assertFalse(Files.exists(directory.resolve("must-not-exist")));
    }
    @Test void malformedAndOversizedEntriesDoNotDiscardHealthyProviders() throws Exception {
        Path data = Files.createDirectories(directory.resolve("share/applications"));
        entry(data, "bad.desktop", "Name=Unresolved Chat\nExec=\"unterminated\n");
        entry(data, "good.desktop", "Name=Healthy Chat\nX-Flatpak=org.example.Healthy\nExec=flatpak run org.example.Healthy\n");
        Files.writeString(data.resolve("large.desktop"), "x".repeat(70_000));
        var scan = linux().scan();
        assertTrue(scan.partial()); assertFalse(scan.warnings().isEmpty());
        assertTrue(find(scan, "Healthy Chat").selectable()); assertFalse(find(scan, "Unresolved Chat").selectable());
        assertThrows(IllegalArgumentException.class, () -> NativeApplicationCatalog.execTokens("env \"unterminated"));
    }
    @Test void nativeJsonPreservesNamesAndRejectsSharedOrInvalidTargets() {
        var catalog = new NativeApplicationCatalog("Windows", directory, Map.of(), args -> {
            assertTrue(args.contains("-EncodedCommand"));
            return "{\"applications\":[{\"displayName\":\"Native Chat\",\"source\":\"Windows app\",\"executable\":\"C:\\\\Apps\\\\chat.exe\"},"
                    + "{\"displayName\":\"Store Chat\",\"source\":\"Microsoft Store\",\"applicationId\":\"windows:Chat_123!Main\"},"
                    + "{\"displayName\":\"Shared host\",\"source\":\"Windows app\",\"executable\":\"C:\\\\Windows\\\\ApplicationFrameHost.exe\"}],\"warnings\":[\"Some apps unavailable\"]}";
        }, new ObjectMapper());
        var scan = catalog.scan();
        assertEquals(TargetPlatform.WINDOWS, scan.platform()); assertTrue(scan.partial());
        assertTrue(find(scan, "Native Chat").selectable()); assertFalse(find(scan, "Shared host").selectable());
        assertEquals("windows:Chat_123!Main", find(scan, "Store Chat").target().value());
        var mac = new NativeApplicationCatalog("Mac OS X", directory, Map.of(), args -> "{\"applications\":[{\"displayName\":\"Mac Chat\",\"source\":\"macOS app\",\"applicationId\":\"macos:com.example.Chat\"}],\"warnings\":[]}", new ObjectMapper()).scan();
        assertEquals("macos:com.example.Chat", find(mac, "Mac Chat").target().value());
    }
    @Test void cachesForSixtySecondsAndExplicitRefreshRescans() {
        var calls = new AtomicInteger(); Instant now = Instant.parse("2026-11-27T00:00:00Z");
        ApplicationCatalog catalog = () -> { calls.incrementAndGet(); return new ApplicationCatalog.Scan(TargetPlatform.LINUX, List.of(), false, List.of(), now); };
        var cached = new ListApplicationsUseCase(catalog, Clock.fixed(now.plusSeconds(59), ZoneOffset.UTC));
        cached.execute(false); cached.execute(false); assertEquals(1, calls.get());
        cached.execute(true); assertEquals(2, calls.get());
        var expired = new ListApplicationsUseCase(catalog, Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC));
        expired.execute(false); expired.execute(false); assertEquals(4, calls.get());
    }
    @Test void linuxProcessIdentityUsesPackageMetadataOrKernelLabelsOnly() throws Exception {
        Path process = Files.createDirectories(directory.resolve("123/root"));
        Files.writeString(process.resolve(".flatpak-info"), "[Application]\nname=org.example.Chat\n[Instance]\ninstance-id=ignored\n");
        assertEquals("flatpak:org.example.Chat", LinuxApplicationIdentity.read(directory, 123));
        Files.delete(process.resolve(".flatpak-info"));
        Path label = Files.createDirectories(directory.resolve("123/attr")).resolve("current");
        Files.writeString(label, "snap.chat.chat (enforce)\n");
        assertEquals("snap:chat.chat", LinuxApplicationIdentity.read(directory, 123));
        Files.writeString(label, "unconfined\n"); assertNull(LinuxApplicationIdentity.read(directory, 123));
        Files.writeString(label, "snap.chat.chat;other (enforce)\n"); assertNull(LinuxApplicationIdentity.read(directory, 123));
    }
    private NativeApplicationCatalog linux() {
        return new NativeApplicationCatalog("Linux", directory.resolve("home"), Map.of("XDG_DATA_HOME", directory.resolve("share").toString(), "XDG_DATA_DIRS", directory.resolve("absent").toString()), args -> { fail("No launcher may execute"); return ""; }, new ObjectMapper());
    }
    private static void entry(Path root, String name, String body) throws Exception { Files.writeString(root.resolve(name), "[Desktop Entry]\nType=Application\n" + body); }
    private static ApplicationCatalog.Entry find(ApplicationCatalog.Scan scan, String... names) {
        return scan.applications().stream().filter(app -> Arrays.asList(names).contains(app.displayName())).findFirst().orElseThrow();
    }
}
