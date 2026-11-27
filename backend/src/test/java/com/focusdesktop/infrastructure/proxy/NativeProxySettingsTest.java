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
            return ConnectionPowerShell.script(args).contains("$v.ProxyEnable") ? "OFF" : "__ABSENT__";
        });
        var changes = settings.prepare("http://127.0.0.1:12345/pac/token");
        assertEquals("__ABSENT__", changes.get(0).before());
        assertEquals("windows-pac", changes.get(0).key());
        assertTrue(commands.stream().allMatch(args -> args.get(0).equals("powershell.exe") && args.contains("-NonInteractive")));
        var existing = new NativeProxySettings("Windows", args -> ConnectionPowerShell.script(args).contains("$v.ProxyEnable") ? "ON" : "__ABSENT__");
        assertThrows(IllegalStateException.class, () -> existing.prepare("http://127.0.0.1:12345/pac/token"));
    }
    @Test void macPreservesManualProxyAndRejectsUnrecognizedServiceKeys() {
        var settings = new NativeProxySettings("Mac OS X", args -> args.contains("-listallnetworkservices")
                ? "An asterisk denotes disabled service\nWi-Fi\n" : "Enabled: Yes\n");
        assertThrows(IllegalStateException.class, () -> settings.prepare("http://127.0.0.1:12345/pac/token"));
        assertThrows(IllegalArgumentException.class, () -> settings.read("windows-pac"));
    }
    @Test void macRestoresAnEmptyPacWithTheNativeEmptyRepresentationAndSwitchOff() {
        var calls = new ArrayList<List<String>>();
        String[] url = {"http://127.0.0.1:12345/pac/owned"}; boolean[] enabled = {true};
        var settings = new NativeProxySettings("Mac OS X", args -> {
            calls.add(args);
            switch (args.get(1)) {
                case "-setautoproxyurl" -> {
                    assertFalse(args.get(3).isEmpty(), "networksetup rejects an empty URL argument");
                    url[0] = args.get(3); enabled[0] = true;
                }
                case "-setautoproxystate" -> enabled[0] = args.get(3).equals("on");
                case "-getautoproxyurl" -> { return "URL: " + url[0] + "\nEnabled: " + (enabled[0] ? "Yes" : "No") + "\n"; }
                default -> fail("Unexpected native command");
            }
            return "";
        });
        settings.write("mac-service:USB Ethernet", "{\"url\":\"\",\"enabled\":false}");
        assertEquals("\"\"", calls.get(0).get(3));
        assertEquals("{\"url\":\"\",\"enabled\":false}", settings.read("mac-service:USB Ethernet"));
        settings.write("mac-service:USB Ethernet", "{\"url\":\"http://previous.example/proxy.pac\",\"enabled\":false}");
        assertEquals("{\"url\":\"http://previous.example/proxy.pac\",\"enabled\":false}", settings.read("mac-service:USB Ethernet"));
    }
}
