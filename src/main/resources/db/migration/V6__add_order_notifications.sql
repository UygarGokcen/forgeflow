-- The real consumer for forgeflow.order-events: a durable, queryable record that an order was
-- actually notified about, instead of the Kafka listener only logging.
--
-- The unique constraint is what makes consuming the event safe to repeat. Kafka only guarantees
-- at-least-once delivery, so the same message can be redelivered after a crash; without this
-- constraint a redelivery would notify the same order twice.

CREATE TABLE order_notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants (id),
    order_id     UUID NOT NULL REFERENCES orders (id),
    channel      VARCHAR(30) NOT NULL CHECK (channel IN ('ORDER_CONFIRMATION')),
    recipient    VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_notifications_order_channel UNIQUE (tenant_id, order_id, channel)
);

CREATE INDEX idx_order_notifications_tenant_order ON order_notifications (tenant_id, order_id);

ALTER TABLE order_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_notifications FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON order_notifications
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);
