-- Durable fixed-window rate-limit counters. bucket_key is an HMAC-SHA-256 digest of the limiter key and window,
-- never a raw IP address, email or user id.
CREATE TABLE rate_limit_buckets (
    bucket_key  BYTEA       PRIMARY KEY,
    hit_count   INTEGER     NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT rate_limit_buckets_key_length CHECK (OCTET_LENGTH(bucket_key) = 32),
    CONSTRAINT rate_limit_buckets_count_positive CHECK (hit_count > 0)
);

CREATE INDEX idx_rate_limit_buckets_expires_at ON rate_limit_buckets (expires_at);
