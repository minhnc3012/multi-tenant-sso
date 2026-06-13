package com.identityplatform.authserver.security;

import com.identityplatform.core.audit.AuditEventType;
import com.identityplatform.core.audit.AuditService;
import com.identityplatform.usermanagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginEventListener tests")
class LoginEventListenerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private UserService userService;

    @InjectMocks
    private LoginEventListener loginEventListener;

    @BeforeEach
    void setUp() {
        // No setup needed for these unit tests
    }

    @Test
    @DisplayName("onLoginSuccess: calls recordLogin and logSuccess")
    void onLoginSuccess_callsCorrectMethods() {
        UserPrincipal principal = mock(UserPrincipal.class);
        Authentication auth = mock(Authentication.class);

        when(auth.getPrincipal()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(UUID.randomUUID());
        when(principal.getOrganizationId()).thenReturn(UUID.randomUUID());
        when(principal.getEmail()).thenReturn("user@acme.com");

        loginEventListener.onLoginSuccess(new AuthenticationSuccessEvent(auth));

        verify(userService).recordLogin(any());
        verify(auditService).logSuccess(
                eq(AuditEventType.USER_LOGIN_SUCCESS),
                any(),
                any(),
                eq("user@acme.com")
        );
    }

    @Test
    @DisplayName("onLoginSuccess: skips when principal is not a UserPrincipal")
    void onLoginSuccess_nonUserPrincipal_doesNothing() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-a-user");

        loginEventListener.onLoginSuccess(new AuthenticationSuccessEvent(auth));

        verify(userService, never()).recordLogin(any());
        verify(auditService, never()).logSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("onLoginFailure: calls logFailure with the correct event type")
    void onLoginFailure_callsLogFailure() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("failed-user@acme.com");

        // BadCredentialsException extends AuthenticationException — correct type for getException()
        BadCredentialsException exception = new BadCredentialsException("Bad credentials");
        AbstractAuthenticationFailureEvent event = mock(AbstractAuthenticationFailureEvent.class);
        when(event.getAuthentication()).thenReturn(auth);
        when(event.getException()).thenReturn(exception);

        loginEventListener.onLoginFailure(event);

        verify(auditService).logFailure(
                eq(AuditEventType.USER_LOGIN_FAILED),
                any(),
                eq("failed-user@acme.com"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("onLoginFailure: does not throw exception on error")
    void onLoginFailure_noThrowOnException() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("test");

        BadCredentialsException exception = new BadCredentialsException("Bad credentials");
        AbstractAuthenticationFailureEvent event = mock(AbstractAuthenticationFailureEvent.class);
        when(event.getAuthentication()).thenReturn(auth);
        when(event.getException()).thenReturn(exception);

        // Must not throw
        loginEventListener.onLoginFailure(event);
    }

    @Test
    @DisplayName("onLoginSuccess: event type USER_LOGIN_SUCCESS is used correctly")
    void eventTypes_correct() {
        UserPrincipal principal = mock(UserPrincipal.class);
        Authentication auth = mock(Authentication.class);

        when(auth.getPrincipal()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(UUID.randomUUID());
        when(principal.getOrganizationId()).thenReturn(UUID.randomUUID());
        when(principal.getEmail()).thenReturn("test@acme.com");

        loginEventListener.onLoginSuccess(new AuthenticationSuccessEvent(auth));

        verify(auditService).logSuccess(
                eq(AuditEventType.USER_LOGIN_SUCCESS), any(), any(), eq("test@acme.com"));
    }
}
