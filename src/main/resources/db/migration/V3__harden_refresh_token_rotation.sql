ALTER TABLE refresh_tokens
    ADD COLUMN token_id VARCHAR(64) NULL AFTER id,
    ADD COLUMN token_hash VARCHAR(128) NULL AFTER token_id,
    ADD COLUMN expires_at TIMESTAMP NULL AFTER token_hash,
    ADD COLUMN revoked_at TIMESTAMP NULL AFTER expires_at,
    ADD COLUMN replaced_by_token_id VARCHAR(64) NULL AFTER revoked_at;

UPDATE refresh_tokens
SET token_hash = SHA2(token, 256),
    expires_at = COALESCE(expiry_date, CURRENT_TIMESTAMP),
    revoked_at = CASE WHEN revoked = TRUE THEN CURRENT_TIMESTAMP ELSE NULL END
WHERE token_hash IS NULL;

UPDATE refresh_tokens
SET token_id = token_hash
WHERE token_id IS NULL;

ALTER TABLE refresh_tokens
    MODIFY COLUMN token_id VARCHAR(64) NOT NULL,
    MODIFY COLUMN token_hash VARCHAR(128) NOT NULL,
    MODIFY COLUMN expires_at TIMESTAMP NOT NULL;

CREATE UNIQUE INDEX uk_refresh_tokens_token_id ON refresh_tokens(token_id);
CREATE UNIQUE INDEX uk_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_revoked ON refresh_tokens(user_id, revoked_at);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

ALTER TABLE refresh_tokens
    DROP COLUMN token,
    DROP COLUMN expiry_date,
    DROP COLUMN revoked;
