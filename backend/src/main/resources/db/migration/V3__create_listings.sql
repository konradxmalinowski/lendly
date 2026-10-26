CREATE TABLE listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category_key VARCHAR(64) NOT NULL,
    item_condition VARCHAR(16) NOT NULL,
    pricing_type VARCHAR(8) NOT NULL,
    daily_price NUMERIC(10, 2),
    currency CHAR(3) NOT NULL DEFAULT 'PLN',
    handover_lat NUMERIC(9, 6),
    handover_lng NUMERIC(9, 6),
    public_lat NUMERIC(9, 6),
    public_lng NUMERIC(9, 6),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple'::regconfig, title || ' ' || description)
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT listings_owner_fk FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT listings_category_fk FOREIGN KEY (category_key) REFERENCES categories (category_key),
    CONSTRAINT listings_title_length CHECK (char_length(trim(title)) BETWEEN 3 AND 100),
    CONSTRAINT listings_description_length CHECK (char_length(trim(description)) BETWEEN 10 AND 2000),
    CONSTRAINT listings_condition_allowed CHECK (item_condition IN ('NEW', 'VERY_GOOD', 'GOOD', 'FAIR')),
    CONSTRAINT listings_pricing_allowed CHECK (pricing_type IN ('FREE', 'PAID')),
    CONSTRAINT listings_price_matches_type CHECK (
        (pricing_type = 'FREE' AND daily_price IS NULL)
        OR (pricing_type = 'PAID' AND daily_price BETWEEN 0.01 AND 9999.99)
    ),
    CONSTRAINT listings_currency_allowed CHECK (currency = 'PLN'),
    CONSTRAINT listings_handover_pair CHECK ((handover_lat IS NULL) = (handover_lng IS NULL)),
    CONSTRAINT listings_public_pair CHECK ((public_lat IS NULL) = (public_lng IS NULL)
        AND (public_lat IS NULL) = (handover_lat IS NULL)),
    CONSTRAINT listings_handover_range CHECK (handover_lat BETWEEN -90 AND 90 AND handover_lng BETWEEN -180 AND 180),
    CONSTRAINT listings_public_range CHECK (public_lat BETWEEN -90 AND 90 AND public_lng BETWEEN -180 AND 180),
    CONSTRAINT listings_status_allowed CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'HIDDEN')),
    CONSTRAINT listings_active_has_location CHECK (status <> 'ACTIVE' OR public_lat IS NOT NULL),
    CONSTRAINT listings_active_published CHECK (status <> 'ACTIVE' OR published_at IS NOT NULL)
);

CREATE INDEX idx_listings_search_vector ON listings USING GIN (search_vector);
CREATE INDEX idx_listings_status_category_published
    ON listings (status, category_key, published_at DESC);
CREATE INDEX idx_listings_owner_updated ON listings (owner_id, updated_at DESC);
CREATE INDEX idx_listings_active_public_point
    ON listings (public_lat, public_lng)
    WHERE status = 'ACTIVE';

CREATE TABLE listing_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL,
    storage_key VARCHAR(64) NOT NULL,
    content_type VARCHAR(32) NOT NULL DEFAULT 'image/jpeg',
    byte_size INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    display_order SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT listing_images_listing_fk FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT listing_images_storage_key_unique UNIQUE (storage_key),
    CONSTRAINT listing_images_storage_key_format CHECK (storage_key ~ '^[0-9a-f]{32}$'),
    CONSTRAINT listing_images_order_unique UNIQUE (listing_id, display_order),
    CONSTRAINT listing_images_content_type_allowed CHECK (content_type = 'image/jpeg'),
    CONSTRAINT listing_images_size_positive CHECK (byte_size > 0),
    CONSTRAINT listing_images_dimensions_positive CHECK (width > 0 AND height > 0),
    CONSTRAINT listing_images_order_range CHECK (display_order BETWEEN 0 AND 7)
);
