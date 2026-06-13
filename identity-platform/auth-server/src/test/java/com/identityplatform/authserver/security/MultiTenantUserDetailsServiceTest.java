package com.identityplatform.authserver.security;

import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.domain.OrganizationStatus;
import com.identityplatform.organization.service.OrganizationService;
import com.identityplatform.usermanagement.domain.AuthProvider;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.domain.UserStatus;
import com.identityplatform.usermanagement.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiTenantUserDetailsService.
 * Uses MockHttpServletRequest + RequestContextHolder to simulate the request context
 * without needing a full Spring container.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiTenantUserDetailsService tests")
class MultiTenantUserDetailsServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private MultiTenantUserDetailsService userDetailsService;

    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── loadUserByUsername: header slug ──────────────────────────

    @Test
    @DisplayName("loadUserByUsername: succeeds when slug comes from header X-Organization-Slug")
    void loadUserByUsername_headerSlug_success() {
        setupRequestWithHeader("X-Organization-Slug", "acme-corp");

        Organization org = activeOrg("acme-corp");
        User user = activeUser("user@acme.com");

        when(organizationService.findBySlug("acme-corp")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        UserDetails result = userDetailsService.loadUserByUsername("user@acme.com");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("user@acme.com");
        assertThat(result).isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) result;
        assertThat(principal.getOrganizationId()).isEqualTo(orgId);
    }

    // ── loadUserByUsername: subdomain ─────────────────────────────

    @Test
    @DisplayName("loadUserByUsername: succeeds when slug comes from subdomain {slug}.yourplatform.com")
    void loadUserByUsername_subdomain_success() {
        setupRequestWithHost("acme-corp.yourplatform.com");

        Organization org = activeOrg("acme-corp");
        User user = activeUser("user@acme.com");

        when(organizationService.findBySlug("acme-corp")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        UserDetails result = userDetailsService.loadUserByUsername("user@acme.com");

        assertThat(result.getUsername()).isEqualTo("user@acme.com");
    }

    // ── loadUserByUsername: email domain fallback ────────────────

    @Test
    @DisplayName("loadUserByUsername: fallback detects tenant from email domain when no slug is present")
    void loadUserByUsername_emailDomainFallback_success() {
        // No header, subdomain is "auth" (ignored)
        setupRequestWithHost("auth.yourplatform.com");

        Organization org = activeOrg("acme-corp");
        User user = activeUser("user@acme.com");

        // Does not call findBySlug, calls findByEmailDomain instead
        when(organizationService.findByEmailDomain("user@acme.com")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        UserDetails result = userDetailsService.loadUserByUsername("user@acme.com");

        assertThat(result.getUsername()).isEqualTo("user@acme.com");
        verify(organizationService, never()).findBySlug(any());
        verify(organizationService).findByEmailDomain("user@acme.com");
    }

    @Test
    @DisplayName("loadUserByUsername: fallback to email domain when no request context is present")
    void loadUserByUsername_noRequestContext_fallsBackToEmailDomain() {
        // No request context set (RequestContextHolder is empty)
        RequestContextHolder.resetRequestAttributes();

        Organization org = activeOrg("acme-corp");
        User user = activeUser("user@acme.com");

        when(organizationService.findByEmailDomain("user@acme.com")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        UserDetails result = userDetailsService.loadUserByUsername("user@acme.com");

        assertThat(result.getUsername()).isEqualTo("user@acme.com");
    }

    // ── loadUserByUsername: form param (highest priority) ────────

    @Test
    @DisplayName("loadUserByUsername: form param org-slug has priority over header")
    void loadUserByUsername_formParamWinsOverHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("org-slug", "form-org");
        request.addHeader("X-Organization-Slug", "header-org");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Organization org = activeOrg("form-org");
        User user = activeUser("user@acme.com");

        when(organizationService.findBySlug("form-org")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        userDetailsService.loadUserByUsername("user@acme.com");

        verify(organizationService, never()).findBySlug("header-org");
        verify(organizationService).findBySlug("form-org");
    }

    @Test
    @DisplayName("loadUserByUsername: form param org-slug has priority over subdomain")
    void loadUserByUsername_formParamWinsOverSubdomain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("org-slug", "form-org");
        request.setServerName("subdomain-org.yourplatform.com");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Organization org = activeOrg("form-org");
        User user = activeUser("user@acme.com");

        when(organizationService.findBySlug("form-org")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        userDetailsService.loadUserByUsername("user@acme.com");

        verify(organizationService, never()).findBySlug("subdomain-org");
        verify(organizationService).findBySlug("form-org");
    }

    // ── loadUserByUsername: header wins over subdomain ────────────

    @Test
    @DisplayName("loadUserByUsername: header X-Organization-Slug has priority over subdomain")
    void loadUserByUsername_headerWinsOverSubdomain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Organization-Slug", "acme-corp");
        request.setServerName("other-org.yourplatform.com");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Organization org = activeOrg("acme-corp");
        User user = activeUser("user@acme.com");

        when(organizationService.findBySlug("acme-corp")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        userDetailsService.loadUserByUsername("user@acme.com");

        verify(organizationService, never()).findBySlug("other-org");
        verify(organizationService).findBySlug("acme-corp");
    }

    // ── loadUserByUsername: inactive org ─────────────────────────

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException when org is SUSPENDED")
    void loadUserByUsername_suspendedOrg_throwsException() {
        setupRequestWithHeader("X-Organization-Slug", "suspended-org");

        Organization org = Organization.builder()
                .id(orgId).slug("suspended-org").build();
        org.setStatus(OrganizationStatus.SUSPENDED);

        when(organizationService.findBySlug("suspended-org")).thenReturn(org);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user@suspended.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("user@suspended.com");

        verify(userService, never()).findByEmailAndOrg(any(), any());
    }

    // ── loadUserByUsername: user not found ───────────────────────

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException when user does not exist in the org")
    void loadUserByUsername_userNotFound_throwsException() {
        setupRequestWithHeader("X-Organization-Slug", "acme-corp");

        Organization org = activeOrg("acme-corp");

        when(organizationService.findBySlug("acme-corp")).thenReturn(org);
        when(userService.findByEmailAndOrg("nobody@acme.com", orgId))
                .thenThrow(new ResourceNotFoundException("User", "nobody@acme.com"));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@acme.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nobody@acme.com");
    }

    // ── loadUserByUsername: org not found ────────────────────────

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException when org slug does not exist")
    void loadUserByUsername_orgNotFound_throwsException() {
        setupRequestWithHeader("X-Organization-Slug", "unknown-org");

        when(organizationService.findBySlug("unknown-org"))
                .thenThrow(new ResourceNotFoundException("Organization", "unknown-org"));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user@unknown.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("user@unknown.com");
    }

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException when email domain does not match any org")
    void loadUserByUsername_emailDomainNotFound_throwsException() {
        setupRequestWithHost("auth.yourplatform.com"); // no slug

        when(organizationService.findByEmailDomain("user@unknown-domain.com"))
                .thenThrow(new ResourceNotFoundException("Organization", "unknown-domain.com"));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user@unknown-domain.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("user@unknown-domain.com");
    }

    // ── loadUserByUsername: UserPrincipal content ─────────────────

    @Test
    @DisplayName("loadUserByUsername: UserPrincipal contains correct userId and organizationId")
    void loadUserByUsername_returnsCorrectPrincipal() {
        setupRequestWithHeader("X-Organization-Slug", "acme-corp");

        Organization org = activeOrg("acme-corp");
        User user = activeUser("admin@acme.com");

        when(organizationService.findBySlug("acme-corp")).thenReturn(org);
        when(userService.findByEmailAndOrg("admin@acme.com", orgId)).thenReturn(user);

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername("admin@acme.com");

        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getOrganizationId()).isEqualTo(orgId);
        assertThat(principal.getEmail()).isEqualTo("admin@acme.com");
    }

    // ── Ignored subdomains ────────────────────────────────────────

    @Test
    @DisplayName("loadUserByUsername: subdomain 'www' is ignored, falls back to email domain")
    void loadUserByUsername_wwwSubdomain_ignoredFallsBackToEmailDomain() {
        setupRequestWithHost("www.yourplatform.com");

        Organization org = activeOrg("acme-corp");
        User user = activeUser("user@acme.com");

        when(organizationService.findByEmailDomain("user@acme.com")).thenReturn(org);
        when(userService.findByEmailAndOrg("user@acme.com", orgId)).thenReturn(user);

        userDetailsService.loadUserByUsername("user@acme.com");

        verify(organizationService, never()).findBySlug("www");
        verify(organizationService).findByEmailDomain("user@acme.com");
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void setupRequestWithHeader(String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(headerName, headerValue);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void setupRequestWithHost(String serverName) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(serverName);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void setupRequestWithParam(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(name, value);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Organization activeOrg(String slug) {
        return Organization.builder()
                .id(orgId)
                .slug(slug)
                .build(); // default status = ACTIVE
    }

    private User activeUser(String email) {
        return User.builder()
                .id(userId)
                .organizationId(orgId)
                .email(email)
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build();
    }
}
