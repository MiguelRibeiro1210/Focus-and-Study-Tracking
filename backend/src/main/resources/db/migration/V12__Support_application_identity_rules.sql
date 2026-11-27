ALTER TABLE blocking_rule DROP CONSTRAINT ck_blocking_rule_target;
ALTER TABLE blocking_rule ADD CONSTRAINT ck_blocking_rule_target CHECK (
    (target_type = 'WEBSITE' AND platform IS NULL AND match_kind IS NULL)
    OR (target_type = 'APPLICATION' AND platform IS NOT NULL AND match_kind IS NOT NULL
        AND platform IN ('WINDOWS', 'MACOS', 'LINUX')
        AND match_kind IN ('EXECUTABLE_PATH', 'PROCESS_NAME', 'APPLICATION_ID'))
);
