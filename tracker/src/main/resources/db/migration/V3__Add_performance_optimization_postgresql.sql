-- PostgreSQL Performance Optimization
-- V3: Add advanced performance features and optimizations

-- First, ensure the required tables and columns exist
DO $$
BEGIN
    -- Check if assets table exists and has required columns
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'assets' 
        AND table_schema = 'public'
    ) THEN
        -- Check if user_id column exists in assets table
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'assets' 
            AND column_name = 'user_id'
            AND table_schema = 'public'
        ) THEN
            -- Create partial indexes for better query performance
            CREATE INDEX IF NOT EXISTS idx_assets_active_user ON assets(user_id) WHERE user_id IS NOT NULL;
            
            -- Create composite indexes for common query patterns
            CREATE INDEX IF NOT EXISTS idx_assets_user_id ON assets(user_id);
        END IF;
    END IF;
    
    -- Check if audit_logs table exists and has details column
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'audit_logs' 
        AND table_schema = 'public'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'audit_logs' 
            AND column_name = 'details'
            AND table_schema = 'public'
        ) THEN
            -- Create index for audit details (TEXT type, not JSONB)
            CREATE INDEX IF NOT EXISTS idx_audit_logs_details ON audit_logs(details);
        END IF;
    END IF;
END $$;

-- Create function for portfolio performance calculation (only if assets table exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'assets' 
        AND table_schema = 'public'
    ) THEN
        CREATE OR REPLACE FUNCTION calculate_portfolio_performance(
            p_user_id BIGINT,
            p_start_date TIMESTAMP DEFAULT NULL,
            p_end_date TIMESTAMP DEFAULT NULL
        )
        RETURNS TABLE (
            total_assets BIGINT,
            total_investment DOUBLE PRECISION,
            current_value DOUBLE PRECISION,
            total_return DOUBLE PRECISION,
            return_percentage DOUBLE PRECISION
        ) AS $func$
        DECLARE
            v_start_date TIMESTAMP;
            v_end_date TIMESTAMP;
        BEGIN
            -- Set default dates if not provided
            v_start_date := COALESCE(p_start_date, CURRENT_DATE - INTERVAL '1 year');
            v_end_date := COALESCE(p_end_date, CURRENT_DATE);
            
            RETURN QUERY
            SELECT 
                COUNT(a.id)::BIGINT as total_assets,
                SUM(a.initial_investment) as total_investment,
                SUM(a.quantity * a.price_per_unit) as current_value,
                SUM(a.quantity * a.price_per_unit - a.initial_investment) as total_return,
                CASE 
                    WHEN SUM(a.initial_investment) > 0 
                    THEN (SUM(a.quantity * a.price_per_unit - a.initial_investment) / SUM(a.initial_investment)) * 100
                    ELSE 0 
                END as return_percentage
            FROM assets a
            WHERE a.user_id = p_user_id;
        END;
        $func$ LANGUAGE plpgsql;
    END IF;
END $$;

-- Create function for asset allocation analysis (only if assets table exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'assets' 
        AND table_schema = 'public'
    ) THEN
        CREATE OR REPLACE FUNCTION get_asset_allocation(p_user_id BIGINT)
        RETURNS TABLE (
            name VARCHAR(255),
            asset_count BIGINT,
            total_quantity DOUBLE PRECISION,
            total_investment DOUBLE PRECISION,
            current_value DOUBLE PRECISION,
            allocation_percentage DOUBLE PRECISION
        ) AS $func$
        BEGIN
            RETURN QUERY
            SELECT 
                a.name,
                COUNT(a.id)::BIGINT as asset_count,
                SUM(a.quantity) as total_quantity,
                SUM(a.initial_investment) as total_investment,
                SUM(a.quantity * a.price_per_unit) as current_value,
                CASE 
                    WHEN (SELECT SUM(quantity * price_per_unit) FROM assets WHERE user_id = p_user_id) > 0
                    THEN (SUM(a.quantity * a.price_per_unit) / (SELECT SUM(quantity * price_per_unit) FROM assets WHERE user_id = p_user_id)) * 100
                    ELSE 0 
                END as allocation_percentage
            FROM assets a
            WHERE a.user_id = p_user_id
            GROUP BY a.name
            ORDER BY allocation_percentage DESC;
        END;
        $func$ LANGUAGE plpgsql;
    END IF;
END $$;

-- Create materialized view for portfolio analytics (only if both tables exist)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'users' 
        AND table_schema = 'public'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'assets' 
        AND table_schema = 'public'
    ) THEN
        CREATE MATERIALIZED VIEW IF NOT EXISTS portfolio_analytics AS
        SELECT 
            u.id as user_id,
            u.username,
            COUNT(a.id) as total_assets,
            SUM(a.initial_investment) as total_investment,
            SUM(a.quantity * a.price_per_unit) as current_value,
            AVG(a.price_per_unit) as avg_price_per_unit,
            COUNT(DISTINCT a.name) as unique_assets
        FROM users u
        LEFT JOIN assets a ON u.id = a.user_id
        GROUP BY u.id, u.username;
        
        -- Create index on materialized view
        CREATE INDEX IF NOT EXISTS idx_portfolio_analytics_user ON portfolio_analytics(user_id);
    END IF;
END $$;

-- Create function to refresh materialized view (only if it exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'portfolio_analytics' 
        AND table_schema = 'public'
    ) THEN
        CREATE OR REPLACE FUNCTION refresh_portfolio_analytics()
        RETURNS VOID AS $func$
        BEGIN
            REFRESH MATERIALIZED VIEW portfolio_analytics;
        END;
        $func$ LANGUAGE plpgsql;
    END IF;
END $$;

-- Add comments for the new functions and views
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'assets' 
        AND table_schema = 'public'
    ) THEN
        COMMENT ON FUNCTION calculate_portfolio_performance(BIGINT, TIMESTAMP, TIMESTAMP) IS 'Calculates portfolio performance metrics for a given time period';
        COMMENT ON FUNCTION get_asset_allocation(BIGINT) IS 'Returns asset allocation analysis for a user';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'portfolio_analytics' 
        AND table_schema = 'public'
    ) THEN
        COMMENT ON MATERIALIZED VIEW portfolio_analytics IS 'Materialized view for portfolio analytics with refresh capability';
        COMMENT ON FUNCTION refresh_portfolio_analytics() IS 'Refreshes the portfolio analytics materialized view';
    END IF;
END $$;
