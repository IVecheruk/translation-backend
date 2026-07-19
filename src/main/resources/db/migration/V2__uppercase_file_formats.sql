ALTER TABLE translation_jobs
    DROP CONSTRAINT chk_translation_jobs_format;

UPDATE translation_jobs
SET file_format = UPPER(file_format);

ALTER TABLE translation_jobs
    ADD CONSTRAINT chk_translation_jobs_format
        CHECK (file_format IN ('DOCX', 'DOC', 'PDF'));