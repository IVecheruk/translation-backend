ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

UPDATE users
SET email_verified_at = created_at
WHERE email_verified = TRUE;

ALTER TABLE users
    ALTER COLUMN email_verified SET DEFAULT FALSE,
    ADD CONSTRAINT chk_users_email_verification
        CHECK (
            (email_verified = FALSE AND email_verified_at IS NULL)
            OR
            (email_verified = TRUE AND email_verified_at IS NOT NULL)
        ),
    ADD CONSTRAINT chk_users_auth_version
        CHECK (auth_version >= 0);

CREATE TABLE account_action_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_account_action_token_type
        CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT chk_account_action_token_hash
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_account_action_token_expiration
        CHECK (expires_at > created_at),
    CONSTRAINT chk_account_action_token_consumption
        CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);

CREATE INDEX idx_account_action_tokens_lookup
    ON account_action_tokens (token_hash)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_account_action_tokens_cleanup
    ON account_action_tokens (expires_at, id);
