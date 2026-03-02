ALTER TABLE meeting_structured_items
    ADD COLUMN IF NOT EXISTS canceled_by BIGINT,
    ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMPTZ;

ALTER TABLE meeting_structured_items
    DROP CONSTRAINT IF EXISTS ck_meeting_structured_items_type;

ALTER TABLE meeting_structured_items
    ADD CONSTRAINT ck_meeting_structured_items_type
        CHECK (item_type IN ('DECISION', 'ACTION', 'TODO'));

CREATE INDEX IF NOT EXISTS idx_meeting_structured_items_session_active_created_at
    ON meeting_structured_items (meeting_session_id, created_at ASC)
    WHERE canceled_at IS NULL;
