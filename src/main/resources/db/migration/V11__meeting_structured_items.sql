CREATE TABLE meeting_structured_items (
    id BIGSERIAL PRIMARY KEY,
    meeting_session_id BIGINT NOT NULL,
    guild_id BIGINT NOT NULL,
    thread_id BIGINT NOT NULL,
    item_type VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    assignee_user_id BIGINT,
    due_date_local DATE,
    source VARCHAR(16) NOT NULL DEFAULT 'SLASH',
    source_message_id BIGINT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_meeting_structured_items_session
        FOREIGN KEY (meeting_session_id) REFERENCES meeting_sessions (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_meeting_structured_items_type
        CHECK (item_type IN ('DECISION', 'ACTION'))
);

CREATE INDEX idx_meeting_structured_items_session_created_at
    ON meeting_structured_items (meeting_session_id, created_at ASC);

CREATE INDEX idx_meeting_structured_items_guild_thread_created_at
    ON meeting_structured_items (guild_id, thread_id, created_at ASC);
