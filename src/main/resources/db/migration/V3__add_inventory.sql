-- Inventory: raw materials and per-product recipes.
--
-- A custom manufacturer stocks raw material (steel sheet, profile, coil), not finished goods —
-- there is no warehouse of pre-cut 2m x 1.5m panels to draw down. So stock lives on `materials`,
-- and `product_materials` records how much of each material one unit of a product consumes.

CREATE TABLE materials (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants (id),
    sku              VARCHAR(100) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    unit_of_measure  VARCHAR(30) NOT NULL
        CHECK (unit_of_measure IN ('PIECE', 'SQUARE_METER', 'LINEAR_METER', 'KILOGRAM')),
    -- Defense in depth, mirroring how tenant isolation is enforced in the database rather than
    -- trusted to application code: even a bug in the consumption logic cannot drive stock negative.
    stock_quantity   NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    reorder_level    NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_materials_tenant_sku UNIQUE (tenant_id, sku)
);

CREATE TABLE product_materials (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    product_id         UUID NOT NULL REFERENCES products (id),
    material_id        UUID NOT NULL REFERENCES materials (id),
    -- Consumption per "unit" of the product, where a unit is a piece for piece-priced products
    -- and one square meter for area-priced ones — the same dimension basis area-based pricing
    -- uses, so a quote line's price and its material draw stay consistent by construction.
    quantity_per_unit  NUMERIC(19, 4) NOT NULL CHECK (quantity_per_unit > 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_materials_tenant_product_material UNIQUE (tenant_id, product_id, material_id)
);

CREATE INDEX idx_materials_tenant_id ON materials (tenant_id);
CREATE INDEX idx_materials_tenant_reorder ON materials (tenant_id, stock_quantity, reorder_level);
CREATE INDEX idx_product_materials_tenant_id ON product_materials (tenant_id);
CREATE INDEX idx_product_materials_product ON product_materials (tenant_id, product_id);

ALTER TABLE materials ENABLE ROW LEVEL SECURITY;
ALTER TABLE materials FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON materials
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE product_materials ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_materials FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON product_materials
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);
