package com.identityplatform.authserver.security;

import com.identityplatform.usermanagement.domain.AuthProvider;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.domain.UserStatus;
import com.identityplatform.usermanagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MultiTenantTokenCustomizer is an OAuth2TokenCustomizer<JwtEncodingContext>.
 * Tests use a real JwtClaimsSet.Builder (not mocked) to verify that claims are injected correctly.
 * userService.findById() must be stubbed because customize() calls it to retrieve user data.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiTenantTokenCustomizer tests")
class MultiTenantTokenCustomizerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtEncodingContext context;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private MultiTenantTokenCustomizer tokenCustomizer;

    private UUID userId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        lenient().when(context.getPrincipal()).thenReturn(authentication);
    }

    /**
     * Creates a real JwtClaimsSet.Builder and wires it into the context mock.
     * customize() will call context.getClaims().claim(...) multiple times on the same builder.
     */
    private JwtClaimsSet.Builder setupClaimsBuilder() {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(builder);
        return builder;
    }

    // ── customize: org_id ────────────────────────────────────────

    @Test
    @DisplayName("customize: inject org_id into JWT claims")
    void customize_injectsOrgId() {
        User user = createTestUser();
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(userId)).thenReturn(user);
        JwtClaimsSet.Builder builder = setupClaimsBuilder();

        tokenCustomizer.customize(context);

        JwtClaimsSet claims = builder.build();
        assertThat(claims.<String>getClaim("org_id")).isEqualTo(orgId.toString());
    }

    // ── customize: roles ─────────────────────────────────────────

    @Test
    @DisplayName("customize: inject roles into JWT claims")
    void customize_injectsRoles() {
        User user = createTestUserWithRoles(Set.of("ORG_ADMIN", "VIEWER"));
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(userId)).thenReturn(user);
        JwtClaimsSet.Builder builder = setupClaimsBuilder();

        tokenCustomizer.customize(context);

        JwtClaimsSet claims = builder.build();
        Set<String> roles = claims.getClaim("roles");
        assertThat(roles).containsExactlyInAnyOrder("ORG_ADMIN", "VIEWER");
    }

    @Test
    @DisplayName("customize: user has no roles → roles is empty set")
    void customize_noRoles_emptySet() {
        User user = createTestUser(); // roles = empty set
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(userId)).thenReturn(user);
        JwtClaimsSet.Builder builder = setupClaimsBuilder();

        tokenCustomizer.customize(context);

        JwtClaimsSet claims = builder.build();
        Set<String> roles = claims.getClaim("roles");
        assertThat(roles).isEmpty();
    }

    // ── customize: permissions ───────────────────────────────────

    @Test
    @DisplayName("customize: inject permissions flattened from a single role")
    void customize_injectsFlattenedPermissions() {
        Set<String> perms = Set.of("users:read", "users:write", "billing:read");
        Role role = Role.builder()
                .name("ORG_ADMIN")
                .permissions(perms)
                .build();
        User user = createTestUser();
        user.setRoles(Set.of(role));

        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(userId)).thenReturn(user);
        JwtClaimsSet.Builder builder = setupClaimsBuilder();

        tokenCustomizer.customize(context);

        JwtClaimsSet claims = builder.build();
        Set<String> permissions = claims.getClaim("permissions");
        assertThat(permissions).containsExactlyInAnyOrder("users:read", "users:write", "billing:read");
    }

    @Test
    @DisplayName("customize: permissions from multiple roles are flattened and deduped")
    void customize_multipleRolesFlattenedPermissions() {
        Role adminRole = Role.builder()
                .name("ORG_ADMIN")
                .permissions(Set.of("users:read", "users:write", "users:delete"))
                .build();
        Role viewerRole = Role.builder()
                .name("VIEWER")
                .permissions(Set.of("billing:read", "users:read")) // users:read appears twice
                .build();
        User user = createTestUser();
        user.setRoles(new HashSet<>(Set.of(adminRole, viewerRole)));

        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(userId)).thenReturn(user);
        JwtClaimsSet.Builder builder = setupClaimsBuilder();

        tokenCustomizer.customize(context);

        JwtClaimsSet claims = builder.build();
        Set<String> permissions = claims.getClaim("permissions");
        // users:read appears only once (deduped by Set)
        assertThat(permissions).containsExactlyInAnyOrder(
                "users:read", "users:write", "users:delete", "billing:read");
    }

    // ── customize: email & name ──────────────────────────────────

    @Test
    @DisplayName("customize: inject email and name into JWT claims")
    void customize_injectsEmailAndName() {
        User user = User.builder()
                .id(userId)
                .organizationId(orgId)
                .email("test@acme.com")
                .firstName("Nguyen")
                .lastName("Van A")
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build();

        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(userId)).thenReturn(user);
        JwtClaimsSet.Builder builder = setupClaimsBuilder();

        tokenCustomizer.customize(context);

        JwtClaimsSet claims = builder.build();
        assertThat(claims.<String>getClaim("email")).isEqualTo("test@acme.com");
        assertThat(claims.<String>getClaim("name")).isEqualTo("Nguyen Van A");
    }

    // ── customize: edge cases ────────────────────────────────────

    @Test
    @DisplayName("customize: skips when principal is not a UserPrincipal (e.g. client credentials)")
    void customize_nonUserPrincipal_doesNothing() {
        when(authentication.getPrincipal()).thenReturn("not-a-user-principal");

        tokenCustomizer.customize(context);

        // getClaims() must not be called when principal is not a UserPrincipal
        verify(context, never()).getClaims();
    }

    @Test
    @DisplayName("customize: does not throw exception when UserService fails (error is logged silently)")
    void customize_serviceFails_doesNotThrow() {
        User user = createTestUser();
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(any())).thenThrow(new RuntimeException("DB connection lost"));

        assertThatCode(() -> tokenCustomizer.customize(context)).doesNotThrowAnyException();
        // getClaims() must not be called because exception occurred before it
        verify(context, never()).getClaims();
    }

    @Test
    @DisplayName("customize: all 5 standard claims are injected")
    void customize_allFiveClaimsInjected() {
        Role role = Role.builder()
                .name("ORG_ADMIN")
                .permissions(Set.of("users:read"))
                .build();
        User user = User.builder()
                .id(userId)
                .organizationId(orgId)
                .email("admin@acme.com")
                .firstName("Admin")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .roles(Set.of(role))
                .build();

        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(userService.findById(userId)).thenReturn(user);
        JwtClaimsSet.Builder builder = setupClaimsBuilder();

        tokenCustomizer.customize(context);

        JwtClaimsSet claims = builder.build();
        assertThat(claims.<String>getClaim("org_id")).isNotNull();
        assertThat(claims.<Set<?>>getClaim("roles")).isNotNull();
        assertThat(claims.<Set<?>>getClaim("permissions")).isNotNull();
        assertThat(claims.<String>getClaim("email")).isNotNull();
        assertThat(claims.<String>getClaim("name")).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private User createTestUser() {
        return User.builder()
                .id(userId)
                .organizationId(orgId)
                .email("test@acme.com")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build(); // roles = empty set (Builder.Default)
    }

    private User createTestUserWithRoles(Set<String> roleNames) {
        Set<Role> roles = roleNames.stream()
                .map(name -> Role.builder().name(name).build())
                .collect(Collectors.toSet());
        return User.builder()
                .id(userId)
                .organizationId(orgId)
                .email("test@acme.com")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .roles(roles)
                .build();
    }
}
