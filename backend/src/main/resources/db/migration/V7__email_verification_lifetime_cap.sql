ALTER TABLE users
    ADD COLUMN verification_failures INT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT users_verification_failures_valid CHECK (verification_failures >= 0);
