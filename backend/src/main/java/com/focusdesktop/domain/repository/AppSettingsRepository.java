package com.focusdesktop.domain.repository;

import com.focusdesktop.domain.model.AppSettings;

public interface AppSettingsRepository {
    AppSettings loadOrCreate();
    AppSettings save(AppSettings settings);
    AppSettings resetToDefaults();
}
