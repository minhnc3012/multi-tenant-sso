package com.identityplatform.authserver.security;

import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserPrincipal tests")
class UserPrincipalTest {

    private User createTestUser() {
        return User.builder()
                .id(java.util.UUID.randomUUID())
                .organizationId(java.util.UUID.randomUUID())
                .email("test@acme.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("$2a$12$hash")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("getUsername: returns email")
    void getUsername_returnsEmail() {
        User user = createTestUser();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getUsername()).isEqualTo("test@acme.com");
    }

    @Test
    @DisplayName("getPassword: returns passwordHash")
    void getPassword_returnsPasswordHash() {
        User user = createTestUser();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getPassword()).isEqualTo("$2a$12$hash");
    }

    @Test
    @DisplayName("getUserId: returns user ID")
    void getUserId_returnsId() {
        User user = createTestUser();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("getOrganizationId: returns org ID")
    void getOrganizationId_returnsOrgId() {
        User user = createTestUser();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getOrganizationId()).isEqualTo(user.getOrganizationId());
    }

    @Test
    @DisplayName("isEnabled: true khi status = ACTIVE")
    void isEnabled_active() {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEnabled: false khi status = SUSPENDED → DisabledException")
    void isEnabled_suspended() {
        User user = User.builder().status(UserStatus.SUSPENDED).build();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled: false when status = LOCKED (isAccountNonLocked=false takes priority → LockedException)")
    void isEnabled_locked_returnsFalse() {
        User user = User.builder().status(UserStatus.LOCKED).build();
        UserPrincipal principal = new UserPrincipal(user);

        // isEnabled=false but isAccountNonLocked is also false
        // Spring checks isAccountNonLocked FIRST → LockedException (not DisabledException)
        assertThat(principal.isEnabled()).isFalse();
        assertThat(principal.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("isEnabled: false khi status = PENDING_VERIFICATION → DisabledException")
    void isEnabled_pendingVerification_returnsFalse() {
        User user = User.builder().status(UserStatus.PENDING_VERIFICATION).build();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isEnabled()).isFalse();
        assertThat(principal.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("isEnabled: false when status = DEACTIVATED → DisabledException (not LockedException)")
    void isEnabled_deactivated_returnsFalse() {
        User user = User.builder().status(UserStatus.DEACTIVATED).build();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isEnabled()).isFalse();
        assertThat(principal.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("isAccountNonLocked: true khi status = ACTIVE")
    void isAccountNonLocked_active() {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("isAccountNonLocked: true when status = SUSPENDED (Spring uses isEnabled to throw DisabledException)")
    void isAccountNonLocked_suspended_returnsTrue() {
        User user = User.builder().status(UserStatus.SUSPENDED).build();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("isAccountNonLocked: false khi status = LOCKED → Spring throws LockedException")
    void isAccountNonLocked_locked() {
        User user = User.builder().status(UserStatus.LOCKED).build();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("isAccountNonExpired: always true")
    void isAccountNonExpired_alwaysTrue() {
        User user = createTestUser();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isAccountNonExpired()).isTrue();
    }

    @Test
    @DisplayName("isCredentialsNonExpired: always true")
    void isCredentialsNonExpired_alwaysTrue() {
        User user = createTestUser();
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("getAuthorities: roles only (no permissions)")
    void getAuthorities_rolesOnly() {
        User user = createTestUser();
        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertThat(authorities).isNotNull();

        // No role → authorities empty
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("getAuthorities: role is prefixed with ROLE_")
    void getAuthorities_rolePrefix() {
        Role role = Role.builder().name("ORG_ADMIN").build();
        User user = createTestUser();
        user.setRoles(Set.of(role));

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ORG_ADMIN");
    }

    @Test
    @DisplayName("getAuthorities: permissions are added to authorities")
    void getAuthorities_permissionsAdded() {
        Role role = Role.builder()
                .name("ORG_ADMIN")
                .permissions(Set.of("users:read", "billing:write"))
                .build();
        User user = createTestUser();
        user.setRoles(Set.of(role));

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains(
                        "ROLE_ORG_ADMIN",
                        "users:read",
                        "billing:write"
                );
    }

    @Test
    @DisplayName("getAuthorities: multiple roles + permissions")
    void getAuthorities_multipleRoles() {
        Role adminRole = Role.builder().name("ORG_ADMIN").permissions(Set.of("users:write")).build();
        Role viewerRole = Role.builder().name("VIEWER").permissions(Set.of("reports:read")).build();

        User user = createTestUser();
        user.setRoles(Set.of(adminRole, viewerRole));

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_ORG_ADMIN",
                        "ROLE_VIEWER",
                        "users:write",
                        "reports:read"
                );
    }

    @Test
    @DisplayName("getAuthorities: duplicate permissions are deduped by Set")
    void getAuthorities_noDuplicates() {
        Role role1 = Role.builder()
                .name("ROLE1")
                .permissions(Set.of("shared:read", "only1:write"))
                .build();
        Role role2 = Role.builder()
                .name("ROLE2")
                .permissions(Set.of("shared:read", "only2:write"))
                .build();

        User user = createTestUser();
        user.setRoles(Set.of(role1, role2));

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        // "shared:read" should appear only once
        long sharedCount = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter("shared:read"::equals)
                .count();
        assertThat(sharedCount).isEqualTo(1);
    }
}
