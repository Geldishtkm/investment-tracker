package com.portfolio.tracker.service;

import com.portfolio.tracker.model.AuditLog;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AuditService {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    /**
     * Log a successful action
     */
    public void logAction(User user, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog(user, action, entityType, entityId, details);
        setSecurityContext(log);
        auditLogRepository.save(log);
    }
    
    /**
     * Log a successful action without entity details
     */
    public void logAction(User user, String action, String details) {
        AuditLog log = new AuditLog(user, action, null, null, details);
        setSecurityContext(log);
        auditLogRepository.save(log);
    }
    
    /**
     * Log a failed action
     */
    public void logFailedAction(User user, String action, String entityType, Long entityId, String errorMessage) {
        AuditLog log = new AuditLog(user, action, entityType, entityId, "Failed: " + errorMessage);
        log.markAsError(errorMessage);
        setSecurityContext(log);
        auditLogRepository.save(log);
    }
    
    /**
     * Log a failed action without entity details
     */
    public void logFailedAction(User user, String action, String errorMessage) {
        AuditLog log = new AuditLog(user, action, null, null, "Failed: " + errorMessage);
        log.markAsError(errorMessage);
        setSecurityContext(log);
        auditLogRepository.save(log);
    }
    
    /**
     * Log security-related events
     */
    public void logSecurityEvent(User user, String action, String details, AuditLog.AuditSeverity severity) {
        AuditLog log = new AuditLog(user, action, "SECURITY", null, details);
        log.setSeverity(severity);
        setSecurityContext(log);
        auditLogRepository.save(log);
    }
    
    /**
     * Log login attempts
     */
    public void logLoginAttempt(String username, boolean success, String details) {
        // Create a temporary user object for logging
        User tempUser = new User();
        tempUser.setUsername(username);
        
        if (success) {
            logAction(tempUser, "LOGIN_SUCCESS", "AUTH", null, details);
        } else {
            logFailedAction(tempUser, "LOGIN_FAILED", "AUTH", null, details);
        }
    }
    
    /**
     * Log MFA attempts
     */
    public void logMfaAttempt(String username, boolean success, String details) {
        User tempUser = new User();
        tempUser.setUsername(username);
        
        if (success) {
            logAction(tempUser, "MFA_SUCCESS", "AUTH", null, details);
        } else {
            logFailedAction(tempUser, "MFA_FAILED", "AUTH", null, details);
        }
    }
    
    /**
     * Log portfolio changes
     */
    public void logPortfolioChange(User user, String action, Long assetId, String details) {
        logAction(user, action, "ASSET", assetId, details);
    }
    
    /**
     * Log VaR calculations
     */
    public void logVaRCalculation(User user, String details) {
        logAction(user, "VAR_CALCULATION", "PORTFOLIO", null, details);
    }
    
    /**
     * Log portfolio rebalancing
     */
    public void logRebalancing(User user, String method, String details) {
        logAction(user, "PORTFOLIO_REBALANCING", "PORTFOLIO", null, 
                 String.format("Method: %s, Details: %s", method, details));
    }
    
    /**
     * Get audit logs for a specific user
     */
    public List<AuditLog> getUserAuditLogs(String username, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable);
    }
    
    /**
     * Get audit logs by action
     */
    public List<AuditLog> getAuditLogsByAction(String action, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
    }
    
    /**
     * Get audit logs by severity
     */
    public List<AuditLog> getAuditLogsBySeverity(AuditLog.AuditSeverity severity, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return auditLogRepository.findBySeverityOrderByTimestampDesc(severity, pageable);
    }
    
    /**
     * Get audit logs within a time range
     */
    public List<AuditLog> getAuditLogsByTimeRange(LocalDateTime start, LocalDateTime end, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
    }
    
    /**
     * Get failed actions
     */
    public List<AuditLog> getFailedActions(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return auditLogRepository.findBySuccessFalseOrderByTimestampDesc(pageable);
    }
    
    /**
     * Get security events
     */
    public List<AuditLog> getSecurityEvents(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return auditLogRepository.findByEntityTypeOrderByTimestampDesc("SECURITY", pageable);
    }
    
    /**
     * Set security context from HTTP request
     */
    private void setSecurityContext(AuditLog log) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ipAddress = getClientIpAddress(request);
                String userAgent = request.getHeader("User-Agent");
                String sessionId = request.getSession().getId();
                
                log.setSecurityContext(ipAddress, userAgent, sessionId);
            }
        } catch (Exception e) {
            // If we can't get request context, continue without it
            log.setSecurityContext("unknown", "unknown", "unknown");
        }
    }
    
    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Get audit statistics for admin dashboard
     */
    public AuditStatistics getAuditStatistics() {
        long totalLogs = auditLogRepository.count();
        long failedActions = auditLogRepository.countBySuccessFalse();
        long securityEvents = auditLogRepository.countByEntityType("SECURITY");
        long todayLogs = auditLogRepository.countByTimestampAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
        
        return new AuditStatistics(totalLogs, failedActions, securityEvents, todayLogs);
    }
    
    /**
     * Audit statistics data class
     */
    public static class AuditStatistics {
        private final long totalLogs;
        private final long failedActions;
        private final long securityEvents;
        private final long todayLogs;
        
        public AuditStatistics(long totalLogs, long failedActions, long securityEvents, long todayLogs) {
            this.totalLogs = totalLogs;
            this.failedActions = failedActions;
            this.securityEvents = securityEvents;
            this.todayLogs = todayLogs;
        }
        
        public long getTotalLogs() { return totalLogs; }
        public long getFailedActions() { return failedActions; }
        public long getSecurityEvents() { return securityEvents; }
        public long getTodayLogs() { return todayLogs; }
    }
}
