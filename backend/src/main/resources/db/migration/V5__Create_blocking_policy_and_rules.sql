CREATE TABLE blocking_policy (
    id UUID PRIMARY KEY,
    singleton_slot SMALLINT NOT NULL UNIQUE CHECK (singleton_slot = 1),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO blocking_policy (id, singleton_slot)
VALUES ('00000000-0000-0000-0000-000000000001', 1);

CREATE TABLE blocking_rule (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES blocking_policy(id) ON DELETE CASCADE,
    display_name VARCHAR NOT NULL CHECK (LENGTH(TRIM(display_name)) > 0),
    target_type VARCHAR(20) NOT NULL,
    platform VARCHAR(10),
    match_kind VARCHAR(20),
    target_value VARCHAR NOT NULL,
    target_key VARCHAR NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category = 'OTHER'),
    action VARCHAR(10) NOT NULL CHECK (action IN ('LOG', 'WARN', 'BLOCK')),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_blocking_rule_target UNIQUE (policy_id, target_key),
    CONSTRAINT ck_blocking_rule_target CHECK (
        (target_type = 'WEBSITE' AND platform IS NULL AND match_kind IS NULL)
        OR (target_type = 'APPLICATION' AND platform IS NOT NULL AND match_kind IS NOT NULL
            AND platform IN ('WINDOWS', 'MACOS', 'LINUX')
            AND match_kind IN ('EXECUTABLE_PATH', 'PROCESS_NAME'))
    )
);
CREATE INDEX idx_blocking_rule_policy ON blocking_rule(policy_id);
