CREATE TABLE distraction_projection_lock (
    singleton_slot SMALLINT PRIMARY KEY CHECK (singleton_slot = 1)
);
INSERT INTO distraction_projection_lock(singleton_slot) VALUES (1);

CREATE TABLE distraction_projection_event (
    event_id UUID PRIMARY KEY,
    detection_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES focus_session(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    payload CLOB NOT NULL
);
CREATE INDEX idx_distraction_projection_detection ON distraction_projection_event(detection_id, occurred_at, event_id);
CREATE INDEX idx_distraction_projection_session ON distraction_projection_event(session_id);

CREATE TABLE distraction_log (
    detection_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES focus_session(id) ON DELETE CASCADE,
    occurred_at TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    detection_payload CLOB NOT NULL,
    outcome_payload CLOB
);
CREATE INDEX idx_distraction_log_time ON distraction_log(occurred_at DESC, detection_id);
CREATE INDEX idx_distraction_log_session ON distraction_log(session_id, occurred_at DESC, detection_id);
