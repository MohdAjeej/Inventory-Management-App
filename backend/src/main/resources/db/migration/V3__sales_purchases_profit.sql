-- Multi-line sales with invoice numbers, discounts, tax, payments and returns; purchases with
-- suppliers and weighted-average cost; customers. Existing single-line sales are converted to a
-- header plus one line item, so no recorded sale is lost.

ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_movement_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_movement_type_check CHECK (movement_type IN
    ('OPENING','ADD','REDUCE','ADJUST','TRANSFER_OUT','TRANSFER_IN','COUNT_ADJUST','DAMAGE','RESERVE','RELEASE',
     'SALE','SALE_CANCEL','RETURN_IN','PURCHASE','PURCHASE_CANCEL'));

-- ---------------------------------------------------------------- parties

CREATE TABLE suppliers (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(200),
    address     VARCHAR(500),
    tax_id      VARCHAR(50),
    notes       VARCHAR(1000),
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_suppliers_business ON suppliers (business_id, LOWER(name));

CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(200),
    address     VARCHAR(500),
    notes       VARCHAR(1000),
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_customers_business ON customers (business_id, LOWER(name));

-- ---------------------------------------------------------------- product pricing

ALTER TABLE products ADD COLUMN purchase_price NUMERIC(18,2) CHECK (purchase_price IS NULL OR purchase_price >= 0);
-- Weighted-average cost per base unit, maintained when stock is purchased. Used for cost of goods sold.
ALTER TABLE products ADD COLUMN average_cost NUMERIC(18,4) CHECK (average_cost IS NULL OR average_cost >= 0);
ALTER TABLE products ADD COLUMN tax_rate NUMERIC(5,2) CHECK (tax_rate IS NULL OR (tax_rate >= 0 AND tax_rate <= 100));
ALTER TABLE products ADD COLUMN supplier_id BIGINT REFERENCES suppliers(id) ON DELETE SET NULL;

-- Per-business counters for invoice, purchase and return numbers.
CREATE TABLE business_sequences (
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name        VARCHAR(30) NOT NULL,
    next_value  BIGINT NOT NULL,
    PRIMARY KEY (business_id, name)
);

-- ---------------------------------------------------------------- sales: header + items

CREATE TABLE sale_items (
    id                 BIGSERIAL PRIMARY KEY,
    business_id        BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    sale_id            BIGINT NOT NULL REFERENCES sales(id) ON DELETE RESTRICT,
    line_no            INTEGER NOT NULL,
    product_id         BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    variant_id         BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    warehouse_id       BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    batch_id           BIGINT REFERENCES batches(id) ON DELETE RESTRICT,
    quantity           NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_price         NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    gross_amount       NUMERIC(18,2) NOT NULL CHECK (gross_amount >= 0),
    discount           NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    net_amount         NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_rate           NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 100),
    tax_amount         NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    line_total         NUMERIC(18,2) NOT NULL CHECK (line_total >= 0),
    -- Cost snapshot at the time of sale; NULL when the product had no known cost.
    unit_cost          NUMERIC(18,4) CHECK (unit_cost IS NULL OR unit_cost >= 0),
    cost_amount        NUMERIC(18,2),
    returned_quantity  NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0 AND returned_quantity <= quantity),
    serials            JSONB,
    movement_id        BIGINT NOT NULL REFERENCES stock_movements(id) ON DELETE RESTRICT,
    cancel_movement_id BIGINT REFERENCES stock_movements(id) ON DELETE RESTRICT,
    CHECK (discount <= gross_amount)
);
CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);
CREATE INDEX idx_sale_items_business_product ON sale_items (business_id, product_id);

INSERT INTO sale_items (business_id, sale_id, line_no, product_id, variant_id, warehouse_id, batch_id, quantity, unit_price,
                        gross_amount, discount, net_amount, tax_rate, tax_amount, line_total, unit_cost, cost_amount,
                        movement_id, cancel_movement_id)
SELECT business_id, id, 1, product_id, variant_id, warehouse_id, batch_id, quantity, unit_price,
       total, 0, total, 0, 0, total, NULL, NULL, movement_id, cancel_movement_id
FROM sales;

ALTER TABLE sales ADD COLUMN invoice_number VARCHAR(40);
UPDATE sales SET invoice_number = 'INV-' || LPAD(id::text, 5, '0');
ALTER TABLE sales ALTER COLUMN invoice_number SET NOT NULL;
CREATE UNIQUE INDEX uq_sales_invoice ON sales (business_id, LOWER(invoice_number));

ALTER TABLE sales ADD COLUMN customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL;
ALTER TABLE sales ADD COLUMN customer_name VARCHAR(200);
ALTER TABLE sales ADD COLUMN customer_phone VARCHAR(50);
ALTER TABLE sales ADD COLUMN subtotal NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN discount_total NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN tax_total NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN cost_total NUMERIC(18,2);
-- Value of goods returned (credited back to the customer) and net money received (payments minus refunds).
ALTER TABLE sales ADD COLUMN returned_total NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN amount_paid NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID'
    CHECK (payment_status IN ('PAID','PARTIALLY_PAID','UNPAID'));
UPDATE sales SET subtotal = total, amount_paid = total, payment_status = 'PAID';

ALTER TABLE sales DROP COLUMN product_id;
ALTER TABLE sales DROP COLUMN variant_id;
ALTER TABLE sales DROP COLUMN warehouse_id;
ALTER TABLE sales DROP COLUMN batch_id;
ALTER TABLE sales DROP COLUMN quantity;
ALTER TABLE sales DROP COLUMN unit_price;
ALTER TABLE sales DROP COLUMN movement_id;
ALTER TABLE sales DROP COLUMN cancel_movement_id;
CREATE INDEX idx_sales_business_customer ON sales (business_id, customer_id);

-- ---------------------------------------------------------------- returns

CREATE TABLE sale_returns (
    id                BIGSERIAL PRIMARY KEY,
    business_id       BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    sale_id           BIGINT NOT NULL REFERENCES sales(id) ON DELETE RESTRICT,
    return_number     VARCHAR(40) NOT NULL,
    returned_at       TIMESTAMPTZ NOT NULL,
    reason            VARCHAR(200) NOT NULL,
    credit_amount     NUMERIC(18,2) NOT NULL CHECK (credit_amount >= 0),
    refund_amount     NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
    refund_method     VARCHAR(30),
    notes             VARCHAR(1000),
    actor_id          BIGINT NOT NULL REFERENCES memberships(id) ON DELETE RESTRICT,
    client_request_id UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sale_returns_client_request UNIQUE (business_id, client_request_id)
);
CREATE INDEX idx_sale_returns_sale ON sale_returns (sale_id);

CREATE TABLE sale_return_items (
    id           BIGSERIAL PRIMARY KEY,
    return_id    BIGINT NOT NULL REFERENCES sale_returns(id) ON DELETE CASCADE,
    sale_item_id BIGINT NOT NULL REFERENCES sale_items(id) ON DELETE RESTRICT,
    quantity     NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    resalable    BOOLEAN NOT NULL,
    net_amount   NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_amount   NUMERIC(18,2) NOT NULL CHECK (tax_amount >= 0),
    cost_amount  NUMERIC(18,2),
    movement_id  BIGINT REFERENCES stock_movements(id) ON DELETE RESTRICT
);
CREATE INDEX idx_sale_return_items_item ON sale_return_items (sale_item_id);

-- ---------------------------------------------------------------- purchases

CREATE TABLE purchases (
    id                BIGSERIAL PRIMARY KEY,
    business_id       BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    purchase_number   VARCHAR(40) NOT NULL,
    supplier_id       BIGINT REFERENCES suppliers(id) ON DELETE SET NULL,
    supplier_name     VARCHAR(200),
    reference_number  VARCHAR(80),
    purchased_at      TIMESTAMPTZ NOT NULL,
    status            VARCHAR(20) NOT NULL CHECK (status IN ('RECEIVED','CANCELLED')),
    subtotal          NUMERIC(18,2) NOT NULL,
    discount_total    NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_total         NUMERIC(18,2) NOT NULL DEFAULT 0,
    total             NUMERIC(18,2) NOT NULL CHECK (total >= 0),
    amount_paid       NUMERIC(18,2) NOT NULL DEFAULT 0,
    payment_status    VARCHAR(20) NOT NULL CHECK (payment_status IN ('PAID','PARTIALLY_PAID','UNPAID')),
    notes             VARCHAR(1000),
    actor_id          BIGINT NOT NULL REFERENCES memberships(id) ON DELETE RESTRICT,
    cancel_reason     VARCHAR(200),
    cancelled_at      TIMESTAMPTZ,
    cancelled_by      BIGINT REFERENCES memberships(id) ON DELETE RESTRICT,
    client_request_id UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchases_client_request UNIQUE (business_id, client_request_id)
);
CREATE UNIQUE INDEX uq_purchases_number ON purchases (business_id, purchase_number);
CREATE INDEX idx_purchases_business_date ON purchases (business_id, purchased_at DESC);

CREATE TABLE purchase_items (
    id                 BIGSERIAL PRIMARY KEY,
    business_id        BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    purchase_id        BIGINT NOT NULL REFERENCES purchases(id) ON DELETE RESTRICT,
    line_no            INTEGER NOT NULL,
    product_id         BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    variant_id         BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    warehouse_id       BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    batch_id           BIGINT REFERENCES batches(id) ON DELETE RESTRICT,
    quantity           NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_cost          NUMERIC(18,2) NOT NULL CHECK (unit_cost >= 0),
    gross_amount       NUMERIC(18,2) NOT NULL,
    discount           NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    net_amount         NUMERIC(18,2) NOT NULL CHECK (net_amount >= 0),
    tax_rate           NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total         NUMERIC(18,2) NOT NULL,
    -- Net cost per base unit after discount (tax excluded); feeds the weighted-average cost.
    landed_unit_cost   NUMERIC(18,4) NOT NULL,
    movement_id        BIGINT NOT NULL REFERENCES stock_movements(id) ON DELETE RESTRICT,
    cancel_movement_id BIGINT REFERENCES stock_movements(id) ON DELETE RESTRICT,
    CHECK (discount <= gross_amount)
);
CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id);
CREATE INDEX idx_purchase_items_business_product ON purchase_items (business_id, product_id);

-- ---------------------------------------------------------------- payments

-- Money in (SALE_PAYMENT), money back to customers (REFUND) and money paid to suppliers (PURCHASE_PAYMENT).
CREATE TABLE payments (
    id                BIGSERIAL PRIMARY KEY,
    business_id       BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    kind              VARCHAR(20) NOT NULL CHECK (kind IN ('SALE_PAYMENT','REFUND','PURCHASE_PAYMENT')),
    sale_id           BIGINT REFERENCES sales(id) ON DELETE RESTRICT,
    purchase_id       BIGINT REFERENCES purchases(id) ON DELETE RESTRICT,
    return_id         BIGINT REFERENCES sale_returns(id) ON DELETE RESTRICT,
    amount            NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    method            VARCHAR(30) NOT NULL,
    reference         VARCHAR(100),
    paid_at           TIMESTAMPTZ NOT NULL,
    actor_id          BIGINT NOT NULL REFERENCES memberships(id) ON DELETE RESTRICT,
    client_request_id UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((sale_id IS NOT NULL) <> (purchase_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_payments_client_request ON payments (business_id, client_request_id) WHERE client_request_id IS NOT NULL;
CREATE INDEX idx_payments_sale ON payments (sale_id);
CREATE INDEX idx_payments_purchase ON payments (purchase_id);

-- Existing sales were treated as paid in full.
INSERT INTO payments (business_id, kind, sale_id, amount, method, paid_at, actor_id)
SELECT business_id, 'SALE_PAYMENT', id, total, 'CASH', sold_at, actor_id FROM sales WHERE total > 0;
