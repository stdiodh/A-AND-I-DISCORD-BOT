CREATE TABLE meeting_summary_artifacts (
    id BIGSERIAL PRIMARY KEY,
    meeting_session_id BIGINT NOT NULL,
    guild_id BIGINT NOT NULL,
    thread_id BIGINT NOT NULL,
    summary_message_id BIGINT,
    message_count INT NOT NULL,
    participant_count INT NOT NULL,
    decision_count INT NOT NULL,
    action_count INT NOT NULL,
    todo_count INT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version VARCHAR(16) NOT NULL,
    source_window_start TIMESTAMPTZ NOT NULL,
    source_window_end TIMESTAMPTZ NOT NULL,
    source_buffer_seconds INT NOT NULL DEFAULT 0,
    decisions_text TEXT,
    actions_text TEXT,
    todos_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_summary_artifacts_meeting_session
        FOREIGN KEY (meeting_session_id) REFERENCES meeting_sessions (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_summary_artifacts_meeting_session_generated_at
    ON meeting_summary_artifacts (meeting_session_id, generated_at DESC);

CREATE INDEX idx_summary_artifacts_guild_thread_generated_at
    ON meeting_summary_artifacts (guild_id, thread_id, generated_at DESC);
