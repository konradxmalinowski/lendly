CREATE TABLE categories (
    category_key VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT categories_key_format CHECK (category_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT categories_display_name_not_blank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT categories_sort_order_nonnegative CHECK (sort_order >= 0)
);

CREATE INDEX idx_categories_active_sort_order
    ON categories (sort_order, category_key)
    WHERE is_active = TRUE;

INSERT INTO categories (category_key, display_name, sort_order) VALUES
    ('tools_and_diy', 'Narzędzia i majsterkowanie', 10),
    ('gardening', 'Ogród i ogrodnictwo', 20),
    ('camping_outdoors', 'Camping i turystyka', 30),
    ('sports', 'Sport i rekreacja', 40),
    ('electronics_events', 'Elektronika i sprzęt na wydarzenia', 50),
    ('household_appliances', 'Sprzęt domowy', 60),
    ('other', 'Pozostałe', 1000);
