CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    auth_version BIGINT NOT NULL CHECK (auth_version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT chk_refresh_session_expiration CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions(user_id);
CREATE INDEX idx_refresh_sessions_expiration ON refresh_sessions(expires_at);

CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    session_id UUID NOT NULL REFERENCES refresh_sessions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_session ON refresh_tokens(session_id);
CREATE UNIQUE INDEX uq_refresh_tokens_active_session ON refresh_tokens(session_id)
    WHERE consumed_at IS NULL;
