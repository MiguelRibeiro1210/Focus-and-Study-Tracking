package com.focusdesktop.infrastructure.persistence.jdbc;

import com.focusdesktop.application.port.BlockingRuleAccess;
import com.focusdesktop.application.port.ProtectionPreferences;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcProtectionPreferences implements ProtectionPreferences {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final BlockingRuleAccess rules;
    private final Clock clock;
    public JdbcProtectionPreferences(org.springframework.jdbc.core.JdbcTemplate jdbc, BlockingRuleAccess rules, Clock clock) {
        this.jdbc = jdbc; this.rules = rules; this.clock = clock;
    }
    @Override
    @Transactional(readOnly = true)
    public State get() {
        return jdbc.queryForObject("SELECT applications_enabled,application_consent_at FROM blocking_policy WHERE singleton_slot=1", (row, index) -> {
            var consent = row.getObject("application_consent_at", OffsetDateTime.class);
            return new State(row.getBoolean("applications_enabled"), consent == null ? null : consent.toInstant());
        });
    }
    @Override
    @Transactional
    public State setApplicationsEnabled(boolean enabled, String consent) {
        if (enabled && !"SUSPEND_MATCHED_APPS".equals(consent)) throw new IllegalArgumentException("Explicit temporary-suspension consent is required");
        rules.lockActivePolicy();
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("UPDATE blocking_policy SET applications_enabled=?,application_consent_at=?,version=version+1,updated_at=? WHERE singleton_slot=1",
                enabled, enabled ? now : null, now);
        return get();
    }
}
