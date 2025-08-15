package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    /**
     * Find audit logs by username, ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.username = :username ORDER BY a.timestamp DESC")
    List<AuditLog> findByUsernameOrderByTimestampDesc(@Param("username") String username, Pageable pageable);
    
    /**
     * Find audit logs by action, ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action = :action ORDER BY a.timestamp DESC")
    List<AuditLog> findByActionOrderByTimestampDesc(@Param("action") String action, Pageable pageable);
    
    /**
     * Find audit logs by severity, ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.severity = :severity ORDER BY a.timestamp DESC")
    List<AuditLog> findBySeverityOrderByTimestampDesc(@Param("severity") AuditLog.AuditSeverity severity, Pageable pageable);
    
    /**
     * Find audit logs within a time range, ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(@Param("start") LocalDateTime start, 
                                                             @Param("end") LocalDateTime end, Pageable pageable);
    
    /**
     * Find failed actions, ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.success = false ORDER BY a.timestamp DESC")
    List<AuditLog> findBySuccessFalseOrderByTimestampDesc(Pageable pageable);
    
    /**
     * Find security events, ordered by timestamp descending
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType ORDER BY a.timestamp DESC")
    List<AuditLog> findByEntityTypeOrderByTimestampDesc(@Param("entityType") String entityType, Pageable pageable);
    
    /**
     * Count failed actions
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.success = false")
    long countBySuccessFalse();
    
    /**
     * Count security events
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.entityType = :entityType")
    long countByEntityType(@Param("entityType") String entityType);
    
    /**
     * Count logs after a specific timestamp
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp > :timestamp")
    long countByTimestampAfter(@Param("timestamp") LocalDateTime timestamp);
    
    /**
     * Find logs by entity type and entity ID
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.timestamp DESC")
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(@Param("entityType") String entityType, 
                                                                  @Param("entityId") Long entityId, Pageable pageable);
    
    /**
     * Find logs by IP address (for security monitoring)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.ipAddress = :ipAddress ORDER BY a.timestamp DESC")
    List<AuditLog> findByIpAddressOrderByTimestampDesc(@Param("ipAddress") String ipAddress, Pageable pageable);
    
    /**
     * Find logs by session ID
     */
    @Query("SELECT a FROM AuditLog a WHERE a.sessionId = :sessionId ORDER BY a.timestamp DESC")
    List<AuditLog> findBySessionIdOrderByTimestampDesc(@Param("sessionId") String sessionId, Pageable pageable);
    
    /**
     * Find critical security events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.severity = 'CRITICAL' ORDER BY a.timestamp DESC")
    List<AuditLog> findCriticalEventsOrderByTimestampDesc(Pageable pageable);
    
    /**
     * Find logs for a specific user and action
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.username = :username AND a.action = :action ORDER BY a.timestamp DESC")
    List<AuditLog> findByUsernameAndActionOrderByTimestampDesc(@Param("username") String username, 
                                                              @Param("action") String action, Pageable pageable);
    
    /**
     * Find logs for portfolio operations
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType IN ('ASSET', 'PORTFOLIO') ORDER BY a.timestamp DESC")
    List<AuditLog> findPortfolioOperationsOrderByTimestampDesc(Pageable pageable);
    
    /**
     * Find authentication-related logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = 'AUTH' ORDER BY a.timestamp DESC")
    List<AuditLog> findAuthenticationLogsOrderByTimestampDesc(Pageable pageable);
    
    /**
     * Find logs with error messages
     */
    @Query("SELECT a FROM AuditLog a WHERE a.errorMessage IS NOT NULL ORDER BY a.timestamp DESC")
    List<AuditLog> findLogsWithErrorsOrderByTimestampDesc(Pageable pageable);
    
    /**
     * Find logs for a specific time period (last N hours)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp > :cutoffTime ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentLogsOrderByTimestampDesc(@Param("cutoffTime") LocalDateTime cutoffTime, Pageable pageable);
    
    /**
     * Find logs by user role
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.role = :role ORDER BY a.timestamp DESC")
    List<AuditLog> findByUserRoleOrderByTimestampDesc(@Param("role") String role, Pageable pageable);
}
