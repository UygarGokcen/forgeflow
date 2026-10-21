-- A version column for optimistic locking (JPA @Version) on the rows that concurrent requests
-- read, change and write back. Every UPDATE checks the version it read and bumps it, so when two
-- requests race on the same row the second one fails with a 409 instead of silently overwriting
-- the first one's change.

ALTER TABLE quotes          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE materials       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
