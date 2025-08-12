-- V3__Add_performance_optimization_h2.sql
-- Add performance optimization features (H2 Compatible)
-- This migration enhances database performance for production workloads

-- Add additional indexes for better query performance
-- Note: These tables are created in V1, so indexes are safe to create
CREATE INDEX IF NOT EXISTS idx_assets_price_per_unit ON assets(price_per_unit);
CREATE INDEX IF NOT EXISTS idx_assets_purchase_price_per_unit ON assets(purchase_price_per_unit);
CREATE INDEX IF NOT EXISTS idx_portfolio_summaries_calculated_at ON portfolio_summaries(calculated_at);
CREATE INDEX IF NOT EXISTS idx_risk_metrics_calculated_at ON risk_metrics(calculated_at);

-- Add composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_assets_user_name ON assets(user_id, name);
CREATE INDEX IF NOT EXISTS idx_assets_user_price ON assets(user_id, price_per_unit);

-- Note: H2 doesn't support partial indexes with WHERE clauses
-- These would be implemented in PostgreSQL for production
-- CREATE INDEX IF NOT EXISTS idx_assets_active ON assets(user_id) WHERE current_price > 0;
-- CREATE INDEX IF NOT EXISTS idx_user_sessions_active ON user_sessions(user_id) WHERE is_active = TRUE;

-- Note: H2 has limited function support compared to PostgreSQL
-- These functions would be implemented in PostgreSQL for production
-- For now, we'll create simple views for common calculations

-- Create a view for portfolio value calculations
CREATE VIEW IF NOT EXISTS portfolio_values AS
SELECT 
    user_id,
    COALESCE(SUM(quantity * price_per_unit), 0) as total_value,
    COUNT(*) as asset_count
FROM assets 
WHERE price_per_unit IS NOT NULL 
GROUP BY user_id;

-- Create a view for ROI calculations
CREATE VIEW IF NOT EXISTS portfolio_roi AS
SELECT 
    user_id,
    COALESCE(AVG(
        CASE 
            WHEN purchase_price_per_unit > 0 THEN 
                ((price_per_unit - purchase_price_per_unit) / purchase_price_per_unit) * 100
            ELSE 0 
        END
    ), 0) as average_roi
FROM assets 
WHERE price_per_unit IS NOT NULL AND purchase_price_per_unit > 0 
GROUP BY user_id;
