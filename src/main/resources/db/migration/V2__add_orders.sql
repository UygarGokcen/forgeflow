-- Orders: created automatically when a quote moves to CONVERTED_TO_ORDER.
-- There is one order per quote (quote_id is unique). Line items are not copied
-- here; they are read from quote_line_items using quote_id, because an order is
-- the confirmed record of a quote rather than a separate document you can edit.

CREATE TABLE orders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    quote_id       UUID NOT NULL REFERENCES quotes (id),
    order_number   VARCHAR(50) NOT NULL,
    customer_name  VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255),
    total_amount   NUMERIC(19, 4) NOT NULL,
    created_by     UUID NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_tenant_number UNIQUE (tenant_id, order_number),
    CONSTRAINT uq_orders_quote_id UNIQUE (quote_id)
);

CREATE INDEX idx_orders_tenant_id ON orders (tenant_id);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON orders
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- No explicit GRANT needed here: V1's `ALTER DEFAULT PRIVILEGES ... GRANT ... TO
-- forgeflow_app` already covers tables created later by the same (forgeflow) role.
