-- Minimal sales recording (for sales analytics), business timezone/currency,
-- optional default selling price and idempotency keys for stock submissions.

ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_movement_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_movement_type_check CHECK (movement_type IN
    ('OPENING','ADD','REDUCE','ADJUST','TRANSFER_OUT','TRANSFER_IN','COUNT_ADJUST',
     'DAMAGE','RESERVE','RELEASE','SALE','SALE_CANCEL'));

-- Set by the app once per form submission so a retried or double-tapped request is applied only once.
ALTER TABLE stock_movements ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX uq_movements_client_request ON stock_movements (business_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

ALTER TABLE business_settings ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
ALTER TABLE business_settings ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'INR';

ALTER TABLE products ADD COLUMN sale_price NUMERIC(18,2) CHECK (sale_price IS NULL OR sale_price >= 0);

CREATE TABLE sales (
    id                 BIGSERIAL PRIMARY KEY,
    business_id        BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    product_id         BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    variant_id         BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    warehouse_id       BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    batch_id           BIGINT REFERENCES batches(id) ON DELETE RESTRICT,
    quantity           NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_price         NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    total              NUMERIC(18,2) NOT NULL CHECK (total >= 0),
    status             VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED','CANCELLED')),
    sold_at            TIMESTAMPTZ NOT NULL,
    notes              VARCHAR(1000),
    actor_id           BIGINT NOT NULL REFERENCES memberships(id) ON DELETE RESTRICT,
    movement_id        BIGINT NOT NULL REFERENCES stock_movements(id) ON DELETE RESTRICT,
    cancel_movement_id BIGINT REFERENCES stock_movements(id) ON DELETE RESTRICT,
    cancel_reason      VARCHAR(200),
    cancelled_at       TIMESTAMPTZ,
    cancelled_by       BIGINT REFERENCES memberships(id) ON DELETE RESTRICT,
    client_request_id  UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sales_client_request UNIQUE (business_id, client_request_id)
);
CREATE INDEX idx_sales_business_date ON sales (business_id, sold_at DESC);
CREATE INDEX idx_sales_business_product_date ON sales (business_id, product_id, sold_at);
