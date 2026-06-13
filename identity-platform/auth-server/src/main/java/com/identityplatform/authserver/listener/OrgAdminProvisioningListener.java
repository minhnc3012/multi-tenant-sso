package com.identityplatform.authserver.listener;

import com.identityplatform.organization.events.OrgCreatedEvent;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.dto.UserDto;
import com.identityplatform.usermanagement.repository.RoleRepository;
import com.identityplatform.usermanagement.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * After a new Organization is committed, automatically invites the first org admin.
 * Runs in a separate transaction (AFTER_COMMIT) so org creation never rolls back
 * due to invite email failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrgAdminProvisioningListener {

    private final UserService userService;
    private final RoleRepository roleRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrgCreated(OrgCreatedEvent event) {
        if (event.adminEmail() == null || event.adminEmail().isBlank()) {
            log.debug("[OrgProvisioning] No adminEmail provided for org={}, skipping admin invite", event.orgSlug());
            return;
        }

        Role orgAdminRole = roleRepository.findByNameAndSystemRoleTrue("ORG_ADMIN")
                .orElse(null);
        if (orgAdminRole == null) {
            log.warn("[OrgProvisioning] ORG_ADMIN role not found — cannot invite admin for org={}", event.orgSlug());
            return;
        }

        try {
            UserDto.InviteRequest invite = new UserDto.InviteRequest(
                    event.adminEmail(),
                    event.adminFirstName() != null ? event.adminFirstName() : "Org",
                    event.adminLastName()  != null ? event.adminLastName()  : "Admin",
                    Set.of(orgAdminRole.getId()),
                    null,
                    null  // orgId is passed explicitly to inviteUser()
            );
            userService.inviteUser(event.orgId(), invite);
            log.info("[OrgProvisioning] Org admin invited: email={}, org={}", event.adminEmail(), event.orgSlug());
        } catch (Exception e) {
            log.error("[OrgProvisioning] Failed to invite org admin email={} for org={}: {}",
                    event.adminEmail(), event.orgSlug(), e.getMessage());
        }
    }
}
