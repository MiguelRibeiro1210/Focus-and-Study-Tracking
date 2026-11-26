package com.focusdesktop.infrastructure.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.port.ActiveApplicationPort;
import com.focusdesktop.domain.model.AppIdentifier;
import com.focusdesktop.domain.model.TargetPlatform;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformActiveApplicationAdapter implements ActiveApplicationPort {
    private static final String WINDOWS_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class FocusForeground { [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow(); [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId); }'
            $window = [FocusForeground]::GetForegroundWindow()
            [uint32]$foregroundProcessId = 0
            if ($window -ne [IntPtr]::Zero) { [void][FocusForeground]::GetWindowThreadProcessId($window, [ref]$foregroundProcessId) }
            Write-Output $foregroundProcessId
            """;
    private final String os;
    private final Map<String, String> environment;
    private final NativeCommands commands;
    private final Function<Long, Optional<Observation>> processes;
    private final ObjectMapper mapper;

    @Autowired
    public PlatformActiveApplicationAdapter(ObjectMapper mapper) {
        this(System.getProperty("os.name"), System.getenv(), NativeCommands::execute,
                PlatformActiveApplicationAdapter::lookupProcess, mapper);
    }

    public PlatformActiveApplicationAdapter(String os, Map<String, String> environment, NativeCommands commands,
            Function<Long, Optional<Observation>> processes, ObjectMapper mapper) {
        this.os = os.toLowerCase(Locale.ROOT); this.environment = Map.copyOf(environment);
        this.commands = commands; this.processes = processes; this.mapper = mapper;
    }

    @Override
    public Optional<Observation> currentApplication() {
        long pid;
        if (os.contains("win")) {
            pid = numeric(commands.run(List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", WINDOWS_SCRIPT)));
        } else if (os.contains("mac")) {
            pid = numeric(commands.run(List.of("/usr/bin/osascript", "-l", "JavaScript", "-e",
                    "ObjC.import('AppKit'); var app = $.NSWorkspace.sharedWorkspace.frontmostApplication; app.isNil() ? 0 : app.processIdentifier;")));
        } else if (os.contains("linux")) {
            pid = linuxPid();
        } else throw new Unavailable("Foreground application detection is unsupported on this operating system.");
        return pid == 0 ? Optional.empty() : processes.apply(pid);
    }

    private long linuxPid() {
        if (present("HYPRLAND_INSTANCE_SIGNATURE")) return jsonPid(commands.run(List.of("hyprctl", "-j", "activewindow")), false);
        if (present("SWAYSOCK")) return jsonPid(commands.run(List.of("swaymsg", "-t", "get_tree", "-r")), true);
        if ("wayland".equalsIgnoreCase(environment.get("XDG_SESSION_TYPE")) || present("WAYLAND_DISPLAY")) {
            throw new Unavailable("This Wayland desktop has no supported foreground adapter. Use Hyprland, Sway or an X11 session.");
        }
        if (!present("DISPLAY")) throw new Unavailable("No graphical desktop is available for foreground detection.");
        String root = commands.run(List.of("xprop", "-notype", "-root", "_NET_ACTIVE_WINDOW"));
        var window = Pattern.compile("(?m)^_NET_ACTIVE_WINDOW\\s*(?:=|: window id #)\\s*(0x[0-9a-fA-F]+)\\s*$").matcher(root);
        if (!window.find()) throw new Unavailable("The X11 window manager does not expose an active window.");
        if (Long.parseUnsignedLong(window.group(1).substring(2), 16) == 0) return 0;
        String property = commands.run(List.of("xprop", "-notype", "-id", window.group(1), "_NET_WM_PID"));
        var process = Pattern.compile("(?m)^_NET_WM_PID\\s*=\\s*(\\d+)\\s*$").matcher(property);
        if (!process.find()) throw new Unavailable("The foreground X11 window does not expose a process identifier.");
        return numeric(process.group(1));
    }

    private long jsonPid(String json, boolean tree) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            JsonNode value = tree ? focused(root) : root.get("pid");
            if (value == null || value.isNull()) return 0;
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw new IllegalArgumentException();
            return value.longValue();
        } catch (Exception exception) { throw new Unavailable("The desktop returned an invalid foreground process response."); }
    }

    private static JsonNode focused(JsonNode node) {
        if (node.path("focused").asBoolean(false) && node.has("pid")) return node.get("pid");
        for (String children : List.of("nodes", "floating_nodes")) {
            for (JsonNode child : node.path(children)) {
                JsonNode result = focused(child); if (result != null) return result;
            }
        }
        return null;
    }

    private static long numeric(String output) {
        try { if (!output.matches("\\d{1,18}")) throw new IllegalArgumentException(); return Long.parseLong(output); }
        catch (IllegalArgumentException exception) { throw new Unavailable("The desktop returned an invalid foreground process identifier."); }
    }
    private boolean present(String key) { return !environment.getOrDefault(key, "").isBlank(); }

    public static Optional<Observation> lookupProcess(long pid) {
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).flatMap(process -> {
            var info = process.info();
            if (!process.isAlive()) return Optional.empty();
            String path = info.command().orElseThrow(() -> new Unavailable("Foreground executable identity is unavailable; check process permissions."));
            var started = info.startInstant().orElseThrow(() -> new Unavailable("Foreground process start time is unavailable; safe instance tracking is not possible."));
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            TargetPlatform platform = os.contains("win") ? TargetPlatform.WINDOWS : os.contains("mac") ? TargetPlatform.MACOS : TargetPlatform.LINUX;
            String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
            try {
                return process.isAlive() ? Optional.of(new Observation(pid, started, new AppIdentifier(platform, path, name))) : Optional.empty();
            } catch (IllegalArgumentException exception) { throw new Unavailable("Foreground executable metadata is not a usable application identifier."); }
        });
    }
}
