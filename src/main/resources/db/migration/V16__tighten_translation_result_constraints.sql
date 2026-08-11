ALTER TABLE translation_jobs
    DROP CONSTRAINT chk_translation_jobs_expected_result_namespace,
    DROP CONSTRAINT chk_translation_jobs_error_state;

ALTER TABLE translation_jobs
    ADD CONSTRAINT chk_translation_jobs_expected_result_namespace
        CHECK (
            expected_result_file_key ~ (
                '^results/' || user_id::text
                || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-'
                || '[0-9a-f]{4}-[0-9a-f]{12}[.]'
                || lower(file_format) || '$'
            )
        ),
    ADD CONSTRAINT chk_translation_jobs_error_state
        CHECK (
            (
                status = 'FAILED'
                AND error_code = 'TRANSLATION_FAILED'
                AND error_message IS NOT NULL
                AND btrim(error_message) <> ''
            )
            OR (
                status <> 'FAILED'
                AND error_code IS NULL
                AND error_message IS NULL
            )
        );
