-- V7: Long-lived refresh tokens, so a short-lived access token can be renewed without logging in
-- again. Only a SHA-256 hash of each token is stored. Every refresh replaces the token with a new
-- one in the same family (one family per login); presenting an already-replaced token revokes the
-- whole family, since it means the token was copied.

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id  VARCHAR(36) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON refresh_tokens(expires_at);
