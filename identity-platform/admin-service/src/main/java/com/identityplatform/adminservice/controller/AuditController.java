package com.identityplatform.adminservice.controller;

import com.identityplatform.core.audit.AuditEventType;
import com.identityplatform.core.audit.AuditLog;
import com.identityplatform.core.audit.AuditLogRepository;
import com.identityplatform.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "View activity history of the organization")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List audit logs for the current organization")
    public ResponseEntity<Page<AuditLog>> list(
            @PageableDefault(size = 50) Pageable pageable) {

        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                auditLogRepository.findByOrganizationIdOrderByOccurredAtDesc(orgId, pageable));
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Audit logs by user")
    public ResponseEntity<Page<AuditLog>> byUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 50) Pageable pageable) {

        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                auditLogRepository.findByOrganizationIdAndActorUserIdOrderByOccurredAtDesc(
                        orgId, userId, pageable));
    }

    @GetMapping("/by-event/{eventType}")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Audit logs by event type")
    public ResponseEntity<Page<AuditLog>> byEventType(
            @PathVariable AuditEventType eventType,
            @PageableDefault(size = 50) Pageable pageable) {

        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                auditLogRepository.findByOrganizationIdAndEventTypeOrderByOccurredAtDesc(
                        orgId, eventType, pageable));
    }

    @GetMapping("/range")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Audit logs within a date range")
    public ResponseEntity<Page<AuditLog>> byDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {

        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                auditLogRepository.findByOrganizationIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                        orgId, from, to, pageable));
    }
}
