package com.focusdesktop.infrastructure.persistence.jdbc;

import com.focusdesktop.application.port.BlockingRuleAccess;
import com.focusdesktop.application.port.WebsiteProtectionPreferences;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWebsiteProtectionPreferences implements WebsiteProtectionPreferences {
    private final JdbcTemplate jdbc;
    private final BlockingRuleAccess rules;
    private final Clock clock;
    public JdbcWebsiteProtectionPreferences(JdbcTemplate jdbc, BlockingRuleAccess rules, Clock clock) {
        this.jdbc = jdbc; this.rules = rules; this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public State get() {
        return jdbc.queryForObject("SELECT websites_enabled,website_consent_at,website_routing_method FROM blocking_policy WHERE singleton_slot=1", (row, index) -> {
            var consent = row.getObject("website_consent_at", OffsetDateTime.class);
            return new State(row.getBoolean("websites_enabled"), consent == null ? null : consent.toInstant(), RoutingMethod.valueOf(row.getString("website_routing_method")));
        });
    }
    @Override @Transactional
    public State setEnabled(boolean enabled, String consent) {
        return setEnabled(enabled, consent, get().routingMethod());
    }
    @Override @Transactional
    public State setEnabled(boolean enabled, String consent, RoutingMethod method) {
        java.util.Objects.requireNonNull(method, "routingMethod");
        if (enabled && !"CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC".equals(consent)
                && !(method == RoutingMethod.BRAVE && "FORWARD_SYSTEM_PROXY_TRAFFIC".equals(consent)))
            throw new IllegalArgumentException("Explicit consent for persistent browser routing and request forwarding is required");
        rules.lockActivePolicy();
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("UPDATE blocking_policy SET websites_enabled=?,website_consent_at=?,website_routing_method=?,version=version+1,updated_at=? WHERE singleton_slot=1",
                enabled, enabled ? now : null, method.name(), now);
        return get();
    }
}
