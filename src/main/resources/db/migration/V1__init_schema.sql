-- ForgeFlow initial schema: multi-tenant core tables + row level security.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================================
-- Tables
-- =========================================================================

CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    role           VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN', 'SALES_REP')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE TABLE products (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants (id),
    sku              VARCHAR(100) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    base_unit_price  NUMERIC(19, 4) NOT NULL CHECK (base_unit_price >= 0),
    unit_of_measure  VARCHAR(30) NOT NULL
        CHECK (unit_of_measure IN ('PIECE', 'SQUARE_METER', 'LINEAR_METER', 'KILOGRAM')),
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku)
);

CREATE TABLE pricing_rules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    product_id     UUID NOT NULL REFERENCES products (id),
    strategy_type  VARCHAR(30) NOT NULL
        CHECK (strategy_type IN ('VOLUME_DISCOUNT', 'AREA_BASED', 'FIXED')),
    config         JSONB NOT NULL DEFAULT '{}'::jsonb,
    priority       INTEGER NOT NULL DEFAULT 0,
    is_active      BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE quotes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    quote_number   VARCHAR(50) NOT NULL,
    customer_name  VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255),
    status         VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'CONVERTED_TO_ORDER', 'REJECTED')),
    created_by     UUID NOT NULL REFERENCES users (id),
    total_amount   NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_quotes_tenant_number UNIQUE (tenant_id, quote_number)
);

CREATE TABLE quote_line_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants (id),
    quote_id    UUID NOT NULL REFERENCES quotes (id),
    product_id  UUID NOT NULL REFERENCES products (id),
    quantity    NUMERIC(19, 4) NOT NULL CHECK (quantity > 0),
    width       NUMERIC(19, 4),
    height      NUMERIC(19, 4),
    unit_price  NUMERIC(19, 4) NOT NULL CHECK (unit_price >= 0),
    line_total  NUMERIC(19, 4) NOT NULL CHECK (line_total >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================================================
-- Indexes
-- =========================================================================

CREATE INDEX idx_users_tenant_id ON users (tenant_id);
CREATE INDEX idx_products_tenant_id ON products (tenant_id);
CREATE INDEX idx_products_tenant_active ON products (tenant_id, is_active);
CREATE INDEX idx_pricing_rules_tenant_id ON pricing_rules (tenant_id);
CREATE INDEX idx_pricing_rules_product_id ON pricing_rules (tenant_id, product_id);
CREATE INDEX idx_quotes_tenant_id ON quotes (tenant_id);
CREATE INDEX idx_quotes_tenant_status ON quotes (tenant_id, status);
CREATE INDEX idx_quote_line_items_tenant_id ON quote_line_items (tenant_id);
CREATE INDEX idx_quote_line_items_quote_id ON quote_line_items (tenant_id, quote_id);

-- =========================================================================
-- Row Level Security (tenant isolation)
--
-- The app sets `app.current_tenant` at the start of every transaction (see
-- TenantAwareJpaTransactionManager). The `tenants` table is left out of RLS
-- on purpose, because we look a tenant up before we know which tenant we are.
--
-- FORCE is needed as well as ENABLE. Postgres skips RLS for the table owner
-- by default, and the `forgeflow` role that runs these migrations owns every
-- table here. Without FORCE it would ignore tenant isolation completely.
-- =========================================================================

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON users
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON products
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE pricing_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE pricing_rules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON pricing_rules
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE quotes ENABLE ROW LEVEL SECURITY;
ALTER TABLE quotes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON quotes
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

ALTER TABLE quote_line_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE quote_line_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON quote_line_items
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- =========================================================================
-- Application role
--
-- The `forgeflow` account Docker creates from POSTGRES_USER (the one running
-- these migrations) is a superuser, and superusers ignore row level security
-- no matter what -- FORCE doesn't apply to them either. So the app has to
-- connect as a different role with no special rights, or tenant isolation
-- wouldn't actually work.
--
-- `forgeflow` stays the migration/admin role (spring.flyway.* in
-- application.yml) and the app connects as `forgeflow_app`
-- (spring.datasource.*). The password here is only for local development;
-- a real deployment should create this role separately with a managed secret.
-- =========================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'forgeflow_app') THEN
        CREATE ROLE forgeflow_app LOGIN PASSWORD 'forgeflow_app' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO forgeflow_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO forgeflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO forgeflow_app;
