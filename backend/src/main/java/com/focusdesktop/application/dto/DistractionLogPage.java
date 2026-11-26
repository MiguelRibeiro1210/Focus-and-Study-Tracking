package com.focusdesktop.application.dto;

import java.util.List;

public record DistractionLogPage(List<DistractionLogDto> items, long total, int page, int size) {
    public DistractionLogPage { items = List.copyOf(items); }
}
