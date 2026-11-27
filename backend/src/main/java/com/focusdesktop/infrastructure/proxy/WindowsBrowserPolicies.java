package com.focusdesktop.infrastructure.proxy;

import com.focusdesktop.infrastructure.desktop.NativeCommands;
import java.util.*;

/** Per-user policies plus system PAC; never overwrites machine or existing user proxy policy. */
final class WindowsBrowserPolicies implements OwnedProxySettings.Store {
    private static final Map<String, String> KEYS = Map.of(
            "brave", "Software\\Policies\\BraveSoftware\\Brave", "chrome", "Software\\Policies\\Google\\Chrome",
            "chromium", "Software\\Policies\\Chromium", "edge", "Software\\Policies\\Microsoft\\Edge",
            "firefox", "Software\\Policies\\Mozilla\\Firefox\\Proxy");
    private final NativeCommands commands;
    private final NativeProxySettings system;
    WindowsBrowserPolicies() { this(arguments -> NativeCommands.execute(arguments, true)); }
    WindowsBrowserPolicies(NativeCommands commands) { this.commands = commands; system = new NativeProxySettings("Windows", commands); }
    @Override public boolean independentSettings() { return true; }
    @Override public Map<String, String> readAll(List<String> keys) {
        StringBuilder script = new StringBuilder("$values=[ordered]@{}; ");
        for (String key : keys) {
            String path = key.equals("windows-pac") ? "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" : "HKCU:\\" + KEYS.get(browser(key));
            String read = key.equals("windows-pac") ? "$p.AutoConfigURL" : "$p.ProxySettings";
            script.append("$v='__ABSENT__'; if(Test-Path -LiteralPath '").append(path).append("'){$p=Get-ItemProperty -LiteralPath '").append(path).append("'; ");
            if (key.equals("windows-policy:firefox"))
                script.append("$o=@{}; $p.PSObject.Properties | Where-Object {$_.Name -notlike 'PS*'} | ForEach-Object {$o[$_.Name]=$_.Value}; if($o.Count -gt 0){$v=ConvertTo-Json -Compress -InputObject $o}; ");
            else script.append("if($null -ne ").append(read).append("){$v=[string]").append(read).append("}; ");
            script.append("}; $values['").append(key).append("']=$v; ");
        }
        script.append("ConvertTo-Json -Compress -Depth 4 -InputObject $values");
        var node = BrowserProxyPolicy.parse(shell(script.toString()));
        var result = new LinkedHashMap<String, String>();
        for (String key : keys) {
            if (!node.has(key) || !node.get(key).isTextual()) throw new IllegalStateException("Invalid registry snapshot");
            String value = node.get(key).asText();
            result.put(key, key.equals("windows-policy:firefox") && !value.equals(BrowserProxyPolicy.ABSENT) ? canonical(value) : value);
        }
        return result;
    }
    @Override public void writeAll(Map<String, String> values) {
        var scripts = new ArrayList<String>();
        var collector = new WindowsBrowserPolicies(args -> { scripts.add(ConnectionPowerShell.script(args)); return ""; });
        values.forEach(collector::write); // Validate every value before executing any script.
        if (!scripts.isEmpty()) shell(String.join(";\n", scripts));
    }
    @Override public void restore(List<OwnedProxySettings.Change> changes) {
        try {
            var current = readAll(changes.stream().map(OwnedProxySettings.Change::key).toList());
            var restore = new LinkedHashMap<String, String>();
            for (int index = changes.size() - 1; index >= 0; index--) {
                var change = changes.get(index);
                if (Objects.equals(current.get(change.key()), change.applied())) restore.put(change.key(), change.before());
            }
            writeAll(restore);
            var after = readAll(new ArrayList<>(restore.keySet()));
            if (!after.equals(restore)) throw new IllegalStateException("Registry restoration not confirmed");
        } catch (RuntimeException exception) {
            // A denied write must not prevent restoring independent browsers.
            OwnedProxySettings.Store.super.restore(changes);
        }
    }
    @Override public List<OwnedProxySettings.Change> prepare(String pac) {
        BrowserProxyPolicy.pac(pac);
        var changes = new ArrayList<>(system.prepare(pac));
        for (String browser : List.of("brave", "chrome", "chromium", "edge", "firefox")) {
            String key = "windows-policy:" + browser;
            for (String hive : List.of("HKCU", "HKLM")) {
                String path = hive + ":\\" + KEYS.get(browser);
                String filter = browser.equals("firefox") ? "$true" : "$_.Name -in @('ProxySettings','ProxyMode','ProxyPacUrl','ProxyServer','ProxyBypassList','ProxyServerMode')";
                String found = shell("if(Test-Path -LiteralPath '" + path + "'){ $p=Get-ItemProperty -LiteralPath '" + path
                        + "'; if(@($p.PSObject.Properties | Where-Object { $_.Name -notlike 'PS*' -and (" + filter + ") }).Count -gt 0){'CONFLICT'} }");
                if (found.equals("CONFLICT")) throw new IllegalStateException("Existing browser proxy policy must be preserved");
            }
            String applied = browser.equals("firefox") ? canonical("{\"Mode\":\"autoConfig\",\"AutoConfigURL\":\"" + pac + "\",\"Locked\":1}")
                    : BrowserProxyPolicy.parse(BrowserProxyPolicy.chromium(pac)).get("ProxySettings").toString();
            changes.add(new OwnedProxySettings.Change(key, BrowserProxyPolicy.ABSENT, applied));
        }
        return changes;
    }
    @Override public String read(String key) {
        if (key.equals("windows-pac")) return system.read(key);
        String browser = browser(key), path = "HKCU:\\" + KEYS.get(browser);
        String result = browser.equals("firefox")
                ? shell("if(!(Test-Path -LiteralPath '" + path + "')){'__ABSENT__'}else{$p=Get-ItemProperty -LiteralPath '" + path
                    + "'; $o=@{}; $p.PSObject.Properties | Where-Object {$_.Name -notlike 'PS*'} | ForEach-Object {$o[$_.Name]=$_.Value}; if($o.Count -eq 0){'__ABSENT__'}else{ConvertTo-Json -Compress -InputObject $o}}")
                : shell("if(!(Test-Path -LiteralPath '" + path + "')){'__ABSENT__'}else{$p=Get-ItemProperty -LiteralPath '" + path
                    + "'; if($null -eq $p.ProxySettings){'__ABSENT__'}else{[Console]::Write($p.ProxySettings)}}");
        return browser.equals("firefox") && !result.equals(BrowserProxyPolicy.ABSENT) ? canonical(result) : result;
    }
    @Override public void write(String key, String value) {
        if (key.equals("windows-pac")) { system.write(key, value); return; }
        String browser = browser(key), path = "HKCU:\\" + KEYS.get(browser);
        if (value.equals(BrowserProxyPolicy.ABSENT)) {
            shell("if(Test-Path -LiteralPath '" + path + "'){"
                    + (browser.equals("firefox") ? "Remove-Item -LiteralPath '" + path + "' -Recurse -Force"
                    : "Remove-ItemProperty -LiteralPath '" + path + "' -Name ProxySettings -ErrorAction SilentlyContinue") + "}");
            return;
        }
        var policy = BrowserProxyPolicy.parse(value);
        String pac = BrowserProxyPolicy.pac(policy.path(browser.equals("firefox") ? "AutoConfigURL" : "ProxyPacUrl").asText());
        if (browser.equals("firefox")) {
            if (!canonical(value).equals(canonical("{\"Mode\":\"autoConfig\",\"AutoConfigURL\":\"" + pac + "\",\"Locked\":1}")))
                throw new IllegalArgumentException("Invalid Firefox proxy policy");
            shell("New-Item -Path '" + path + "' -Force | Out-Null; New-ItemProperty -LiteralPath '" + path
                    + "' -Name Mode -PropertyType String -Value 'autoConfig' -Force | Out-Null; New-ItemProperty -LiteralPath '" + path
                    + "' -Name AutoConfigURL -PropertyType String -Value '" + pac + "' -Force | Out-Null; New-ItemProperty -LiteralPath '" + path
                    + "' -Name Locked -PropertyType DWord -Value 1 -Force | Out-Null");
        } else {
            if (!policy.equals(BrowserProxyPolicy.parse(BrowserProxyPolicy.chromium(pac)).get("ProxySettings")))
                throw new IllegalArgumentException("Invalid Chromium proxy policy");
            shell("New-Item -Path '" + path + "' -Force | Out-Null; New-ItemProperty -LiteralPath '" + path
                    + "' -Name ProxySettings -PropertyType String -Value '" + value.replace("'", "''") + "' -Force | Out-Null");
        }
    }
    private String browser(String key) {
        String browser = key.startsWith("windows-policy:") ? key.substring(15) : "";
        if (!KEYS.containsKey(browser)) throw new IllegalArgumentException("Invalid browser policy key");
        return browser;
    }
    private String shell(String script) {
        return commands.run(ConnectionPowerShell.command(script)).trim();
    }
    private String canonical(String value) {
        try { return BrowserProxyPolicy.JSON.writeValueAsString(BrowserProxyPolicy.JSON.convertValue(BrowserProxyPolicy.parse(value), TreeMap.class)); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Invalid browser policy", exception); }
    }
}
