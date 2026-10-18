-- V4: Email verification for new signups

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_code VARCHAR(6);
ALTER TABLE users ADD COLUMN IF NOT EXISTS code_expiration TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_sent_at TIMESTAMP;

-- Accounts created before verification existed have no email to verify; keep them able to log in
UPDATE users SET is_enabled = TRUE;

-- One account per email address (case-insensitive); older accounts without an email are unaffected
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (LOWER(email)) WHERE email IS NOT NULL;
