ALTER TABLE assignment_tasks
    ALTER COLUMN verify_url DROP NOT NULL;

ALTER TABLE assignment_tasks
    DROP CONSTRAINT IF EXISTS ck_assignment_tasks_verify_url_not_blank;
