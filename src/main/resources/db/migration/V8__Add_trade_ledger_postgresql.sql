-- V8: Trade ledger for the Institutional Trader Evaluation. Behavioral metrics (disposition
-- effect, FOMO, panic selling, profit factor, trading velocity) need dated buys and sells, which
-- the assets table alone can't provide.

CREATE TABLE IF NOT EXISTS trades (
    id         BIGSERIAL        PRIMARY KEY,
    user_id    BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Holding the trade belongs to; kept (as NULL) when a position is fully sold
    asset_id   BIGINT           REFERENCES assets(id) ON DELETE SET NULL,
    asset_name VARCHAR(255)     NOT NULL,
    side       VARCHAR(4)       NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity   DOUBLE PRECISION NOT NULL CHECK (quantity > 0),
    price      DOUBLE PRECISION NOT NULL CHECK (price > 0),
    trade_date DATE             NOT NULL,
    -- FALSE for holdings imported from before the ledger existed: their purchase date is unknown
    date_known BOOLEAN          NOT NULL DEFAULT TRUE,
    source     VARCHAR(10)      NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'IMPORTED')),
    created_at TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trades_user_date ON trades(user_id, trade_date);
CREATE INDEX IF NOT EXISTS idx_trades_asset ON trades(asset_id);

-- Existing holdings become one buy each, at their recorded purchase price, date unknown
INSERT INTO trades (user_id, asset_id, asset_name, side, quantity, price, trade_date, date_known, source)
SELECT a.user_id, a.id, a.name, 'BUY', a.quantity, a.purchase_price_per_unit,
       COALESCE(a.updated_at::date, CURRENT_DATE), FALSE, 'IMPORTED'
FROM assets a
WHERE a.user_id IS NOT NULL AND a.quantity > 0 AND a.purchase_price_per_unit > 0;

-- Optional target annual volatility the user wants to run at (e.g. 0.40 = 40%), for risk-target adherence
ALTER TABLE users ADD COLUMN IF NOT EXISTS risk_target_volatility DOUBLE PRECISION;
