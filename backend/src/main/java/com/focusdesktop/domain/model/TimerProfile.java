package com.focusdesktop.domain.model;

import java.util.Locale;
import java.util.Objects;

public final class TimerProfile {
    private final TimerProfileId id;
    private String name;
    private TimerPreferences preferences;

    public TimerProfile(TimerProfileId id, String name, TimerPreferences preferences) {
        this.id = Objects.requireNonNull(id, "id");
        update(name, preferences);
    }

    public void update(String newName, TimerPreferences newPreferences) {
        this.name = normalizeName(newName);
        this.preferences = Objects.requireNonNull(newPreferences, "preferences");
    }

    public static String normalizeName(String value) {
        Objects.requireNonNull(value, "name");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 60) {
            throw new IllegalArgumentException("Profile name must contain between 1 and 60 characters");
        }
        return normalized;
    }

    public boolean hasName(String other) {
        return name.toLowerCase(Locale.ROOT).equals(normalizeName(other).toLowerCase(Locale.ROOT));
    }

    public TimerProfileId id() { return id; }
    public String name() { return name; }
    public TimerPreferences preferences() { return preferences; }
}
