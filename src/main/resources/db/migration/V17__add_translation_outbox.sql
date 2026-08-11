CREATE TABLE translation_outbox_events (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE
        REFERENCES translation_jobs (id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_translation_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'EXHAUSTED')),
    CONSTRAINT chk_translation_outbox_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_translation_outbox_state
        CHECK (
            (status = 'PENDING' AND locked_until IS NULL AND published_at IS NULL)
            OR (status = 'PUBLISHING' AND locked_until IS NOT NULL AND published_at IS NULL)
            OR (status = 'PUBLISHED' AND locked_until IS NULL AND published_at IS NOT NULL)
            OR (status = 'EXHAUSTED' AND locked_until IS NULL AND published_at IS NULL)
        )
);

CREATE INDEX idx_translation_outbox_claimable
    ON translation_outbox_events (status, available_at, locked_until)
    WHERE status IN ('PENDING', 'PUBLISHING');
