package com.portfolio.tracker.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;
    
    @Column(name = "action", nullable = false, length = 100)
    private String action;
    
    @Column(name = "entity_type", length = 50)
    private String entityType;
    
    @Column(name = "entity_id")
    private Long entityId;
    
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AuditSeverity severity = AuditSeverity.INFO;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "success", nullable = false)
    private boolean success = true;
    
    @Column(name = "error_message")
    private String errorMessage;

    public enum AuditSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }

    // Constructors
    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(User user, String action, String entityType, Long entityId) {
        this();
        this.user = user;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public AuditLog(User user, String action, String entityType, Long entityId, String details) {
        this(user, action, entityType, entityId);
        this.details = details;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public AuditSeverity getSeverity() { return severity; }
    public void setSeverity(AuditSeverity severity) { this.severity = severity; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    // Helper methods
    public void markAsError(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
        this.severity = AuditSeverity.ERROR;
    }

    public void setSecurityContext(String ipAddress, String userAgent, String sessionId) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.sessionId = sessionId;
    }

    @Override
    public String toString() {
        return String.format("AuditLog{id=%d, user=%s, action='%s', entityType='%s', entityId=%d, timestamp=%s, success=%s}",
                id, user != null ? user.getUsername() : "null", action, entityType, entityId, timestamp, success);
    }
}
