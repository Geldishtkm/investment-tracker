-- V9: Record when each holding's current price was last quoted, so valuations based on old prices
-- (CoinGecko unreachable when a user returns) can be detected and shown instead of passing as live.
-- TIMESTAMPTZ: an absolute instant, independent of the server's time zone.

ALTER TABLE assets ADD COLUMN IF NOT EXISTS price_updated_at TIMESTAMPTZ;
