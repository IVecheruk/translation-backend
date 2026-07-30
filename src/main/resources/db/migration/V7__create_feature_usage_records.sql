CREATE TABLE feature_usage_records
(
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    feature_code        VARCHAR(64) NOT NULL,
    units               INTEGER NOT NULL,
    period_start        TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end          TIMESTAMP WITH TIME ZONE NOT NULL,
    status              VARCHAR(16) NOT NULL,
    translation_job_id  UUID,
    expires_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_feature_usage_records_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_feature_usage_records_translation_job
        FOREIGN KEY (translation_job_id)
        REFERENCES translation_jobs (id)
        ON DELETE SET NULL,

    CONSTRAINT chk_feature_usage_records_feature_code
        CHECK (
            feature_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
        ),

    CONSTRAINT chk_feature_usage_records_units
        CHECK (
            units > 0
        ),

    CONSTRAINT chk_feature_usage_records_period
        CHECK (
            period_end > period_start
        ),

    CONSTRAINT chk_feature_usage_records_status
        CHECK (
            status IN (
                'RESERVED',
                'CONSUMED',
                'RELEASED'
            )
        ),

    CONSTRAINT chk_feature_usage_records_expiration
        CHECK (
            (
                status = 'RESERVED'
                AND expires_at IS NOT NULL
            )
            OR
            (
                status IN ('CONSUMED', 'RELEASED')
                AND expires_at IS NULL
            )
        )
);

CREATE INDEX idx_feature_usage_records_quota_lookup
    ON feature_usage_records (
        user_id,
        feature_code,
        period_start,
        period_end,
        status
    );

CREATE INDEX idx_feature_usage_records_expired_reservations
    ON feature_usage_records (expires_at)
    WHERE status = 'RESERVED';

CREATE UNIQUE INDEX uk_feature_usage_records_translation_job
    ON feature_usage_records (translation_job_id)
    WHERE translation_job_id IS NOT NULL;