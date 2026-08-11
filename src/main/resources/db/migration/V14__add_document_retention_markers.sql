ALTER TABLE translation_jobs
    ADD COLUMN source_deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN result_deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE translation_jobs
    ADD CONSTRAINT chk_translation_jobs_source_deleted_terminal
        CHECK (
            source_deleted_at IS NULL
            OR status IN ('DONE', 'FAILED')
        ),
    ADD CONSTRAINT chk_translation_jobs_result_deleted_done
        CHECK (
            result_deleted_at IS NULL
            OR (status = 'DONE' AND result_file_key IS NOT NULL)
        );

CREATE INDEX idx_translation_jobs_source_cleanup
    ON translation_jobs (status, updated_at, id)
    WHERE source_deleted_at IS NULL;

CREATE INDEX idx_translation_jobs_result_cleanup
    ON translation_jobs (updated_at, id)
    WHERE status = 'DONE'
      AND result_file_key IS NOT NULL
      AND result_deleted_at IS NULL;
