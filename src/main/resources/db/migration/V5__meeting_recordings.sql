CREATE TABLE meeting_recordings (
    meeting_id BIGINT PRIMARY KEY,
    voice_channel_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    data_dir TEXT NOT NULL,
    started_at_utc TIMESTAMPTZ,
    ended_at_utc TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_meeting_recordings_meeting_sessions
        FOREIGN KEY (meeting_id) REFERENCES meeting_sessions (id),
    CONSTRAINT ck_meeting_recordings_status
        CHECK (status IN ('DISABLED', 'PENDING', 'RECORDING', 'STOPPED', 'FAILED'))
);

CREATE INDEX idx_meeting_recordings_status_updated_at
    ON meeting_recordings (status, updated_at DESC);
