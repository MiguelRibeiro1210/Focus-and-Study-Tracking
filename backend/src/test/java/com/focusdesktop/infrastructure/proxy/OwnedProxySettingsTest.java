package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnedProxySettingsTest {
    @TempDir Path directory;
    private final Map<String, String> values = new HashMap<>(Map.of("url", "original", "mode", "none"));
    private OwnedProxySettings.Store store() {
        return new OwnedProxySettings.Store() {
            public List<OwnedProxySettings.Change> prepare(String url) {
                return List.of(new OwnedProxySettings.Change("url", values.get("url"), url),
                        new OwnedProxySettings.Change("mode", values.get("mode"), "auto"));
            }
            public String read(String key) { return values.get(key); }
            public void write(String key, String value) { values.put(key, value); }
        };
    }
    @Test void restoresOwnedChangesAndPreservesConcurrentUserEdits() throws Exception {
        Path journal = directory.resolve("settings.json");
        try (var settings = new OwnedProxySettings(store(), journal)) {
            settings.apply("local-pac"); assertEquals("auto", values.get("mode"));
            values.put("url", "user-new-value"); settings.restore();
            assertEquals("auto", values.get("mode")); assertEquals("user-new-value", values.get("url"));
            assertFalse(Files.exists(journal));
        }
    }
    @Test void recoversDurableSnapshotAndRefusesASecondOwner() throws Exception {
        Path journal = directory.resolve("settings.json");
        Files.writeString(journal, "[{\"key\":\"mode\",\"before\":\"none\",\"applied\":\"auto\"}]");
        values.put("mode", "auto");
        try (var settings = new OwnedProxySettings(store(), journal)) {
            assertThrows(Exception.class, () -> new OwnedProxySettings(store(), journal));
            assertTrue(Files.exists(journal)); settings.restore(); assertEquals("none", values.get("mode"));
        }
    }
    @Test void retainsJournalWhenRestorationCannotBeConfirmed() throws Exception {
        Path journal = directory.resolve("settings.json");
        var denied = new java.util.concurrent.atomic.AtomicBoolean();
        var original = store();
        var failing = new OwnedProxySettings.Store() {
            public List<OwnedProxySettings.Change> prepare(String url) { return original.prepare(url); }
            public String read(String key) { return original.read(key); }
            public void write(String key, String value) {
                if (denied.get()) throw new IllegalStateException("Denied");
                original.write(key, value);
            }
        };
        try (var settings = new OwnedProxySettings(failing, journal)) {
            settings.apply("local-pac");
            denied.set(true);
            assertThrows(java.io.IOException.class, settings::restore);
            assertTrue(Files.exists(journal));
            denied.set(false);
        }
        assertEquals("original", values.get("url"));
    }
}
