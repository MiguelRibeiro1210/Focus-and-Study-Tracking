CREATE TABLE focus_session (
    id UUID PRIMARY KEY,
    session_type VARCHAR(20) NOT NULL,
    state VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    planned_seconds BIGINT NOT NULL,
    actual_seconds BIGINT NOT NULL,
    settings_snapshot CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active_slot SMALLINT,
    version BIGINT NOT NULL,
    CONSTRAINT uq_focus_session_active_slot UNIQUE (active_slot),
    CONSTRAINT ck_focus_session_active_slot CHECK (
        (state IN ('RUNNING', 'PAUSED', 'WAITING_FOR_NEXT_INTERVAL') AND active_slot = 1)
        OR (state NOT IN ('RUNNING', 'PAUSED', 'WAITING_FOR_NEXT_INTERVAL') AND active_slot IS NULL)
    )
);

CREATE INDEX idx_focus_session_state ON focus_session (state);
CREATE INDEX idx_focus_session_started_at ON focus_session (started_at);

CREATE TABLE session_interval (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    interval_type VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    planned_seconds BIGINT NOT NULL,
    actual_seconds BIGINT NOT NULL,
    remaining_seconds BIGINT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    paused_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_session_interval_session FOREIGN KEY (session_id) REFERENCES focus_session (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_session_interval_sequence UNIQUE (session_id, sequence_no)
);

CREATE INDEX idx_session_interval_session ON session_interval (session_id);

CREATE TABLE domain_event_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload CLOB NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_domain_event_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_domain_event_outbox_pending ON domain_event_outbox (delivered_at, occurred_at);
