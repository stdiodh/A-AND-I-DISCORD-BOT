ALTER TABLE meeting_sessions
    ADD COLUMN IF NOT EXISTS agenda_link_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_meeting_sessions_agenda_links'
    ) THEN
        ALTER TABLE meeting_sessions
            ADD CONSTRAINT fk_meeting_sessions_agenda_links
                FOREIGN KEY (agenda_link_id) REFERENCES agenda_links (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_meeting_sessions_agenda_link_id
    ON meeting_sessions (agenda_link_id);
