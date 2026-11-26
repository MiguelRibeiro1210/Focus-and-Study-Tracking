package com.focusdesktop.infrastructure.desktop;

import java.util.*;

final class ProcessAncestors {
    private ProcessAncestors() { }
    static List<ProcessHandle> selfAndAncestors() {
        List<ProcessHandle> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ProcessHandle process = ProcessHandle.current();
        while (process != null && visited.add(process.pid())) {
            result.add(process); process = process.parent().orElse(null);
        }
        return List.copyOf(result);
    }
}
