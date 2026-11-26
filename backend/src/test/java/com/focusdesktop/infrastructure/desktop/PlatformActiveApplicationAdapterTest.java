package com.focusdesktop.infrastructure.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.port.ActiveApplicationPort;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlatformActiveApplicationAdapterTest {
    @Test
    void selectsFixedWindowsAndMacCommandsAndResolvesOnlyTheForegroundPid() {
        for (String os : List.of("Windows 11", "Mac OS X")) {
            List<List<String>> commands = new ArrayList<>(); List<Long> pids = new ArrayList<>();
            var adapter = adapter(os, Map.of(), args -> { commands.add(args); return "456"; }, pids);
            assertTrue(adapter.currentApplication().isEmpty());
            assertEquals(List.of(456L), pids);
            assertEquals(os.startsWith("Windows") ? "powershell.exe" : "/usr/bin/osascript", commands.get(0).get(0));
            assertFalse(String.join(" ", commands.get(0)).contains("title"));
        }
    }

    @Test
    void x11UsesNumericWindowIdAndRequiresThePidProperty() {
        List<List<String>> commands = new ArrayList<>(); List<Long> pids = new ArrayList<>();
        var adapter = adapter("Linux", Map.of("DISPLAY", ":1"), args -> {
            commands.add(args); return commands.size() == 1 ? "_NET_ACTIVE_WINDOW: window id # 0x123abc" : "_NET_WM_PID = 456";
        }, pids);
        adapter.currentApplication();
        assertEquals(List.of(456L), pids);
        assertEquals(List.of("xprop", "-notype", "-id", "0x123abc", "_NET_WM_PID"), commands.get(1));
    }

    @Test
    void hyprlandAndSwayExtractOnlyTheFocusedPidAndIgnoreTitles() {
        List<Long> pids = new ArrayList<>();
        adapter("Linux", Map.of("HYPRLAND_INSTANCE_SIGNATURE", "test", "WAYLAND_DISPLAY", "wayland-1"),
                args -> "{\"pid\":456,\"title\":\"Private title\"}", pids).currentApplication();
        adapter("Linux", Map.of("SWAYSOCK", "test"), args -> "{\"nodes\":[{\"pid\":123,\"focused\":false}],"
                + "\"floating_nodes\":[{\"pid\":789,\"focused\":true,\"name\":\"Private title\"}]}", pids).currentApplication();
        assertEquals(List.of(456L, 789L), pids);
    }

    @Test
    void unsupportedWaylandDoesNotFallBackToXwaylandAndNoWindowDoesNotLookupAProcess() {
        List<Long> pids = new ArrayList<>(); List<List<String>> commands = new ArrayList<>();
        assertThrows(ActiveApplicationPort.Unavailable.class, () -> adapter("Linux",
                Map.of("XDG_SESSION_TYPE", "wayland", "DISPLAY", ":1"), args -> { commands.add(args); return "0"; }, pids).currentApplication());
        assertTrue(commands.isEmpty());
        assertTrue(adapter("Windows", Map.of(), args -> "0", pids).currentApplication().isEmpty());
        assertTrue(pids.isEmpty());
    }

    @Test
    void malformedOrInjectionResponsesNeverReachProcessLookup() {
        for (String output : List.of("123; touch secret", "-1", "title 456", "9999999999999999999999")) {
            List<Long> pids = new ArrayList<>();
            assertThrows(ActiveApplicationPort.Unavailable.class, () -> adapter("Windows", Map.of(), args -> output, pids).currentApplication());
            assertTrue(pids.isEmpty());
        }
        assertThrows(ActiveApplicationPort.Unavailable.class, () -> adapter("Linux", Map.of("HYPRLAND_INSTANCE_SIGNATURE", "test"),
                args -> "{\"pid\":\"123\"}", new ArrayList<>()).currentApplication());
    }

    @Test
    void resolvesTheCurrentTestProcessWithoutEnumeratingOtherProcesses() {
        var observation = PlatformActiveApplicationAdapter.lookupProcess(ProcessHandle.current().pid()).orElseThrow();
        assertEquals(ProcessHandle.current().pid(), observation.pid());
        assertEquals(ProcessHandle.current().info().startInstant().orElseThrow(), observation.startedAt());
        assertNotNull(observation.identity().executablePath());
        assertTrue(observation.identity().processName().toLowerCase(Locale.ROOT).startsWith("java"));
        assertTrue(PlatformActiveApplicationAdapter.lookupProcess(Long.MAX_VALUE).isEmpty());
    }

    private PlatformActiveApplicationAdapter adapter(String os, Map<String, String> env, NativeCommands commands, List<Long> pids) {
        return new PlatformActiveApplicationAdapter(os, env, commands, pid -> { pids.add(pid); return Optional.empty(); }, new ObjectMapper());
    }
}
