-- V5: Persist CoinGecko price history so a restart doesn't start with an empty cache, and so
-- stale data can still be served while CoinGecko is rate limiting us

CREATE TABLE IF NOT EXISTS price_history_cache (
    coin_id     VARCHAR(255) NOT NULL,
    vs_currency VARCHAR(10)  NOT NULL,
    points_json TEXT         NOT NULL,
    fetched_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (coin_id, vs_currency)
);
