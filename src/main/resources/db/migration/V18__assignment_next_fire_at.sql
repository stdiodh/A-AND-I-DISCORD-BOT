ALTER TABLE assignment_tasks
    ADD COLUMN IF NOT EXISTS next_fire_at TIMESTAMPTZ;

UPDATE assignment_tasks
SET next_fire_at = CASE
    WHEN status = 'PENDING' THEN NOW()
    ELSE NULL
END
WHERE next_fire_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_assignment_tasks_status_next_fire
    ON assignment_tasks (status, next_fire_at);
