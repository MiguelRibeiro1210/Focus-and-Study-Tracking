package com.focusdesktop.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WebsiteConsentMigrationTest {
    @Test
    void requiresBroaderConsentOnUpgradeWhilePreservingRulesAndApplicationConsent() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:consent_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).target("8").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("UPDATE blocking_policy SET websites_enabled=TRUE,website_consent_at=CURRENT_TIMESTAMP,applications_enabled=TRUE,application_consent_at=CURRENT_TIMESTAMP");
        jdbc.update("INSERT INTO blocking_rule(id,policy_id,display_name,target_type,target_value,target_key,category,action,enabled,created_at,updated_at) "
                + "SELECT ?,id,'Example','WEBSITE','example.com','website:example.com','OTHER','BLOCK',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM blocking_policy", UUID.randomUUID());
        Flyway.configure().dataSource(source).load().migrate();
        assertFalse(jdbc.queryForObject("SELECT websites_enabled FROM blocking_policy", Boolean.class));
        assertNull(jdbc.queryForObject("SELECT website_consent_at FROM blocking_policy", Object.class));
        assertTrue(jdbc.queryForObject("SELECT applications_enabled FROM blocking_policy", Boolean.class));
        assertNotNull(jdbc.queryForObject("SELECT application_consent_at FROM blocking_policy", Object.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM blocking_rule WHERE enabled=TRUE", Integer.class));
    }
}
