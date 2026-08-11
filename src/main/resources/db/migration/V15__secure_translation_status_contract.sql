ALTER TABLE translation_jobs
    ADD COLUMN expected_result_file_key VARCHAR(1024);

UPDATE translation_jobs
SET expected_result_file_key =
        'results/' || user_id::text || '/' || id::text || '.'
        || lower(file_format);

ALTER TABLE translation_jobs
    ALTER COLUMN expected_result_file_key SET NOT NULL,
    ADD COLUMN error_code VARCHAR(64);

UPDATE translation_jobs
SET error_code = 'TRANSLATION_FAILED',
    error_message = left(
            coalesce(nullif(btrim(error_message), ''), 'Translation failed'),
            2000
    )
WHERE status = 'FAILED';

UPDATE translation_jobs
SET error_code = NULL,
    error_message = NULL
WHERE status <> 'FAILED';

ALTER TABLE translation_jobs
    ALTER COLUMN error_message TYPE VARCHAR(2000)
        USING left(error_message, 2000),
    ADD CONSTRAINT chk_translation_jobs_expected_result_namespace
        CHECK (
            expected_result_file_key LIKE
            'results/' || user_id::text || '/%'
            AND expected_result_file_key NOT LIKE '%..%'
            AND position(E'\\\\' in expected_result_file_key) = 0
        ),
    ADD CONSTRAINT chk_translation_jobs_error_state
        CHECK (
            (
                status = 'FAILED'
                AND error_code IS NOT NULL
                AND error_message IS NOT NULL
                AND btrim(error_message) <> ''
            )
            OR (
                status <> 'FAILED'
                AND error_code IS NULL
                AND error_message IS NULL
            )
        );

CREATE UNIQUE INDEX uq_translation_jobs_expected_result_file_key
    ON translation_jobs (expected_result_file_key);
