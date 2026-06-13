package com.identityplatform.authserver.security;

import com.identityplatform.core.audit.AuditEventType;
import com.identityplatform.core.audit.AuditLog;
import com.identityplatform.core.audit.AuditService;
import com.identityplatform.usermanagement.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Listens to Spring Security authentication events
 * to write audit logs and update lastLoginAt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventListener {

    private final AuditService auditService;
    private final UserService userService;

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof UserPrincipal principal)) {
            return;
        }

        // Update last login time
        userService.recordLogin(principal.getUserId());

        auditService.logSuccess(
                AuditEventType.USER_LOGIN_SUCCESS,
                principal.getOrganizationId(),
                principal.getUserId(),
                principal.getEmail()
        );

        log.info("Login success: userId={}, org={}", principal.getUserId(), principal.getOrganizationId());
    }

    @EventListener
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getClientIp();
        String reason = event.getException().getMessage();

        // org is unknown on login failure (user has not authenticated yet)
        // log with orgId = null
        auditService.logFailure(
                AuditEventType.USER_LOGIN_FAILED,
                null,
                username,
                reason,
                ipAddress
        );

        log.warn("Login failed: username={}, ip={}, reason={}", username, ipAddress, reason);
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;

            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
