package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WindowsBrowserPoliciesTest {
    private static final String PAC = "http://127.0.0.1:12345/pac/00000000-0000-0000-0000-000000000001";
    @Test void preparesUserPoliciesForEverySupportedBrowserAndTheSystemPac() {
        var commands = new ArrayList<List<String>>();
        var store = new WindowsBrowserPolicies(args -> {
            commands.add(args); String script = ConnectionPowerShell.script(args);
            return script.contains("$v.ProxyEnable") ? "OFF" : script.contains("$v.AutoConfigURL") ? "__ABSENT__" : "";
        });
        var changes = store.prepare(PAC);
        assertEquals(6, changes.size());
        assertEquals(Set.of("windows-pac", "windows-policy:brave", "windows-policy:chrome", "windows-policy:chromium", "windows-policy:edge", "windows-policy:firefox"),
                changes.stream().map(OwnedProxySettings.Change::key).collect(java.util.stream.Collectors.toSet()));
        changes.forEach(change -> store.write(change.key(), change.applied()));
        assertTrue(commands.stream().allMatch(command -> command.get(0).equals("powershell.exe") && command.contains("-NoProfile") && command.contains("-NonInteractive")));
        String scripts = commands.stream().map(ConnectionPowerShell::script).toList().toString();
        assertTrue(scripts.contains("BraveSoftware\\Brave")); assertTrue(scripts.contains("Microsoft\\Edge"));
        assertTrue(scripts.contains("-PropertyType DWord -Value 1"));
        assertThrows(IllegalArgumentException.class, () -> store.write("windows-policy:arbitrary", "{}"));
        assertThrows(IllegalArgumentException.class, () -> store.prepare("http://remote.example/pac"));
        assertThrows(IllegalArgumentException.class, () -> store.write("windows-policy:chrome", "{\"ProxyMode\":\"direct\"}"));
    }
    @Test void refusesMachinePolicyBeforeAnyRegistryWrite() {
        var commands = new ArrayList<String>();
        var store = new WindowsBrowserPolicies(args -> {
            String script = ConnectionPowerShell.script(args); commands.add(script);
            return script.contains("$v.ProxyEnable") ? "OFF" : script.contains("$v.AutoConfigURL") ? "__ABSENT__" : script.contains("HKLM:") ? "CONFLICT" : "";
        });
        assertThrows(IllegalStateException.class, () -> store.prepare(PAC));
        assertTrue(commands.stream().noneMatch(command -> command.contains("New-Item") || command.contains("Set-Item")));
    }
    @Test void batchesRegistrySnapshotsAndValidatesAllWritesBeforeLaunchingPowerShell() {
        var commands = new ArrayList<String>();
        var store = new WindowsBrowserPolicies(args -> {
            commands.add(ConnectionPowerShell.script(args));
            return "{\"windows-pac\":\"__ABSENT__\",\"windows-policy:chrome\":\"__ABSENT__\"}";
        });
        assertEquals(Map.of("windows-pac", "__ABSENT__", "windows-policy:chrome", "__ABSENT__"),
                store.readAll(List.of("windows-pac", "windows-policy:chrome")));
        assertEquals(1, commands.size()); commands.clear();
        store.writeAll(Map.of("windows-policy:chrome", BrowserProxyPolicy.parse(BrowserProxyPolicy.chromium(PAC)).get("ProxySettings").toString(),
                "windows-policy:edge", BrowserProxyPolicy.ABSENT));
        assertEquals(1, commands.size()); commands.clear();
        var invalid = new LinkedHashMap<String, String>();
        invalid.put("windows-policy:edge", BrowserProxyPolicy.ABSENT); invalid.put("windows-policy:chrome", "{}");
        assertThrows(IllegalArgumentException.class, () -> store.writeAll(invalid));
        assertTrue(commands.isEmpty(), "An invalid batch must not apply its earlier valid changes");
    }
    @Test void encodedBatchPreservesJsonAndNativeInteropQuotes() {
        var commands = new ArrayList<List<String>>();
        var store = new WindowsBrowserPolicies(args -> { commands.add(args); return ""; });
        String policy = BrowserProxyPolicy.parse(BrowserProxyPolicy.chromium(PAC)).get("ProxySettings").toString();
        store.writeAll(Map.of("windows-pac", PAC, "windows-policy:edge", policy));
        assertEquals(1, commands.size());
        var command = commands.get(0);
        assertTrue(command.contains("-EncodedCommand")); assertFalse(command.contains("-Command"));
        String script = ConnectionPowerShell.script(command);
        assertTrue(script.contains("[DllImport(\"wininet.dll\")]"));
        assertTrue(script.contains("-Value '" + policy + "'"));
        assertTrue(command.get(command.size() - 1).matches("[A-Za-z0-9+/=]+"));
    }
}
