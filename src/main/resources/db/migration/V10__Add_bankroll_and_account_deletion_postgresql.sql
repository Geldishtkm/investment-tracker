-- Paper-trading bankroll (S0): set once by the user, then fixed. Cash = S0 - buys + sells.
ALTER TABLE users ADD COLUMN IF NOT EXISTS initial_bankroll NUMERIC(20, 2);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bankroll_set_at TIMESTAMPTZ;

-- Email confirmation for account deletion: only a SHA-256 hash of the code is stored
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_code_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_code_expires_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_code_sent_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_attempts INT NOT NULL DEFAULT 0;
