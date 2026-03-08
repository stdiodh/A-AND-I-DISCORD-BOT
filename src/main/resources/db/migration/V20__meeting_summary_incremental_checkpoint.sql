ALTER TABLE meeting_summary_artifacts
    ADD COLUMN IF NOT EXISTS source_last_message_id BIGINT;

ALTER TABLE meeting_summary_artifacts
    ADD COLUMN IF NOT EXISTS participant_user_ids_text TEXT;
