package com.focusdesktop.application.dto;

import java.util.UUID;

public record TimerProfileDto(UUID id, String name, long workSeconds, long shortBreakSeconds,
                              long longBreakSeconds, int intervalsBeforeLongBreak,
                              boolean autoStartBreaks, boolean autoStartWork, boolean isDefault) { }
