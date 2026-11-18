package com.focusdesktop.domain.model;

public record DataRetentionPolicy(Integer retentionDays) {
    public DataRetentionPolicy {
        if (retentionDays != null && (retentionDays < 7 || retentionDays > 3650)) {
            throw new IllegalArgumentException("Retention must be keep-forever or between 7 and 3650 days");
        }
    }

    public static DataRetentionPolicy keepForever() {
        return new DataRetentionPolicy(null);
    }
}
