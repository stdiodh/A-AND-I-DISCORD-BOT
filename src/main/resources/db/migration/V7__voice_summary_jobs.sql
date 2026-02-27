CREATE TABLE voice_summary_jobs (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    meeting_thread_id BIGINT,
    voice_channel_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    data_dir TEXT NOT NULL,
    max_minutes INT NOT NULL DEFAULT 120,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    CONSTRAINT ck_voice_summary_jobs_status
        CHECK (status IN (
            'DISABLED',
            'READY',
            'RECORDING',
            'RECORDED',
            'TRANSCRIBING',
            'DONE',
            'FAILED'
        ))
);

CREATE INDEX idx_voice_summary_jobs_guild_status
    ON voice_summary_jobs (guild_id, status);

CREATE INDEX idx_voice_summary_jobs_status_updated_at
    ON voice_summary_jobs (status, updated_at DESC);
