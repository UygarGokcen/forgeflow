-- Purchase orders: what a shop uses to actually restock a material a supplier, instead of
-- `materials/low-stock` only reporting that something is running short.

CREATE TABLE purchase_orders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    po_number      VARCHAR(50) NOT NULL,
    supplier_name  VARCHAR(255) NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'RECEIVED', 'CANCELLED')),
    created_by     UUID NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_orders_tenant_po_number UNIQUE (tenant_id, po_number)
);

CREATE TABLE purchase_order_line_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    purchase_order_id   UUID NOT NULL REFERENCES purchase_orders (id),
    material_id         UUID NOT NULL REFERENCES materials (id),
    quantity_ordered     NUMERIC(19, 4) NOT NULL CHECK (quantity_ordered > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_po_line_items_po_material UNIQUE (tenant_id, purchase_order_id, material_id)
);

CREATE INDEX idx_purchase_orders_tenant_id ON purchase_orders (tenant_id);
CREATE INDEX idx_po_line_items_tenant_po ON purchase_order_line_items (tenant_id, purchase_order_id);

ALTER TABLE purchase_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_orders FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON purchase_orders
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE purchase_order_line_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_line_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON purchase_order_line_items
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);
