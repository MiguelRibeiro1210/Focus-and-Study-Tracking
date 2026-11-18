CREATE TABLE app_settings (
    id VARCHAR(40) PRIMARY KEY,
    default_profile_id UUID NOT NULL,
    daily_goal_hours INTEGER NOT NULL,
    theme VARCHAR(16) NOT NULL,
    notifications_enabled BOOLEAN NOT NULL,
    retention_days INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_app_settings_daily_goal CHECK (daily_goal_hours BETWEEN 1 AND 24),
    CONSTRAINT ck_app_settings_retention CHECK (retention_days IS NULL OR retention_days BETWEEN 7 AND 3650)
);

CREATE TABLE timer_profile (
    id UUID PRIMARY KEY,
    settings_id VARCHAR(40) NOT NULL,
    name VARCHAR(60) NOT NULL,
    name_normalized VARCHAR(60) NOT NULL,
    work_seconds BIGINT NOT NULL,
    short_break_seconds BIGINT NOT NULL,
    long_break_seconds BIGINT NOT NULL,
    intervals_before_long_break INTEGER NOT NULL,
    auto_start_breaks BOOLEAN NOT NULL,
    auto_start_work BOOLEAN NOT NULL,
    CONSTRAINT fk_timer_profile_settings FOREIGN KEY (settings_id) REFERENCES app_settings (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_timer_profile_work CHECK (work_seconds BETWEEN 60 AND 10800 AND MOD(work_seconds, 60) = 0),
    CONSTRAINT ck_timer_profile_short CHECK (short_break_seconds BETWEEN 60 AND 3600 AND MOD(short_break_seconds, 60) = 0),
    CONSTRAINT ck_timer_profile_long CHECK (long_break_seconds BETWEEN 60 AND 7200 AND MOD(long_break_seconds, 60) = 0),
    CONSTRAINT ck_timer_profile_long_at_least_short CHECK (long_break_seconds >= short_break_seconds),
    CONSTRAINT ck_timer_profile_intervals CHECK (intervals_before_long_break BETWEEN 1 AND 12)
);

CREATE INDEX idx_timer_profile_settings ON timer_profile (settings_id);
CREATE UNIQUE INDEX uq_timer_profile_name_ci ON timer_profile (settings_id, name_normalized);
