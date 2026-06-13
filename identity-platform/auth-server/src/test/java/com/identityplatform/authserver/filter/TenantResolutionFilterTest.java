package com.identityplatform.authserver.filter;

import com.identityplatform.core.tenant.TenantContext;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.service.OrganizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantResolutionFilter tests")
class TenantResolutionFilterTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TenantResolutionFilter filter;

    private UUID testOrgId;

    @BeforeEach
    void setUp() {
        testOrgId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // Helper: @SuperBuilder allows setting id from BaseEntity directly in the builder
    private Organization buildOrg(String slug) {
        return Organization.builder()
                .id(testOrgId)
                .slug(slug)
                .build();
    }

    @Test
    @DisplayName("doFilterInternal: resolve tenant from header X-Organization-Slug")
    void doFilterInternal_fromHeader() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn("acme-corp");
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(organizationService.findBySlug("acme-corp")).thenReturn(buildOrg("acme-corp"));

        // Capture TenantContext WHILE the filter is running (before finally clear)
        UUID[] tenantDuringFilter = {null};
        doAnswer(inv -> {
            tenantDuringFilter[0] = TenantContext.getCurrentTenantOrNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(tenantDuringFilter[0]).isEqualTo(testOrgId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal: resolve tenant from subdomain")
    void doFilterInternal_fromSubdomain() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn(null);
        when(request.getServerName()).thenReturn("acme-corp.yourplatform.com");
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(organizationService.findBySlug("acme-corp")).thenReturn(buildOrg("acme-corp"));

        UUID[] tenantDuringFilter = {null};
        doAnswer(inv -> {
            tenantDuringFilter[0] = TenantContext.getCurrentTenantOrNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(tenantDuringFilter[0]).isEqualTo(testOrgId);
    }

    @Test
    @DisplayName("doFilterInternal: resolve tenant from path /api/v1/t/{slug}/...")
    void doFilterInternal_fromPath() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn(null);
        when(request.getServerName()).thenReturn("auth.yourplatform.com");
        when(request.getRequestURI()).thenReturn("/api/v1/t/acme-corp/users");
        when(organizationService.findBySlug("acme-corp")).thenReturn(buildOrg("acme-corp"));

        UUID[] tenantDuringFilter = {null};
        doAnswer(inv -> {
            tenantDuringFilter[0] = TenantContext.getCurrentTenantOrNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(tenantDuringFilter[0]).isEqualTo(testOrgId);
    }

    @Test
    @DisplayName("doFilterInternal: header has higher priority than subdomain")
    void doFilterInternal_headerWinsOverSubdomain() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn("acme-corp");
        when(request.getServerName()).thenReturn("other-org.yourplatform.com");
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(organizationService.findBySlug("acme-corp")).thenReturn(buildOrg("acme-corp"));

        UUID[] tenantDuringFilter = {null};
        doAnswer(inv -> {
            tenantDuringFilter[0] = TenantContext.getCurrentTenantOrNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(tenantDuringFilter[0]).isEqualTo(testOrgId);
        verify(organizationService, never()).findBySlug("other-org");
    }

    @Test
    @DisplayName("doFilterInternal: inactive tenant → does not set TenantContext")
    void doFilterInternal_inactiveOrg_doesNotSetTenant() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn("suspended-org");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        Organization suspendedOrg = Organization.builder().slug("suspended-org").build();
        suspendedOrg.setStatus(com.identityplatform.organization.domain.OrganizationStatus.SUSPENDED);
        when(organizationService.findBySlug("suspended-org")).thenReturn(suspendedOrg);

        UUID[] tenantDuringFilter = {UUID.randomUUID()}; // non-null sentinel
        doAnswer(inv -> {
            tenantDuringFilter[0] = TenantContext.getCurrentTenantOrNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(tenantDuringFilter[0]).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal: TenantContext is cleared after the request (finally block)")
    void doFilterInternal_clearsContextAfterRequest() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn("acme-corp");
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(organizationService.findBySlug("acme-corp")).thenReturn(buildOrg("acme-corp"));

        filter.doFilterInternal(request, response, filterChain);

        // Context was cleared in finally — getCurrentTenant() must throw
        assertThatThrownBy(TenantContext::getCurrentTenant)
                .isInstanceOf(com.identityplatform.core.tenant.TenantNotSetException.class);
    }

    @Test
    @DisplayName("doFilterInternal: tenant not found → does not throw, continues request")
    void doFilterInternal_tenantNotFound_continuesRequest() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn("unknown-org");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        doThrow(new com.identityplatform.core.exception.ResourceNotFoundException("Organization", "unknown-org"))
                .when(organizationService).findBySlug("unknown-org");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("shouldNotFilter: skip filter cho actuator endpoints")
    void shouldNotFilter_actuatorEndpoints() {
        assertThat(filter.shouldNotFilter(mockRequest("/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(mockRequest("/actuator/info"))).isTrue();
        assertThat(filter.shouldNotFilter(mockRequest("/actuator/metrics"))).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: skip filter cho OIDC well-known")
    void shouldNotFilter_oidcWellKnown() {
        assertThat(filter.shouldNotFilter(mockRequest("/.well-known/openid-configuration"))).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: skip filter cho swagger")
    void shouldNotFilter_swaggerEndpoints() {
        assertThat(filter.shouldNotFilter(mockRequest("/v3/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(mockRequest("/v3/api-docs/swagger-config"))).isTrue();
        assertThat(filter.shouldNotFilter(mockRequest("/swagger-ui"))).isTrue();
        assertThat(filter.shouldNotFilter(mockRequest("/swagger-ui.html"))).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter: does not skip API endpoints and OAuth2 endpoints")
    void shouldNotFilter_apiEndpoints_notSkipped() {
        assertThat(filter.shouldNotFilter(mockRequest("/api/v1/users"))).isFalse();
        assertThat(filter.shouldNotFilter(mockRequest("/api/v1/organizations"))).isFalse();
        assertThat(filter.shouldNotFilter(mockRequest("/oauth2/authorize"))).isFalse();
        assertThat(filter.shouldNotFilter(mockRequest("/oauth2/token"))).isFalse();
    }

    @Test
    @DisplayName("resolveTenantSlug: empty slug → null")
    void resolveTenantSlug_emptyHeader_returnsNull() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn("");
        when(request.getServerName()).thenReturn("auth.yourplatform.com");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("resolveTenantSlug: www subdomain is ignored")
    void resolveTenantSlug_wwwSubdomain_ignored() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn(null);
        when(request.getServerName()).thenReturn("www.yourplatform.com");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        filter.doFilterInternal(request, response, filterChain);

        verify(organizationService, never()).findBySlug(any());
    }

    @Test
    @DisplayName("resolveTenantSlug: api subdomain is ignored")
    void resolveTenantSlug_apiSubdomain_ignored() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn(null);
        when(request.getServerName()).thenReturn("api.yourplatform.com");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        filter.doFilterInternal(request, response, filterChain);

        verify(organizationService, never()).findBySlug(any());
    }

    @Test
    @DisplayName("resolveTenantSlug: auth subdomain is ignored")
    void resolveTenantSlug_authSubdomain_ignored() throws ServletException, IOException {
        when(request.getHeader("X-Organization-Slug")).thenReturn(null);
        when(request.getServerName()).thenReturn("auth.yourplatform.com");
        when(request.getRequestURI()).thenReturn("/api/v1/users");

        filter.doFilterInternal(request, response, filterChain);

        verify(organizationService, never()).findBySlug(any());
    }

    // Helper
    private HttpServletRequest mockRequest(String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }
}
