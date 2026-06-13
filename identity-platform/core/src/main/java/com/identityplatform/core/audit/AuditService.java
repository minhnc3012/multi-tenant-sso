package com.identityplatform.core.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Audit log service.
 *
 * Uses @Async to avoid blocking the main request.
 * Uses Propagation.REQUIRES_NEW so audit logs are always written,
 * even when the main transaction is rolled back (e.g., a failed login still needs to be logged).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEventType eventType,
                    UUID organizationId,
                    UUID actorUserId,
                    String actorEmail,
                    String targetId,
                    String targetType,
                    AuditLog.AuditResult result,
                    String ipAddress,
                    String failureReason) {
        try {
            AuditLog entry = AuditLog.builder()
                    .eventType(eventType)
                    .organizationId(organizationId)
                    .actorUserId(actorUserId)
                    .actorEmail(actorEmail)
                    .targetId(targetId)
                    .targetType(targetType)
                    .result(result)
                    .ipAddress(ipAddress)
                    .failureReason(failureReason)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failure must not crash the application
            log.error("Failed to write audit log: event={}, org={}", eventType, organizationId, e);
        }
    }

    // Convenience methods

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(AuditEventType eventType, UUID organizationId,
                           UUID actorUserId, String actorEmail) {
        log(eventType, organizationId, actorUserId, actorEmail,
                null, null, AuditLog.AuditResult.SUCCESS, null, null);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(AuditEventType eventType, UUID organizationId,
                           String actorEmail, String reason, String ipAddress) {
        log(eventType, organizationId, null, actorEmail,
                null, null, AuditLog.AuditResult.FAILURE, ipAddress, reason);
    }
}
