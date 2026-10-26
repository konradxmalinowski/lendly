CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    pricing_type VARCHAR(8) NOT NULL,
    daily_price NUMERIC(10, 2),
    currency CHAR(3) NOT NULL DEFAULT 'PLN',
    estimated_cost NUMERIC(10, 2) NOT NULL,
    note VARCHAR(500),
    decision_reason VARCHAR(300),
    cancelled_by VARCHAR(8),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMPTZ,
    handed_over_at TIMESTAMPTZ,
    returned_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT reservations_listing_fk FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT reservations_borrower_fk FOREIGN KEY (borrower_id) REFERENCES users (id),
    CONSTRAINT reservations_dates_ordered CHECK (start_date <= end_date),
    CONSTRAINT reservations_max_length CHECK (end_date - start_date <= 29),
    CONSTRAINT reservations_status_allowed CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'ACTIVE', 'COMPLETED')
    ),
    CONSTRAINT reservations_pricing_allowed CHECK (
        (pricing_type = 'FREE' AND daily_price IS NULL AND estimated_cost = 0)
        OR (pricing_type = 'PAID' AND daily_price > 0 AND estimated_cost > 0)
    ),
    CONSTRAINT reservations_currency_allowed CHECK (currency = 'PLN'),
    CONSTRAINT reservations_cancelled_by_allowed CHECK (
        (status = 'CANCELLED' AND cancelled_by IN ('BORROWER', 'OWNER'))
        OR (status <> 'CANCELLED' AND cancelled_by IS NULL)
    ),
    -- Safety net under the service-level listing lock: active periods of one listing never overlap.
    CONSTRAINT reservations_no_active_overlap EXCLUDE USING gist (
        listing_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status IN ('PENDING', 'ACCEPTED', 'ACTIVE'))
);

CREATE INDEX idx_reservations_listing_status_start ON reservations (listing_id, status, start_date);
CREATE INDEX idx_reservations_borrower_created ON reservations (borrower_id, created_at DESC);
CREATE INDEX idx_reservations_pending_expiry ON reservations (expires_at) WHERE status = 'PENDING';

CREATE TABLE listing_unavailable_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unavailable_listing_fk FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT unavailable_dates_ordered CHECK (start_date <= end_date),
    CONSTRAINT unavailable_max_length CHECK (end_date - start_date <= 179),
    CONSTRAINT unavailable_no_overlap EXCLUDE USING gist (
        listing_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    )
);

CREATE INDEX idx_unavailable_listing_start ON listing_unavailable_periods (listing_id, start_date);
