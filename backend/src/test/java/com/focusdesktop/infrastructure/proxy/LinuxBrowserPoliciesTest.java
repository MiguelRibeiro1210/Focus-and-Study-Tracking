package com.focusdesktop.infrastructure.proxy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxBrowserPoliciesTest {
    @TempDir Path root;
    private static final String PAC = "http://127.0.0.1:12345/pac/00000000-0000-0000-0000-000000000001";
    private void installed(String binary) throws Exception {
        Path file = root.resolve("usr/bin/" + binary); Files.createDirectories(file.getParent()); Files.writeString(file, "fixture");
        assertTrue(file.toFile().setExecutable(true));
    }
    @Test void nativePoliciesPersistForNormalLaunchesAndRestoreIndependently() throws Exception {
        installed("brave"); installed("firefox");
        Path firefox = root.resolve("etc/firefox/policies/policies.json"); Files.createDirectories(firefox.getParent());
        Files.writeString(firefox, "{\"policies\":{\"DisableTelemetry\":true}}");
        var store = new LinuxBrowserPolicies(root);
        try (var owned = new OwnedProxySettings(store, root.resolve("journal"))) {
            owned.apply(PAC); assertTrue(owned.isApplied());
            Path brave = root.resolve("etc/brave/policies/managed/focusdesktop-proxy.json");
            assertEquals(PAC, BrowserProxyPolicy.parse(Files.readString(brave)).path("ProxySettings").path("ProxyPacUrl").asText());
            assertTrue(Files.isReadable(brave));
            var current = (com.fasterxml.jackson.databind.node.ObjectNode) BrowserProxyPolicy.parse(Files.readString(firefox));
            current.withObject("/policies").put("DisableFirefoxStudies", true);
            Files.writeString(firefox, current.toString());
            assertTrue(owned.isApplied(), "Unrelated Firefox policy edits must not revoke the proxy lease");
            Files.writeString(brave, "{\"ProxyMode\":\"direct\"}");
            assertFalse(owned.isApplied()); owned.restore();
            assertEquals("direct", BrowserProxyPolicy.parse(Files.readString(brave)).path("ProxyMode").asText());
            var remaining = BrowserProxyPolicy.parse(Files.readString(firefox)).path("policies");
            assertFalse(remaining.has("Proxy")); assertTrue(remaining.path("DisableTelemetry").asBoolean());
            assertTrue(remaining.path("DisableFirefoxStudies").asBoolean());
        }
    }
    @Test void conflictsAndUnsafeDestinationsNeverReplaceExistingPolicies() throws Exception {
        installed("brave");
        Path policies = root.resolve("etc/brave/policies/managed"); Files.createDirectories(policies);
        Path existing = policies.resolve("corporate.json"); Files.writeString(existing, "{\"ProxyMode\":\"fixed_servers\"}");
        var store = new LinuxBrowserPolicies(root);
        assertThrows(IllegalStateException.class, () -> store.prepare(PAC));
        assertFalse(Files.exists(policies.resolve("focusdesktop-proxy.json")));
        Files.delete(existing);
        assertThrows(IllegalArgumentException.class, () -> store.prepare("https://external.example/pac"));
        assertThrows(IllegalArgumentException.class, () -> store.write("linux-policy:../../etc/passwd", "{}"));
        assertThrows(IllegalArgumentException.class, () -> store.write("linux-policy:brave", "{\"ExtensionInstallForcelist\":[\"bad\"]}"));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void refusesSymlinkPolicyPathsWithoutTouchingTargets() throws Exception {
        installed("brave");
        Path target = root.resolve("untouched"); Files.createDirectory(target);
        Files.createDirectories(root.resolve("etc")); Files.createSymbolicLink(root.resolve("etc/brave"), target);
        assertThrows(IllegalStateException.class, () -> new LinuxBrowserPolicies(root).prepare(PAC));
        try (var files = Files.list(target)) { assertEquals(0, files.count()); }
    }
    @Test void onlyConfiguresInstalledNativeBrowsersAndRejectsExistingFirefoxProxy() throws Exception {
        assertThrows(IllegalStateException.class, () -> new LinuxBrowserPolicies(root).prepare(PAC));
        installed("firefox");
        Path path = root.resolve("etc/firefox/policies/policies.json"); Files.createDirectories(path.getParent());
        Files.writeString(path, "{\"policies\":{\"Proxy\":{\"Mode\":\"none\"}}}");
        assertThrows(IllegalStateException.class, () -> new LinuxBrowserPolicies(root).prepare(PAC));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void privilegedAdapterRejectsUserControlledPolicyDirectories() throws Exception {
        installed("brave");
        org.junit.jupiter.api.Assumptions.assumeFalse(Files.getOwner(root).getName().equals("root"));
        assertThrows(IllegalStateException.class, () -> new LinuxBrowserPolicies(root, true).prepare(PAC));
    }
}
