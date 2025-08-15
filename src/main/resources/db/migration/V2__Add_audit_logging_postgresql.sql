-- PostgreSQL Audit Logging Enhancement
-- V2: Add comprehensive audit logging with PostgreSQL-specific features

-- Add audit logging triggers
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at columns
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_assets_updated_at 
    BEFORE UPDATE ON assets 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create function to log user actions
CREATE OR REPLACE FUNCTION log_user_action(
    p_user_id BIGINT,
    p_action VARCHAR(100),
    p_entity_type VARCHAR(50),
    p_entity_id BIGINT,
    p_details JSONB DEFAULT NULL,
    p_ip_address INET DEFAULT NULL,
    p_user_agent TEXT DEFAULT NULL
)
RETURNS BIGINT AS $$
DECLARE
    v_audit_id BIGINT;
BEGIN
    INSERT INTO audit_logs (
        user_id, 
        action, 
        entity_type, 
        entity_id, 
        details, 
        ip_address, 
        user_agent
    ) VALUES (
        p_user_id, 
        p_action, 
        p_entity_type, 
        p_entity_id, 
        p_details, 
        p_ip_address, 
        p_user_agent
    ) RETURNING id INTO v_audit_id;
    
    RETURN v_audit_id;
END;
$$ LANGUAGE plpgsql;

-- Create function to get audit trail for a user
CREATE OR REPLACE FUNCTION get_user_audit_trail(
    p_user_id BIGINT,
    p_limit INTEGER DEFAULT 100
)
RETURNS TABLE (
    action VARCHAR(100),
    entity_type VARCHAR(50),
    entity_id BIGINT,
    details JSONB,
    created_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        al.action,
        al.entity_type,
        al.entity_id,
        al.details,
        al.created_at
    FROM audit_logs al
    WHERE al.user_id = p_user_id
    ORDER BY al.created_at DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

-- Add comments for the new functions
COMMENT ON FUNCTION update_updated_at_column() IS 'Automatically updates the updated_at timestamp column';
COMMENT ON FUNCTION log_user_action(BIGINT, VARCHAR, VARCHAR, BIGINT, JSONB, INET, TEXT) IS 'Logs user actions for audit purposes';
COMMENT ON FUNCTION get_user_audit_trail(BIGINT, INTEGER) IS 'Retrieves audit trail for a specific user';
