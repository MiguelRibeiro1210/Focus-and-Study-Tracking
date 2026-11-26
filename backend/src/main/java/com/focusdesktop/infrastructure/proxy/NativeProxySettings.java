package com.focusdesktop.infrastructure.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.infrastructure.desktop.NativeCommands;
import java.net.URI;
import java.util.*;

/** Fixed native command arrays; never accepts a shell program from rules or the UI. */
final class NativeProxySettings implements OwnedProxySettings.Store {
    private static final String REGISTRY = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    private final String os;
    private final NativeCommands commands;
    private final ObjectMapper mapper = new ObjectMapper();
    NativeProxySettings() { this(System.getProperty("os.name"), NativeCommands::execute); }
    NativeProxySettings(String os, NativeCommands commands) { this.os = os.toLowerCase(Locale.ROOT); this.commands = commands; }
    @Override public List<OwnedProxySettings.Change> prepare(String pacUrl) {
        URI pac = URI.create(pacUrl);
        if (!"http".equals(pac.getScheme()) || !"127.0.0.1".equals(pac.getHost()) || !pac.getPath().startsWith("/pac/"))
            throw new IllegalArgumentException("Loopback PAC required");
        if (os.contains("win")) {
            String manual = powershell("$v=Get-ItemProperty -LiteralPath '" + REGISTRY + "'; if($v.ProxyEnable -eq 1 -or $v.AutoDetect -eq 1){'ON'}else{'OFF'}");
            String before = read("windows-pac");
            if (manual.equals("ON") || !Set.of("", "__ABSENT__").contains(before))
                throw new IllegalStateException("An existing proxy configuration must be preserved");
            return List.of(new OwnedProxySettings.Change("windows-pac", before, pacUrl));
        }
        if (os.contains("mac")) {
            var changes = new ArrayList<OwnedProxySettings.Change>();
            String[] services = commands.run(List.of("/usr/sbin/networksetup", "-listallnetworkservices")).split("\\R");
            for (int index = 1; index < services.length; index++) {
                String service = services[index].trim(); if (service.isEmpty() || service.startsWith("*")) continue;
                for (String kind : List.of("-getwebproxy", "-getsecurewebproxy", "-getsocksfirewallproxy", "-getproxyautodiscovery")) {
                    String result = commands.run(List.of("/usr/sbin/networksetup", kind, service));
                    if (result.contains("Enabled: Yes") || result.trim().endsWith("On"))
                        throw new IllegalStateException("An existing proxy configuration must be preserved");
                }
                String key = "mac-service:" + service;
                String before = read(key);
                try {
                    if (mapper.readTree(before).path("enabled").asBoolean()) throw new IllegalStateException("Existing PAC must be preserved");
                    changes.add(new OwnedProxySettings.Change(key, before, mapper.writeValueAsString(new MacValue(pacUrl, true))));
                } catch (java.io.IOException exception) { throw new IllegalStateException("Invalid native settings"); }
            }
            return changes;
        }
        if (!os.contains("linux")) throw new IllegalStateException("Unsupported system proxy settings");
        String mode = read("gnome-mode");
        if (!mode.equals("'none'")) throw new IllegalStateException("An existing proxy configuration must be preserved");
        return List.of(new OwnedProxySettings.Change("gnome-url", read("gnome-url"), "'" + pacUrl + "'"),
                new OwnedProxySettings.Change("gnome-mode", mode, "'auto'"));
    }
    @Override public String read(String key) {
        if (os.contains("win") && key.equals("windows-pac"))
            return powershell("$v=Get-ItemProperty -LiteralPath '" + REGISTRY + "'; if($null -eq $v.AutoConfigURL){'__ABSENT__'}else{$v.AutoConfigURL}");
        if (os.contains("linux") && Set.of("gnome-mode", "gnome-url").contains(key))
            return commands.run(List.of("gsettings", "get", "org.gnome.system.proxy", key.equals("gnome-mode") ? "mode" : "autoconfig-url")).trim();
        if (os.contains("mac") && key.startsWith("mac-service:")) {
            String result = commands.run(List.of("/usr/sbin/networksetup", "-getautoproxyurl", service(key)));
            String url = null; Boolean enabled = null;
            for (String line : result.split("\\R")) {
                if (line.startsWith("URL: ")) url = line.substring(5).trim();
                if (line.startsWith("Enabled: ")) enabled = line.substring(9).trim().equals("Yes");
            }
            if (url == null || enabled == null) throw new IllegalStateException("Cannot read proxy configuration");
            if (url.equals("(null)")) url = "";
            try { return mapper.writeValueAsString(new MacValue(url, enabled)); }
            catch (java.io.IOException exception) { throw new IllegalStateException("Cannot encode native settings"); }
        }
        throw new IllegalArgumentException("Invalid native proxy key");
    }
    @Override public void write(String key, String value) {
        if (os.contains("win") && key.equals("windows-pac")) {
            String assignment = value.equals("__ABSENT__")
                    ? "Remove-ItemProperty -LiteralPath '" + REGISTRY + "' -Name AutoConfigURL -ErrorAction SilentlyContinue;"
                    : "Set-ItemProperty -LiteralPath '" + REGISTRY + "' -Name AutoConfigURL -Value '" + value.replace("'", "''") + "';";
            powershell(assignment + " Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class ProxyRefresh { [DllImport(\"wininet.dll\")] public static extern bool InternetSetOption(IntPtr h, int o, IntPtr b, int l); }';"
                    + "[ProxyRefresh]::InternetSetOption([IntPtr]::Zero,39,[IntPtr]::Zero,0)|Out-Null; [ProxyRefresh]::InternetSetOption([IntPtr]::Zero,37,[IntPtr]::Zero,0)|Out-Null");
            return;
        }
        if (os.contains("linux") && Set.of("gnome-mode", "gnome-url").contains(key)) {
            commands.run(List.of("gsettings", "set", "org.gnome.system.proxy", key.equals("gnome-mode") ? "mode" : "autoconfig-url", value)); return;
        }
        if (os.contains("mac") && key.startsWith("mac-service:")) {
            try {
                MacValue target = mapper.readValue(value, MacValue.class);
                commands.run(List.of("/usr/sbin/networksetup", "-setautoproxyurl", service(key), target.url()));
                commands.run(List.of("/usr/sbin/networksetup", "-setautoproxystate", service(key), target.enabled() ? "on" : "off"));
                return;
            } catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid native settings"); }
        }
        throw new IllegalArgumentException("Invalid native proxy key");
    }
    private String powershell(String script) {
        return commands.run(List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", "$ErrorActionPreference='Stop'; " + script)).trim();
    }
    private static String service(String key) {
        String value = key.substring("mac-service:".length());
        if (value.isBlank() || value.length() > 256 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid service");
        return value;
    }
    private record MacValue(String url, boolean enabled) { }
}
