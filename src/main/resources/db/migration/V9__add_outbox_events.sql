-- Transactional outbox for events that leave the database (today: Kafka).
--
-- An event is written here in the same transaction as the change that caused it, so the event
-- exists if and only if that change committed. OutboxRelay then sends pending rows to Kafka and
-- marks them published. Before this table existed, the event was sent from an AFTER_COMMIT
-- listener, and if Kafka happened to be down at that moment the event was logged and lost.
--
-- No row-level security on purpose: the relay runs in the background with no tenant bound and
-- has to see every tenant's pending events. Nothing on the request path reads this table.
-- tenant_id is still stored, because the relay uses it as the Kafka message key.

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    attempts       INT NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

-- The relay only ever looks for unpublished rows, oldest first, so only those are indexed.
CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at) WHERE published_at IS NULL;
