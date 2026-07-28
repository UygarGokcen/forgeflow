-- Orders: created automatically when a Quote transitions to CONVERTED_TO_ORDER.
-- One order per quote (quote_id is unique) — line items are not duplicated here,
-- they're read from quote_line_items via quote_id, since an order is simply the
-- confirmed record of a quote rather than an independently editable document.

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
