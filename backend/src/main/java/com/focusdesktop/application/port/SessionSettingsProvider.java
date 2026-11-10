package com.focusdesktop.application.port;

import com.focusdesktop.domain.model.SessionSettings;
import java.util.Optional;

public interface SessionSettingsProvider {
    Optional<SessionSettings> findCurrentSettings();
}
