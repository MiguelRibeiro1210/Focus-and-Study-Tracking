ALTER TABLE domain_event_outbox
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE domain_event_outbox
    ADD COLUMN last_error VARCHAR(1000);

UPDATE domain_event_outbox
SET next_attempt_at = occurred_at
WHERE delivered_at IS NULL;

CREATE INDEX idx_domain_event_outbox_due
    ON domain_event_outbox (delivered_at, next_attempt_at, occurred_at);
