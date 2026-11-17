package com.focusdesktop.application.dto;

import java.util.List;

public record SessionHistoryPage(List<SessionHistoryItem> items, long total, int page, int size) {
    public SessionHistoryPage {
        items = List.copyOf(items);
    }
}
