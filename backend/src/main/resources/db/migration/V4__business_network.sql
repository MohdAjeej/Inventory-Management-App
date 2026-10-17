-- Business network and trade invoices: businesses connect with each other, a seller sends an invoice,
-- the buyer approves it and both inventories, the seller's sale and the buyer's purchase are posted together.
-- Additive only: no existing column or table changes meaning.

-- ---------------------------------------------------------------- business identity

ALTER TABLE businesses ADD COLUMN business_code VARCHAR(12);
ALTER TABLE businesses ADD COLUMN tax_id VARCHAR(50);
ALTER TABLE businesses ADD COLUMN discoverable BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE businesses SET business_code = 'B' || UPPER(SUBSTRING(MD5(id::text || clock_timestamp()::text || random()::text), 1, 7))
    WHERE business_code IS NULL;
CREATE UNIQUE INDEX uk_businesses_code ON businesses (business_code);
CREATE INDEX idx_businesses_name ON businesses (LOWER(name));

-- A customer or supplier record can stand for another business on the platform.
ALTER TABLE customers ADD COLUMN linked_business_id BIGINT REFERENCES businesses(id) ON DELETE SET NULL;
ALTER TABLE suppliers ADD COLUMN linked_business_id BIGINT REFERENCES businesses(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uk_customers_linked ON customers (business_id, linked_business_id) WHERE linked_business_id IS NOT NULL;
CREATE UNIQUE INDEX uk_suppliers_linked ON suppliers (business_id, linked_business_id) WHERE linked_business_id IS NOT NULL;

-- Stock sent back to a supplier (buyer side of a trade return).
ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_movement_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_movement_type_check CHECK (movement_type IN
    ('OPENING','ADD','REDUCE','ADJUST','TRANSFER_OUT','TRANSFER_IN','COUNT_ADJUST','DAMAGE','RESERVE','RELEASE',
     'SALE','SALE_CANCEL','RETURN_IN','PURCHASE','PURCHASE_CANCEL','PURCHASE_RETURN'));

-- ---------------------------------------------------------------- connections

CREATE TABLE business_connections (
    id                    BIGSERIAL PRIMARY KEY,
    requester_business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    addressee_business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    status                VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','REMOVED')),
    message               VARCHAR(300),
    requested_by          BIGINT NOT NULL REFERENCES memberships(id),
    responded_by          BIGINT REFERENCES memberships(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at          TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    CHECK (requester_business_id <> addressee_business_id)
);
-- At most one live (pending or accepted) connection between two businesses, whoever asked first.
CREATE UNIQUE INDEX uk_connections_live ON business_connections
    (LEAST(requester_business_id, addressee_business_id), GREATEST(requester_business_id, addressee_business_id))
    WHERE status IN ('PENDING','ACCEPTED');
CREATE INDEX idx_connections_requester ON business_connections (requester_business_id, status);
CREATE INDEX idx_connections_addressee ON business_connections (addressee_business_id, status);

-- ---------------------------------------------------------------- trade invoices

CREATE TABLE trade_invoices (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_number      VARCHAR(40) NOT NULL,
    seller_business_id  BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    buyer_business_id   BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','POSTED','REJECTED','CANCELLED')),
    invoice_date        DATE NOT NULL,
    notes               VARCHAR(1000),
    delivery_notes      VARCHAR(1000),
    seller_warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
    buyer_warehouse_id  BIGINT REFERENCES warehouses(id),
    subtotal            NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18,2) NOT NULL DEFAULT 0,
    total               NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_by          BIGINT NOT NULL REFERENCES memberships(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    responded_by        BIGINT REFERENCES memberships(id),
    responded_at        TIMESTAMPTZ,
    rejection_reason    VARCHAR(300),
    cancel_reason       VARCHAR(300),
    seller_sale_id      BIGINT UNIQUE REFERENCES sales(id),
    buyer_purchase_id   BIGINT UNIQUE REFERENCES purchases(id),
    client_request_id   UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    CHECK (seller_business_id <> buyer_business_id),
    CONSTRAINT uq_trade_invoice_number UNIQUE (seller_business_id, invoice_number),
    CONSTRAINT uq_trade_invoice_request UNIQUE (seller_business_id, client_request_id)
);
CREATE INDEX idx_trade_invoices_seller ON trade_invoices (seller_business_id, status, created_at DESC);
CREATE INDEX idx_trade_invoices_buyer ON trade_invoices (buyer_business_id, status, created_at DESC);

-- Each line is a snapshot of the seller's product at the time of sending, so later catalogue edits
-- never change what was invoiced.
CREATE TABLE trade_invoice_lines (
    id                BIGSERIAL PRIMARY KEY,
    invoice_id        BIGINT NOT NULL REFERENCES trade_invoices(id) ON DELETE CASCADE,
    line_no           INTEGER NOT NULL,
    seller_variant_id BIGINT NOT NULL REFERENCES product_variants(id),
    product_name      VARCHAR(200) NOT NULL,
    variant_name      VARCHAR(200) NOT NULL,
    default_variant   BOOLEAN NOT NULL DEFAULT TRUE,
    sku               VARCHAR(80),
    barcode           VARCHAR(80),
    brand             VARCHAR(120),
    model             VARCHAR(120),
    category_name     VARCHAR(200),
    description       VARCHAR(2000),
    attributes        JSONB NOT NULL DEFAULT '{}'::jsonb,
    variant_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    unit_code         VARCHAR(20) NOT NULL,
    unit_name         VARCHAR(80) NOT NULL,
    unit_decimals     INTEGER NOT NULL DEFAULT 0,
    quantity          NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_price        NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    gross_amount      NUMERIC(18,2) NOT NULL,
    discount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount        NUMERIC(18,2) NOT NULL,
    tax_rate          NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total        NUMERIC(18,2) NOT NULL,
    buyer_variant_id  BIGINT REFERENCES product_variants(id),
    returned_quantity NUMERIC(18,3) NOT NULL DEFAULT 0,
    CONSTRAINT uq_trade_line_no UNIQUE (invoice_id, line_no)
);

-- ---------------------------------------------------------------- returns

CREATE TABLE trade_returns (
    id                    BIGSERIAL PRIMARY KEY,
    invoice_id            BIGINT NOT NULL REFERENCES trade_invoices(id) ON DELETE CASCADE,
    return_number         VARCHAR(40) NOT NULL,
    status                VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','ACCEPTED','REJECTED','CANCELLED')),
    reason                VARCHAR(300) NOT NULL,
    notes                 VARCHAR(1000),
    total                 NUMERIC(18,2) NOT NULL DEFAULT 0,
    requested_by          BIGINT NOT NULL REFERENCES memberships(id),
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_by          BIGINT REFERENCES memberships(id),
    responded_at          TIMESTAMPTZ,
    response_note         VARCHAR(300),
    seller_sale_return_id BIGINT UNIQUE REFERENCES sale_returns(id),
    client_request_id     UUID,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trade_return_request UNIQUE (invoice_id, client_request_id)
);
CREATE INDEX idx_trade_returns_invoice ON trade_returns (invoice_id);

CREATE TABLE trade_return_lines (
    id              BIGSERIAL PRIMARY KEY,
    return_id       BIGINT NOT NULL REFERENCES trade_returns(id) ON DELETE CASCADE,
    invoice_line_id BIGINT NOT NULL REFERENCES trade_invoice_lines(id),
    quantity        NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    amount          NUMERIC(18,2) NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------- product links

-- Which of the buyer's own products a seller's product is received into. Seller product ids are only
-- meaningful together with the seller business.
CREATE TABLE product_mappings (
    id                 BIGSERIAL PRIMARY KEY,
    buyer_business_id  BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    seller_business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    seller_variant_id  BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    buyer_variant_id   BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_mapping UNIQUE (buyer_business_id, seller_variant_id)
);

-- ---------------------------------------------------------------- shared history

CREATE TABLE trade_events (
    id            BIGSERIAL PRIMARY KEY,
    invoice_id    BIGINT NOT NULL REFERENCES trade_invoices(id) ON DELETE CASCADE,
    return_id     BIGINT REFERENCES trade_returns(id) ON DELETE CASCADE,
    action        VARCHAR(40) NOT NULL,
    business_id   BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    membership_id BIGINT REFERENCES memberships(id),
    note          VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trade_events_invoice ON trade_events (invoice_id, created_at);
