CREATE TABLE email_verification_codes (
    user_id UUID PRIMARY KEY,
    code_hmac BYTEA NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts SMALLINT NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_verification_codes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT email_verification_codes_hmac_length CHECK (OCTET_LENGTH(code_hmac) = 32),
    CONSTRAINT email_verification_codes_attempts_valid CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT email_verification_codes_expiry_valid CHECK (expires_at > created_at)
);

CREATE INDEX idx_email_verification_codes_expiry
    ON email_verification_codes (expires_at);

CREATE TABLE user_mfa (
    user_id UUID PRIMARY KEY,
    secret_ciphertext BYTEA NOT NULL,
    enabled_at TIMESTAMPTZ,
    last_used_step BIGINT,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_mfa_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_mfa_secret_length CHECK (OCTET_LENGTH(secret_ciphertext) BETWEEN 29 AND 128),
    CONSTRAINT user_mfa_last_used_step_valid CHECK (last_used_step IS NULL OR last_used_step >= 0),
    CONSTRAINT user_mfa_failed_attempts_valid CHECK (failed_attempts >= 0),
    CONSTRAINT user_mfa_enabled_valid CHECK (enabled_at IS NULL OR enabled_at >= created_at)
);

CREATE TABLE mfa_recovery_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    code_hash BYTEA NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mfa_recovery_codes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT mfa_recovery_codes_hash_length CHECK (OCTET_LENGTH(code_hash) = 32),
    CONSTRAINT mfa_recovery_codes_used_valid CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT uq_mfa_recovery_codes_user_hash UNIQUE (user_id, code_hash)
);

CREATE INDEX idx_mfa_recovery_codes_user_unused
    ON mfa_recovery_codes (user_id)
    WHERE used_at IS NULL;

CREATE TABLE mfa_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts SMALLINT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT fk_mfa_challenges_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT mfa_challenges_token_hash_length CHECK (OCTET_LENGTH(token_hash) = 32),
    CONSTRAINT mfa_challenges_attempts_valid CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT mfa_challenges_expiry_valid CHECK (expires_at > created_at),
    CONSTRAINT mfa_challenges_consumed_valid CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    CONSTRAINT uq_mfa_challenges_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_mfa_challenges_user_id
    ON mfa_challenges (user_id);

CREATE INDEX idx_mfa_challenges_expiry
    ON mfa_challenges (expires_at);

UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;
