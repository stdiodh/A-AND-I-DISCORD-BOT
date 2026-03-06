ALTER TABLE meeting_sessions
    ADD COLUMN IF NOT EXISTS board_channel_id BIGINT;

DROP INDEX IF EXISTS uq_meeting_sessions_guild_active;

CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_sessions_guild_board_channel_active
    ON meeting_sessions (guild_id, board_channel_id)
    WHERE status = 'ACTIVE' AND board_channel_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_meeting_sessions_guild_board_status_started_at
    ON meeting_sessions (guild_id, board_channel_id, status, started_at DESC);
