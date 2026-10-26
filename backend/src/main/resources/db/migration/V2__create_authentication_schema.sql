CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(255),
    display_name VARCHAR(80) NOT NULL,
    avatar_url VARCHAR(2048),
    area_label VARCHAR(120),
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_email_normalized CHECK (
        email = LOWER(BTRIM(email))
        AND email !~ '[[:space:]]'
        AND CHAR_LENGTH(email) BETWEEN 3 AND 254
        AND POSITION('@' IN email) > 1
    ),
    CONSTRAINT users_password_hash_not_blank CHECK (
        password_hash IS NULL OR LENGTH(BTRIM(password_hash)) > 0
    ),
    CONSTRAINT users_display_name_valid CHECK (
        CHAR_LENGTH(BTRIM(display_name)) BETWEEN 2 AND 80
        AND display_name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT users_area_label_valid CHECK (
        area_label IS NULL
        OR CHAR_LENGTH(BTRIM(area_label)) BETWEEN 2 AND 120
    ),
    CONSTRAINT users_role_allowed CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    CONSTRAINT users_status_allowed CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX uq_users_email_normalized
    ON users (email);

CREATE TABLE oauth_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(254),
    provider_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oauth_identities_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT oauth_identities_provider_allowed CHECK (provider = 'GOOGLE'),
    CONSTRAINT oauth_identities_subject_not_blank CHECK (
        LENGTH(BTRIM(provider_subject)) BETWEEN 1 AND 255
    ),
    CONSTRAINT oauth_identities_verified_email_present CHECK (
        NOT provider_email_verified OR provider_email IS NOT NULL
    ),
    CONSTRAINT uq_oauth_identities_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_oauth_identities_user_id
    ON oauth_identities (user_id);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    csrf_token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT auth_sessions_csrf_hash_length CHECK (OCTET_LENGTH(csrf_token_hash) = 32),
    CONSTRAINT auth_sessions_expiry_valid CHECK (
        expires_at > created_at
        AND expires_at <= created_at + INTERVAL '30 days'
    ),
    CONSTRAINT auth_sessions_revocation_valid CHECK (
        revoked_at IS NULL OR revoked_at >= created_at
    )
);

CREATE INDEX idx_auth_sessions_user_id
    ON auth_sessions (user_id);

CREATE INDEX idx_auth_sessions_active_user_expiry
    ON auth_sessions (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_session_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    replaced_by_id UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_sessions_auth_session
        FOREIGN KEY (auth_session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE,
    CONSTRAINT refresh_sessions_token_hash_length CHECK (OCTET_LENGTH(token_hash) = 32),
    CONSTRAINT refresh_sessions_expiry_valid CHECK (expires_at > created_at),
    CONSTRAINT refresh_sessions_consumed_valid CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    ),
    CONSTRAINT refresh_sessions_replacement_valid CHECK (
        replaced_by_id IS NULL OR (replaced_by_id <> id AND consumed_at IS NOT NULL)
    ),
    CONSTRAINT uq_refresh_sessions_auth_session_id_id UNIQUE (auth_session_id, id),
    CONSTRAINT uq_refresh_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_sessions_replacement
        FOREIGN KEY (auth_session_id, replaced_by_id)
        REFERENCES refresh_sessions (auth_session_id, id)
);

CREATE INDEX idx_refresh_sessions_auth_session_id
    ON refresh_sessions (auth_session_id);

CREATE INDEX idx_refresh_sessions_expiry
    ON refresh_sessions (expires_at);

CREATE UNIQUE INDEX uq_refresh_sessions_current_per_family
    ON refresh_sessions (auth_session_id)
    WHERE consumed_at IS NULL;

CREATE TABLE csrf_contexts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT csrf_contexts_token_hash_length CHECK (OCTET_LENGTH(token_hash) = 32),
    CONSTRAINT csrf_contexts_expiry_valid CHECK (expires_at > created_at),
    CONSTRAINT csrf_contexts_consumed_valid CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    ),
    CONSTRAINT uq_csrf_contexts_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_csrf_contexts_expiry
    ON csrf_contexts (expires_at);

CREATE INDEX idx_csrf_contexts_consumed_at
    ON csrf_contexts (consumed_at)
    WHERE consumed_at IS NOT NULL;
