ALTER TABLE assignment_tasks
    ADD COLUMN IF NOT EXISTS notify_role_id BIGINT;

ALTER TABLE assignment_tasks
    ADD COLUMN IF NOT EXISTS due_at TIMESTAMPTZ;

ALTER TABLE assignment_tasks
    ADD COLUMN IF NOT EXISTS pre_remind_hours_json TEXT;

ALTER TABLE assignment_tasks
    ADD COLUMN IF NOT EXISTS pre_notified_json TEXT;

ALTER TABLE assignment_tasks
    ADD COLUMN IF NOT EXISTS closing_message TEXT;

ALTER TABLE assignment_tasks
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

UPDATE assignment_tasks
SET due_at = COALESCE(due_at, remind_at)
WHERE due_at IS NULL;

ALTER TABLE assignment_tasks
    ALTER COLUMN due_at SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_assignment_tasks_status'
    ) THEN
        ALTER TABLE assignment_tasks
            DROP CONSTRAINT ck_assignment_tasks_status;
    END IF;
END $$;

ALTER TABLE assignment_tasks
    ADD CONSTRAINT ck_assignment_tasks_status
        CHECK (status IN ('PENDING', 'DONE', 'CANCELED', 'CLOSED'));

CREATE INDEX IF NOT EXISTS idx_assignment_tasks_status_due_closed
    ON assignment_tasks (status, due_at, closed_at);
