package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.AuditLog;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.AuditService;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {
    
    @Autowired
    private AuditService auditService;
    
    @Autowired
    private UserService userService;
    
    /**
     * Get audit statistics for admin dashboard
     */
    @GetMapping("/audit/statistics")
    public ResponseEntity<Map<String, Object>> getAuditStatistics() {
        try {
            AuditService.AuditStatistics stats = auditService.getAuditStatistics();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "statistics", Map.of(
                    "totalLogs", stats.getTotalLogs(),
                    "failedActions", stats.getFailedActions(),
                    "securityEvents", stats.getSecurityEvents(),
                    "todayLogs", stats.getTodayLogs()
                )
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting audit statistics: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get recent audit logs
     */
    @GetMapping("/audit/logs")
    public ResponseEntity<Map<String, Object>> getRecentAuditLogs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String severity) {
        
        try {
            List<AuditLog> logs;
            
            if (action != null) {
                logs = auditService.getAuditLogsByAction(action, limit);
            } else if (severity != null) {
                try {
                    AuditLog.AuditSeverity sev = AuditLog.AuditSeverity.valueOf(severity.toUpperCase());
                    logs = auditService.getAuditLogsBySeverity(sev, limit);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid severity level: " + severity
                    ));
                }
            } else {
                // Get recent logs by time range (last 24 hours)
                LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
                logs = auditService.getAuditLogsByTimeRange(cutoff, LocalDateTime.now(), limit);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "logs", logs,
                "count", logs.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting audit logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get failed actions for security monitoring
     */
    @GetMapping("/audit/failed-actions")
    public ResponseEntity<Map<String, Object>> getFailedActions(
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            List<AuditLog> failedActions = auditService.getFailedActions(limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "failedActions", failedActions,
                "count", failedActions.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting failed actions: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get security events
     */
    @GetMapping("/audit/security-events")
    public ResponseEntity<Map<String, Object>> getSecurityEvents(
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            List<AuditLog> securityEvents = auditService.getSecurityEvents(limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "securityEvents", securityEvents,
                "count", securityEvents.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting security events: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get user-specific audit logs
     */
    @GetMapping("/audit/user/{username}")
    public ResponseEntity<Map<String, Object>> getUserAuditLogs(
            @PathVariable String username,
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            List<AuditLog> userLogs = auditService.getUserAuditLogs(username, limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "username", username,
                "logs", userLogs,
                "count", userLogs.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting user audit logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get portfolio operations audit logs
     */
    @GetMapping("/audit/portfolio-operations")
    public ResponseEntity<Map<String, Object>> getPortfolioOperations(
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            List<AuditLog> portfolioLogs = auditService.getAuditLogsByAction("PORTFOLIO_REBALANCING", limit);
            List<AuditLog> varLogs = auditService.getAuditLogsByAction("VAR_CALCULATION", limit);
            List<AuditLog> assetLogs = auditService.getAuditLogsByAction("ASSET_UPDATE", limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "portfolioRebalancing", portfolioLogs,
                "varCalculations", varLogs,
                "assetUpdates", assetLogs,
                "totalCount", portfolioLogs.size() + varLogs.size() + assetLogs.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting portfolio operations: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get authentication logs
     */
    @GetMapping("/audit/authentication")
    public ResponseEntity<Map<String, Object>> getAuthenticationLogs(
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            List<AuditLog> authLogs = auditService.getAuditLogsByAction("LOGIN_SUCCESS", limit);
            List<AuditLog> failedLogins = auditService.getAuditLogsByAction("LOGIN_FAILED", limit);
            List<AuditLog> mfaLogs = auditService.getAuditLogsByAction("MFA_SUCCESS", limit);
            List<AuditLog> mfaFailures = auditService.getAuditLogsByAction("MFA_FAILED", limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "successfulLogins", authLogs,
                "failedLogins", failedLogins,
                "successfulMfa", mfaLogs,
                "failedMfa", mfaFailures,
                "totalCount", authLogs.size() + failedLogins.size() + mfaLogs.size() + mfaFailures.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting authentication logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get critical security events
     */
    @GetMapping("/audit/critical-events")
    public ResponseEntity<Map<String, Object>> getCriticalEvents(
            @RequestParam(defaultValue = "50") int limit) {
        
        try {
            List<AuditLog> criticalEvents = auditService.getAuditLogsBySeverity(AuditLog.AuditSeverity.CRITICAL, limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "criticalEvents", criticalEvents,
                "count", criticalEvents.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting critical events: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get audit logs by time range
     */
    @GetMapping("/audit/time-range")
    public ResponseEntity<Map<String, Object>> getAuditLogsByTimeRange(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            LocalDateTime startTime = LocalDateTime.parse(start);
            LocalDateTime endTime = LocalDateTime.parse(end);
            
            List<AuditLog> logs = auditService.getAuditLogsByTimeRange(startTime, endTime, limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "startTime", startTime,
                "endTime", endTime,
                "logs", logs,
                "count", logs.size()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error parsing time range: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Health check for admin services
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Admin Service",
                "auditService", "UP",
                "userService", "UP",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "DOWN",
                "service", "Admin Service",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
}
