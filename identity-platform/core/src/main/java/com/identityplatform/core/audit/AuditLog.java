package com.identityplatform.core.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log for all significant events.
 * Each record is associated with a tenant (organizationId).
 *
 * Used for:
 * - Compliance / security review
 * - Debugging auth issues
 * - Gate Activity Report (who logged in, when, and from where)
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_org_time", columnList = "organizationId, occurredAt"),
        @Index(name = "idx_audit_user", columnList = "actorUserId"),
        @Index(name = "idx_audit_event", columnList = "eventType")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEventType eventType;

    /**
     * UUID of the user performing the action.
     * Null if it is a system action.
     */
    @Column
    private UUID actorUserId;

    @Column
    private String actorEmail;

    /**
     * Resource affected (user ID, role ID, etc.)
     */
    @Column
    private String targetId;

    @Column
    private String targetType;

    /**
     * Additional details in JSON format
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    /**
     * Outcome: SUCCESS, FAILURE
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AuditResult result = AuditResult.SUCCESS;

    @Column
    private String failureReason;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    public enum AuditResult {
        SUCCESS, FAILURE
    }
}
