-- =====================================================================
-- Universal multi-tenant inventory platform - initial schema
-- Every tenant-owned table carries business_id NOT NULL.
-- =====================================================================

-- ---------- Tenancy & identity ----------
CREATE TABLE businesses (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    business_type VARCHAR(120),
    logo_url      VARCHAR(500),
    email         VARCHAR(200),
    phone         VARCHAR(50),
    address       VARCHAR(500),
    city          VARCHAR(120),
    state         VARCHAR(120),
    country       VARCHAR(120),
    postal_code   VARCHAR(30),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE business_settings (
    business_id                 BIGINT PRIMARY KEY REFERENCES businesses(id) ON DELETE CASCADE,
    allow_negative_stock        BOOLEAN NOT NULL DEFAULT FALSE,
    default_low_stock_threshold INTEGER NOT NULL DEFAULT 5 CHECK (default_low_stock_threshold >= 0),
    track_batches               BOOLEAN NOT NULL DEFAULT FALSE,
    track_expiry                BOOLEAN NOT NULL DEFAULT FALSE,
    track_serials               BOOLEAN NOT NULL DEFAULT FALSE,
    track_damaged_reserved      BOOLEAN NOT NULL DEFAULT FALSE,
    notify_low_stock            BOOLEAN NOT NULL DEFAULT TRUE,
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(200) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    phone         VARCHAR(50),
    password_hash VARCHAR(100) NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_users_email ON users (lower(email));

CREATE TABLE memberships (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN','INVENTORY_MANAGER','STAFF')),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DEACTIVATED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_membership_user_business UNIQUE (business_id, user_id)
);
CREATE INDEX idx_memberships_user ON memberships (user_id);

-- Per-membership overrides on top of role defaults
CREATE TABLE membership_permissions (
    membership_id BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    permission    VARCHAR(50) NOT NULL,
    granted       BOOLEAN NOT NULL,
    PRIMARY KEY (membership_id, permission)
);

CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    membership_id BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    token_hash    VARCHAR(100) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_membership ON refresh_tokens (membership_id);

CREATE TABLE invitations (
    id            BIGSERIAL PRIMARY KEY,
    business_id   BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    email         VARCHAR(200) NOT NULL,
    role          VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN','INVENTORY_MANAGER','STAFF')),
    code          VARCHAR(20) NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','CANCELLED','EXPIRED')),
    invited_by_id BIGINT REFERENCES memberships(id) ON DELETE SET NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at   TIMESTAMPTZ,
    version       BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_invitations_business ON invitations (business_id, status);

-- ---------- Catalog configuration ----------
CREATE TABLE units (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    decimals    INTEGER NOT NULL DEFAULT 0 CHECK (decimals BETWEEN 0 AND 4),
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_units_business_code ON units (business_id, lower(code));

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    parent_id   BIGINT REFERENCES categories(id) ON DELETE RESTRICT,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_categories_business ON categories (business_id);
CREATE UNIQUE INDEX uk_categories_sibling_name
    ON categories (business_id, COALESCE(parent_id, 0), lower(name));

CREATE TABLE attribute_definitions (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE, -- NULL = applies to all products
    attr_key    VARCHAR(60) NOT NULL,
    label       VARCHAR(120) NOT NULL,
    field_type  VARCHAR(20) NOT NULL CHECK (field_type IN ('TEXT','NUMBER','DATE','BOOLEAN','DROPDOWN','MULTI_SELECT')),
    required    BOOLEAN NOT NULL DEFAULT FALSE,
    options     JSONB NOT NULL DEFAULT '[]'::jsonb,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_attr_defs_business ON attribute_definitions (business_id, category_id);
CREATE UNIQUE INDEX uk_attr_defs_key
    ON attribute_definitions (business_id, COALESCE(category_id, 0), lower(attr_key));

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    business_id   BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    category_id   BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    base_unit_id  BIGINT NOT NULL REFERENCES units(id),
    name          VARCHAR(200) NOT NULL,
    brand         VARCHAR(120),
    model         VARCHAR(120),
    description   VARCHAR(2000),
    min_stock     INTEGER CHECK (min_stock IS NULL OR min_stock >= 0),
    attributes    JSONB NOT NULL DEFAULT '{}'::jsonb,
    track_batches BOOLEAN NOT NULL DEFAULT FALSE,
    track_serials BOOLEAN NOT NULL DEFAULT FALSE,
    archived      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_products_business ON products (business_id, archived);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_name ON products (business_id, lower(name));
CREATE INDEX idx_products_attributes ON products USING GIN (attributes);

CREATE TABLE unit_conversions (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    from_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    to_unit_id   BIGINT NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    product_id   BIGINT REFERENCES products(id) ON DELETE CASCADE, -- NULL = general conversion
    factor       NUMERIC(18,6) NOT NULL CHECK (factor > 0),
    version      BIGINT NOT NULL DEFAULT 0,
    CHECK (from_unit_id <> to_unit_id)
);
CREATE UNIQUE INDEX uk_unit_conversion
    ON unit_conversions (business_id, from_unit_id, to_unit_id, COALESCE(product_id, 0));

CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_images_product ON product_images (product_id);

CREATE TABLE product_variants (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    sku         VARCHAR(80),
    barcode     VARCHAR(80),
    attributes  JSONB NOT NULL DEFAULT '{}'::jsonb,
    min_stock   INTEGER CHECK (min_stock IS NULL OR min_stock >= 0),
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_variants_product ON product_variants (product_id);
CREATE UNIQUE INDEX uk_variants_sku ON product_variants (business_id, lower(sku)) WHERE sku IS NOT NULL;
CREATE UNIQUE INDEX uk_variants_barcode ON product_variants (business_id, barcode) WHERE barcode IS NOT NULL;

-- ---------- Locations ----------
CREATE TABLE warehouses (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    code        VARCHAR(30) NOT NULL,
    address     VARCHAR(500),
    is_branch   BOOLEAN NOT NULL DEFAULT FALSE,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_warehouses_code ON warehouses (business_id, lower(code));

CREATE TABLE storage_locations (
    id           BIGSERIAL PRIMARY KEY,
    business_id  BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    code         VARCHAR(40) NOT NULL,
    description  VARCHAR(200),
    archived     BOOLEAN NOT NULL DEFAULT FALSE,
    version      BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_storage_locations ON storage_locations (warehouse_id, lower(code));

-- ---------- Stock ----------
CREATE TABLE batches (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    variant_id  BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    lot_number  VARCHAR(80) NOT NULL,
    expiry_date DATE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_batches ON batches (variant_id, lower(lot_number));

CREATE TABLE stock_balances (
    id           BIGSERIAL PRIMARY KEY,
    business_id  BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    variant_id   BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    batch_id     BIGINT REFERENCES batches(id) ON DELETE RESTRICT,
    on_hand      NUMERIC(18,3) NOT NULL DEFAULT 0,
    reserved     NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    damaged      NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (damaged >= 0),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_stock_balances
    ON stock_balances (variant_id, warehouse_id, COALESCE(batch_id, 0));
CREATE INDEX idx_stock_balances_business ON stock_balances (business_id);
CREATE INDEX idx_stock_balances_warehouse ON stock_balances (warehouse_id);

CREATE TABLE serial_numbers (
    id           BIGSERIAL PRIMARY KEY,
    business_id  BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    variant_id   BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    warehouse_id BIGINT REFERENCES warehouses(id) ON DELETE SET NULL,
    serial       VARCHAR(120) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'IN_STOCK' CHECK (status IN ('IN_STOCK','OUT','DAMAGED','RESERVED')),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_serial_numbers ON serial_numbers (business_id, variant_id, serial);

CREATE TABLE stock_movements (
    id                BIGSERIAL PRIMARY KEY,
    business_id       BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    variant_id        BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    product_id        BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    warehouse_id      BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    batch_id          BIGINT REFERENCES batches(id) ON DELETE RESTRICT,
    movement_type     VARCHAR(20) NOT NULL CHECK (movement_type IN
                        ('OPENING','ADD','REDUCE','ADJUST','TRANSFER_OUT','TRANSFER_IN','COUNT_ADJUST',
                         'DAMAGE','RESERVE','RELEASE')),
    quantity          NUMERIC(18,3) NOT NULL,
    quantity_before   NUMERIC(18,3) NOT NULL,
    quantity_after    NUMERIC(18,3) NOT NULL,
    reason            VARCHAR(200),
    notes             VARCHAR(1000),
    serials           JSONB,
    actor_id          BIGINT NOT NULL REFERENCES memberships(id) ON DELETE RESTRICT,
    transfer_group_id UUID,
    stock_count_id    BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_movements_business_date ON stock_movements (business_id, created_at DESC);
CREATE INDEX idx_movements_variant ON stock_movements (variant_id, created_at DESC);
CREATE INDEX idx_movements_product ON stock_movements (product_id, created_at DESC);
CREATE INDEX idx_movements_warehouse ON stock_movements (warehouse_id);

-- Movements are append-only: block UPDATE and DELETE at the database level.
CREATE FUNCTION forbid_stock_movement_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stock_movements_immutable
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_stock_movement_change();

CREATE TABLE stock_counts (
    id            BIGSERIAL PRIMARY KEY,
    business_id   BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    warehouse_id  BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RECONCILED','CANCELLED')),
    notes         VARCHAR(1000),
    created_by_id BIGINT NOT NULL REFERENCES memberships(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reconciled_at TIMESTAMPTZ,
    version       BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_stock_counts_business ON stock_counts (business_id, status);

CREATE TABLE stock_count_lines (
    id               BIGSERIAL PRIMARY KEY,
    stock_count_id   BIGINT NOT NULL REFERENCES stock_counts(id) ON DELETE CASCADE,
    variant_id       BIGINT NOT NULL REFERENCES product_variants(id),
    batch_id         BIGINT REFERENCES batches(id),
    expected_qty     NUMERIC(18,3) NOT NULL,
    counted_qty      NUMERIC(18,3)
);
CREATE UNIQUE INDEX uk_count_line ON stock_count_lines (stock_count_id, variant_id, COALESCE(batch_id, 0));

ALTER TABLE stock_movements
    ADD CONSTRAINT fk_movements_stock_count FOREIGN KEY (stock_count_id) REFERENCES stock_counts(id);

-- ---------- Events ----------
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    business_id  BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    recipient_id BIGINT REFERENCES memberships(id) ON DELETE CASCADE, -- NULL = all members
    type         VARCHAR(40) NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         VARCHAR(1000),
    payload      JSONB NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key   VARCHAR(120),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_dedupe ON notifications (business_id, dedupe_key, created_at);
CREATE INDEX idx_notifications_business ON notifications (business_id, created_at DESC);

-- Read state is per member so broadcast notifications work.
CREATE TABLE notification_reads (
    notification_id BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    membership_id   BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    read_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, membership_id)
);

CREATE TABLE audit_events (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    actor_id    BIGINT REFERENCES memberships(id) ON DELETE SET NULL,
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id   BIGINT,
    details     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_business_date ON audit_events (business_id, created_at DESC);
