CREATE TABLE meeting_sessions (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    thread_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_by BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_by BIGINT,
    ended_at TIMESTAMPTZ,
    summary_message_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_meeting_sessions_guild_config
        FOREIGN KEY (guild_id) REFERENCES guild_config (guild_id),
    CONSTRAINT ck_meeting_sessions_status
        CHECK (status IN ('ACTIVE', 'ENDED'))
);

CREATE UNIQUE INDEX uq_meeting_sessions_thread_id
    ON meeting_sessions (thread_id);

CREATE INDEX idx_meeting_sessions_guild_status_started_at
    ON meeting_sessions (guild_id, status, started_at DESC);

CREATE UNIQUE INDEX uq_meeting_sessions_guild_active
    ON meeting_sessions (guild_id)
    WHERE status = 'ACTIVE';
