-- Receiving a purchase order adds stock through the same ledger a quote conversion draws from
-- (see V4), so the set of allowed reasons needs to grow to include it.

ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_reason_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_reason_check
    CHECK (reason IN ('INITIAL_STOCK', 'MANUAL_ADJUSTMENT', 'CONSUMPTION', 'PURCHASE_RECEIPT'));
