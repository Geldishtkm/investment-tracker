-- PostgreSQL Initial Schema
-- V1: Create users and assets tables with PostgreSQL-specific optimizations

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255),
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255),
    role VARCHAR(255) NOT NULL CHECK (role IN ('USER', 'ADMIN', 'MANAGER')),
    mfa_enabled BOOLEAN DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    last_login TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    account_locked_until TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Create assets table
CREATE TABLE IF NOT EXISTS assets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    price_per_unit DOUBLE PRECISION NOT NULL,
    purchase_price_per_unit DOUBLE PRECISION NOT NULL,
    initial_investment DOUBLE PRECISION,
    user_id BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create audit_logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    details TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    session_id VARCHAR(100),
    success BOOLEAN NOT NULL,
    error_message VARCHAR(255),
    severity VARCHAR(255) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'CRITICAL')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Create portfolio_summaries table
CREATE TABLE IF NOT EXISTS portfolio_summaries (
    id BIGSERIAL PRIMARY KEY,
    number_of_assets INTEGER NOT NULL,
    total_value DOUBLE PRECISION NOT NULL,
    averageroi DOUBLE PRECISION NOT NULL,
    calculated_at TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_assets_user_id ON assets(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);

-- Create a view for portfolio summary
CREATE OR REPLACE VIEW portfolio_summary AS
SELECT 
    u.id as user_id,
    u.username,
    COUNT(a.id) as total_assets,
    SUM(a.quantity * a.price_per_unit) as total_value,
    SUM(a.initial_investment) as total_investment,
    AVG(a.price_per_unit) as avg_price_per_unit
FROM users u
LEFT JOIN assets a ON u.id = a.user_id
GROUP BY u.id, u.username;

-- Add comments for documentation
COMMENT ON TABLE users IS 'User accounts and authentication information';
COMMENT ON TABLE assets IS 'Portfolio assets and holdings';
COMMENT ON TABLE audit_logs IS 'Audit trail for security and compliance';
COMMENT ON TABLE portfolio_summaries IS 'Portfolio summary calculations';
COMMENT ON VIEW portfolio_summary IS 'Aggregated portfolio information per user';
