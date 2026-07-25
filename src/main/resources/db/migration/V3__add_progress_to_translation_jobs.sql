ALTER TABLE translation_jobs
    ADD COLUMN progress INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_translation_jobs_progress
        CHECK (progress BETWEEN 0 AND 100);