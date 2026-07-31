-- An order's lifecycle after conversion (build, ship, deliver) is separate from the quote's own
-- status: a quote's status is about whether the customer agreed to buy, an order's status is
-- about whether the shop has built and shipped what was agreed to.

ALTER TABLE orders
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED'
        CHECK (status IN ('CONFIRMED', 'IN_PRODUCTION', 'SHIPPED', 'DELIVERED', 'CANCELLED'));
