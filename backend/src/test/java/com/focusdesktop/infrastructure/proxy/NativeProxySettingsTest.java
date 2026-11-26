package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NativeProxySettingsTest {
    @Test void linuxPreparesReversibleSettingsAndRefusesCustomProxyModes() {
        var values = new HashMap<>(Map.of("mode", "'none'", "autoconfig-url", "'previous'"));
        var settings = new NativeProxySettings("Linux", args -> {
            assertEquals("gsettings", args.get(0));
            if (args.get(1).equals("get")) return values.get(args.get(3));
            values.put(args.get(3), args.get(4)); return "";
        });
        var changes = settings.prepare("http://127.0.0.1:12345/pac/token");
        assertEquals(2, changes.size());
        changes.forEach(change -> settings.write(change.key(), change.applied()));
        assertEquals("'auto'", values.get("mode"));
        assertThrows(IllegalStateException.class, () -> settings.prepare("http://127.0.0.1:12345/pac/token"));
        assertThrows(IllegalArgumentException.class, () -> settings.read("arbitrary-key"));
    }
    @Test void windowsUsesFixedUserRegistryCommandsAndPreservesExistingProxy() {
        var commands = new ArrayList<List<String>>();
        var settings = new NativeProxySettings("Windows 11", args -> {
            commands.add(args);
            return args.get(args.size() - 1).contains("$v.ProxyEnable") ? "OFF" : "__ABSENT__";
        });
        var changes = settings.prepare("http://127.0.0.1:12345/pac/token");
        assertEquals("__ABSENT__", changes.get(0).before());
        assertEquals("windows-pac", changes.get(0).key());
        assertTrue(commands.stream().allMatch(args -> args.get(0).equals("powershell.exe") && args.contains("-NonInteractive")));
        var existing = new NativeProxySettings("Windows", args -> args.get(args.size() - 1).contains("$v.ProxyEnable") ? "ON" : "__ABSENT__");
        assertThrows(IllegalStateException.class, () -> existing.prepare("http://127.0.0.1:12345/pac/token"));
    }
    @Test void macPreservesManualProxyAndRejectsUnrecognizedServiceKeys() {
        var settings = new NativeProxySettings("Mac OS X", args -> args.contains("-listallnetworkservices")
                ? "An asterisk denotes disabled service\nWi-Fi\n" : "Enabled: Yes\n");
        assertThrows(IllegalStateException.class, () -> settings.prepare("http://127.0.0.1:12345/pac/token"));
        assertThrows(IllegalArgumentException.class, () -> settings.read("windows-pac"));
    }
}
