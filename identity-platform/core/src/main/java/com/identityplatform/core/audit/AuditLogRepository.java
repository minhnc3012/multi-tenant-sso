package com.identityplatform.core.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOrganizationIdOrderByOccurredAtDesc(
            UUID organizationId, Pageable pageable);

    Page<AuditLog> findByOrganizationIdAndActorUserIdOrderByOccurredAtDesc(
            UUID organizationId, UUID actorUserId, Pageable pageable);

    Page<AuditLog> findByOrganizationIdAndEventTypeOrderByOccurredAtDesc(
            UUID organizationId, AuditEventType eventType, Pageable pageable);

    Page<AuditLog> findByOrganizationIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID organizationId, Instant from, Instant to, Pageable pageable);
}
