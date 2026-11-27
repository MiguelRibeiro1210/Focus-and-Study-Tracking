package com.focusdesktop.infrastructure.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Fixed native browser policy destinations; no paths or JSON accepted from the API. */
final class LinuxBrowserPolicies implements OwnedProxySettings.Store {
    private static final Map<String, String> DIRECTORIES = Map.of(
            "brave", "etc/brave/policies/managed", "chrome", "etc/opt/chrome/policies/managed",
            "chromium", "etc/chromium/policies/managed", "edge", "etc/opt/edge/policies/managed");
    private static final Map<String, List<String>> BINARIES = Map.of(
            "brave", List.of("usr/bin/brave", "usr/bin/brave-browser", "opt/brave.com/brave/brave"),
            "chrome", List.of("usr/bin/google-chrome", "usr/bin/google-chrome-stable", "opt/google/chrome/chrome"),
            "chromium", List.of("usr/bin/chromium", "usr/bin/chromium-browser"),
            "edge", List.of("usr/bin/microsoft-edge", "usr/bin/microsoft-edge-stable"),
            "firefox", List.of("usr/bin/firefox", "usr/bin/firefox-esr"));
    private final Path root;
    private final boolean privileged;
    LinuxBrowserPolicies() { this(Path.of("/"), true); }
    LinuxBrowserPolicies(Path root) { this(root, false); }
    LinuxBrowserPolicies(Path root, boolean privileged) { this.root = root.toAbsolutePath().normalize(); this.privileged = privileged; }
    @Override public boolean independentSettings() { return true; }

    @Override public List<OwnedProxySettings.Change> prepare(String pac) {
        BrowserProxyPolicy.pac(pac);
        var changes = new ArrayList<OwnedProxySettings.Change>();
        for (String browser : List.of("brave", "chrome", "chromium", "edge", "firefox")) {
            if (BINARIES.get(browser).stream().noneMatch(binary -> Files.isExecutable(root.resolve(binary)))) continue;
            String key = "linux-policy:" + browser;
            if (!read(key).equals(BrowserProxyPolicy.ABSENT)) throw new IllegalStateException("Existing browser proxy policy must be preserved");
            if (!browser.equals("firefox")) checkOtherPolicies(browser);
            String applied = browser.equals("firefox")
                    ? BrowserProxyPolicy.JSON.createObjectNode().put("Mode", "autoConfig").put("AutoConfigURL", pac).put("Locked", true).toString()
                    : BrowserProxyPolicy.chromium(pac);
            changes.add(new OwnedProxySettings.Change(key, BrowserProxyPolicy.ABSENT, applied));
        }
        if (changes.isEmpty()) throw new IllegalStateException("No supported native browsers found");
        return changes;
    }

    private void checkOtherPolicies(String browser) {
        Path directory = file("linux-policy:" + browser).getParent();
        if (!Files.exists(directory)) return;
        try (var files = Files.newDirectoryStream(directory, "*.json")) {
            int count = 0;
            for (Path policy : files) {
                if (++count > 128) throw new IOException("Too many policy files");
                JsonNode node = document(policy);
                for (String key : List.of("ProxySettings", "ProxyMode", "ProxyPacUrl", "ProxyServer", "ProxyBypassList", "ProxyServerMode"))
                    if (node.has(key)) throw new IllegalStateException("Existing browser proxy policy must be preserved");
            }
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @Override public String read(String key) {
        Path path = file(key);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return BrowserProxyPolicy.ABSENT;
        JsonNode value = document(path);
        if (key.equals("linux-policy:firefox")) {
            value = value.path("policies").get("Proxy");
            if (value == null) return BrowserProxyPolicy.ABSENT;
        }
        return value.toString();
    }
    @Override public void write(String key, String value) {
        Path path = file(key);
        try {
            ObjectNode content;
            if (key.equals("linux-policy:firefox")) {
                content = Files.exists(path) ? (ObjectNode) document(path) : BrowserProxyPolicy.JSON.createObjectNode();
                if (content.has("policies") && !content.get("policies").isObject()) throw new IOException("Invalid Firefox policies");
                var policies = content.withObject("/policies");
                if (value.equals(BrowserProxyPolicy.ABSENT)) policies.remove("Proxy");
                else { validate(value, true); policies.set("Proxy", BrowserProxyPolicy.parse(value)); }
                if (policies.isEmpty() && content.size() == 1) { Files.deleteIfExists(path); return; }
            } else {
                if (value.equals(BrowserProxyPolicy.ABSENT)) { Files.deleteIfExists(path); return; }
                validate(value, false); content = (ObjectNode) BrowserProxyPolicy.parse(value);
            }
            if (privileged) {
                var missing = new ArrayList<Path>();
                for (Path parent = path.getParent(); parent != null && !Files.exists(parent); parent = parent.getParent()) missing.add(parent);
                Collections.reverse(missing);
                var permissions = java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x");
                for (Path parent : missing) {
                    try {
                        Files.createDirectory(parent, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(permissions));
                        Files.setPosixFilePermissions(parent, permissions); // Browser users must be able to read new policy directories.
                    } catch (FileAlreadyExistsException exception) { safe(parent); }
                }
            } else Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), "focusdesktop-policy-", ".tmp");
            try {
                Files.writeString(temporary, content.toString());
                // Browser processes run as the user, even when the guardian is root.
                Files.setPosixFilePermissions(temporary, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private void validate(String value, boolean firefox) {
        JsonNode node = BrowserProxyPolicy.parse(value);
        if (firefox) {
            if (node.size() != 3 || !node.path("Mode").asText().equals("autoConfig") || !node.path("Locked").asBoolean())
                throw new IllegalArgumentException("Invalid Firefox proxy");
            BrowserProxyPolicy.pac(node.path("AutoConfigURL").asText());
        } else {
            String pac = node.path("ProxySettings").path("ProxyPacUrl").asText();
            if (!node.equals(BrowserProxyPolicy.parse(BrowserProxyPolicy.chromium(pac)))) throw new IllegalArgumentException("Invalid Chromium proxy");
        }
    }
    private Path file(String key) {
        String browser = key.startsWith("linux-policy:") ? key.substring(13) : "";
        Path file;
        if (browser.equals("firefox")) file = root.resolve("etc/firefox/policies/policies.json");
        else if (DIRECTORIES.containsKey(browser)) file = root.resolve(DIRECTORIES.get(browser)).resolve("focusdesktop-proxy.json");
        else throw new IllegalArgumentException("Invalid browser policy key");
        safe(file); return file;
    }
    private void safe(Path path) {
        for (Path part = path; part != null && part.startsWith(root); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IllegalStateException("Symbolic browser policy path is unsafe");
            if (privileged && Files.exists(part, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    var permissions = Files.getPosixFilePermissions(part);
                    if (!Files.getOwner(part).getName().equals("root")
                            || permissions.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
                            || permissions.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE))
                        throw new IllegalStateException("Browser policy paths must be administrator-controlled");
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }
        }
    }
    private JsonNode document(Path path) {
        safe(path);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1_048_576) throw new IOException("Unsafe policy file");
            return BrowserProxyPolicy.parse(Files.readString(path));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
}
