package com.identityplatform.adminservice.audit;

import com.identityplatform.organization.events.OrgCreatedEvent;
import com.identityplatform.organization.events.OrgSuspendedEvent;
import com.identityplatform.usermanagement.events.UserActivatedEvent;
import com.identityplatform.usermanagement.events.UserInvitedEvent;
import com.identityplatform.usermanagement.events.UserSuspendedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cross-cutting audit listener — subscribes to domain events from all modules.
 * Today: logs to console. Tomorrow: persist to audit_logs table or forward to SIEM.
 * When extracting to microservices: swap @TransactionalEventListener for @KafkaListener — zero change here.
 */
@Slf4j
@Component
public class AuditEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(OrgCreatedEvent e) {
        log.info("[AUDIT] org.created orgId={} slug={} adminEmail={}", e.orgId(), e.orgSlug(), e.adminEmail());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(OrgSuspendedEvent e) {
        log.warn("[AUDIT] org.suspended orgId={} slug={}", e.orgId(), e.orgSlug());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserInvitedEvent e) {
        log.info("[AUDIT] user.invited userId={} orgId={} email={}", e.userId(), e.orgId(), e.email());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserSuspendedEvent e) {
        log.warn("[AUDIT] user.suspended userId={} orgId={}", e.userId(), e.orgId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserActivatedEvent e) {
        log.info("[AUDIT] user.activated userId={} orgId={}", e.userId(), e.orgId());
    }
}
