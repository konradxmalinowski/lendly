ALTER TABLE users DROP CONSTRAINT users_status_allowed;
ALTER TABLE users
    ADD CONSTRAINT users_status_allowed CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));
