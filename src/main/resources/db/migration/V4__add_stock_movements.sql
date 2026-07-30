-- An append-only ledger of every change to a material's stock, so stock can be audited and
-- rebuilt instead of only being a single running total on `materials`.

CREATE TABLE stock_movements (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants (id),
    material_id      UUID NOT NULL REFERENCES materials (id),
    quantity_delta   NUMERIC(19, 4) NOT NULL CHECK (quantity_delta <> 0),
    balance_after    NUMERIC(19, 4) NOT NULL CHECK (balance_after >= 0),
    reason           VARCHAR(30) NOT NULL
        CHECK (reason IN ('INITIAL_STOCK', 'MANUAL_ADJUSTMENT', 'CONSUMPTION')),
    reference_id     UUID,
    note             VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movements_tenant_material ON stock_movements (tenant_id, material_id, created_at DESC);

ALTER TABLE stock_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movements FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON stock_movements
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);
