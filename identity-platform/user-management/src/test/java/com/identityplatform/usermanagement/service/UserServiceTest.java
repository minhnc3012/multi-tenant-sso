package com.identityplatform.usermanagement.service;

import com.identityplatform.core.exception.DuplicateResourceException;
import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.usermanagement.domain.AuthProvider;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.domain.UserStatus;
import com.identityplatform.usermanagement.dto.UserDto;
import com.identityplatform.usermanagement.repository.RoleRepository;
import com.identityplatform.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InviteService inviteService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private UserService userService;

    private UUID organizationId;
    private UUID userId;
    private User inviteUserRequest;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        inviteUserRequest = User.builder()
                .id(userId)
                .organizationId(organizationId)
                .email("newuser@acme.com")
                .firstName("Nguyen")
                .lastName("Van A")
                .status(UserStatus.PENDING_VERIFICATION)
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    // ── inviteUser ──────────────────────────────────────────────

    @Test
    @DisplayName("inviteUser: success with a new email that does not yet exist")
    void inviteUser_success() {
        String email = "newuser@acme.com";
        UserDto.InviteRequest request = new UserDto.InviteRequest(email, "Nguyen", "Van A", null, null, null);

        when(userRepository.existsByEmailAndOrganizationIdAndDeletedFalse(email, organizationId))
                .thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });

        User result = userService.inviteUser(organizationId, request);

        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getFirstName()).isEqualTo("Nguyen");
        assertThat(result.getLastName()).isEqualTo("Van A");
        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);

        // Verify invite email was sent
        verify(inviteService).sendInvite(any());

        // Verify save was called
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getOrganizationId()).isEqualTo(organizationId);
    }

    @Test
    @DisplayName("inviteUser: throws DuplicateResourceException when email already exists in the org")
    void inviteUser_duplicateEmail_throwsException() {
        UserDto.InviteRequest request = new UserDto.InviteRequest("existing@acme.com", "Name", "Surname", null, null, null);

        when(userRepository.existsByEmailAndOrganizationIdAndDeletedFalse("existing@acme.com", organizationId))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.inviteUser(organizationId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("existing@acme.com");

        verify(userRepository, never()).save(any());
        verify(inviteService, never()).sendInvite(any());
    }

    @Test
    @DisplayName("inviteUser: roleIds provided does not cause an error")
    void inviteUser_withRoleIds() {
        UUID roleId = UUID.randomUUID();
        UserDto.InviteRequest request = new UserDto.InviteRequest(
                "roleuser@acme.com", "Name", "Surname", Set.of(roleId), null, null);

        when(userRepository.existsByEmailAndOrganizationIdAndDeletedFalse(any(), any()))
                .thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });

        User result = userService.inviteUser(organizationId, request);

        assertThat(result).isNotNull();
        verify(inviteService).sendInvite(any());
    }

    // ── provisionSsoUser ────────────────────────────────────────

    @Test
    @DisplayName("provisionSsoUser: creates a new user when none exists (JIT provisioning)")
    void provisionSsoUser_newUser() {
        UUID orgId = UUID.randomUUID();
        UserDto.SsoProvisionRequest request = new UserDto.SsoProvisionRequest(
                "ssouser@acme.com", "SSO", "User", "ext-sub-123", AuthProvider.AZURE_AD);

        Role orgMember = Role.builder()
                .id(UUID.randomUUID())
                .name(Role.ORG_MEMBER)
                .systemRole(true)
                .build();

        when(userRepository.findByExternalSubjectIdAndOrganizationId("ext-sub-123", orgId))
                .thenReturn(Optional.empty());
        when(roleRepository.findByNameAndSystemRoleTrue("ORG_MEMBER"))
                .thenReturn(Optional.of(orgMember));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });

        User result = userService.provisionSsoUser(orgId, request);

        assertThat(result.getEmail()).isEqualTo("ssouser@acme.com");
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.AZURE_AD);
        assertThat(result.getExternalSubjectId()).isEqualTo("ext-sub-123");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.getEmailVerifiedAt()).isNotNull();
        assertThat(result.getRoles()).containsExactly(orgMember);

        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("provisionSsoUser: assigns no roles when ORG_MEMBER system role is not configured")
    void provisionSsoUser_newUser_noOrgMemberRole_assignsEmptyRoles() {
        UUID orgId = UUID.randomUUID();
        UserDto.SsoProvisionRequest request = new UserDto.SsoProvisionRequest(
                "ssouser@acme.com", "SSO", "User", "ext-sub-123", AuthProvider.AZURE_AD);

        when(userRepository.findByExternalSubjectIdAndOrganizationId("ext-sub-123", orgId))
                .thenReturn(Optional.empty());
        when(roleRepository.findByNameAndSystemRoleTrue("ORG_MEMBER"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });

        User result = userService.provisionSsoUser(orgId, request);

        assertThat(result.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("provisionSsoUser: returns the existing user when already present (no duplicate)")
    void provisionSsoUser_existingUser_returnsExisting() {
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .email("existing@acme.com")
                .externalSubjectId("ext-sub-123")
                .authProvider(AuthProvider.GOOGLE)
                .build();

        when(userRepository.findByExternalSubjectIdAndOrganizationId("ext-sub-123", organizationId))
                .thenReturn(Optional.of(existingUser));

        User result = userService.provisionSsoUser(organizationId,
                new UserDto.SsoProvisionRequest("existing@acme.com", "F", "L", "ext-sub-123", AuthProvider.GOOGLE));

        assertThat(result.getId()).isEqualTo(existingUser.getId());
        assertThat(result.getEmail()).isEqualTo("existing@acme.com");
        // save is not called because the user already exists
        verify(userRepository, never()).save(any());
    }

    // ── recordLogin ─────────────────────────────────────────────

    @Test
    @DisplayName("recordLogin: updates lastLoginAt")
    void recordLogin_updatesLastLoginAt() {
        userService.recordLogin(userId);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).updateLastLoginAt(eq(userId), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isNotNull();
    }

    // ── changePassword ──────────────────────────────────────────

    @Test
    @DisplayName("changePassword: success when current password is correct")
    void changePassword_success() {
        User user = User.builder()
                .id(userId)
                .passwordHash("$2a$12$existingHash")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("currentPass", "$2a$12$existingHash")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("$2a$12$newHash");

        userService.changePassword(userId, "currentPass", "newPass");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newHash");
        verify(userRepository).save(user);
        verify(passwordEncoder).matches("currentPass", "$2a$12$existingHash");
        verify(passwordEncoder).encode("newPass");
    }

    @Test
    @DisplayName("changePassword: throws exception when current password is wrong")
    void changePassword_wrongCurrentPassword_throwsException() {
        User user = User.builder()
                .id(userId)
                .passwordHash("$2a$12$existingHash")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "$2a$12$existingHash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, "wrongPass", "newPass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    // ── suspendUser ─────────────────────────────────────────────

    @Test
    @DisplayName("suspendUser: changes status to SUSPENDED")
    void suspendUser_success() {
        User user = User.builder()
                .id(userId)
                .organizationId(organizationId)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.suspendUser(organizationId, userId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("suspendUser: throws when user does not belong to the org")
    void suspendUser_userNotInOrg_throwsException() {
        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.suspendUser(organizationId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findByOrganization ──────────────────────────────────────

    @Test
    @DisplayName("findByOrganization: returns a paginated list")
    void findByOrganization_returnsPaged() {
        User user1 = User.builder().id(UUID.randomUUID()).organizationId(organizationId).email("a@acme.com").build();
        User user2 = User.builder().id(UUID.randomUUID()).organizationId(organizationId).email("b@acme.com").build();

        Page<User> userPage = new PageImpl<>(List.of(user1, user2), PageRequest.of(0, 20), 2);

        when(userRepository.findByOrganizationIdAndDeletedFalse(organizationId, PageRequest.of(0, 20)))
                .thenReturn(userPage);

        Page<User> result = userService.findByOrganization(organizationId, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // ── findById / findByIdAndOrg / findByEmailAndOrg ───────────

    @Test
    @DisplayName("findById: success")
    void findById_success() {
        User user = User.builder().id(userId).organizationId(organizationId).email("test@acme.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        User result = userService.findById(userId);

        assertThat(result.getId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("findById: throws ResourceNotFoundException when not found")
    void findById_notFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");
    }

    // ── findClaimsById ──────────────────────────────────────────

    @Test
    @DisplayName("findClaimsById: returns roles and permissions for a user with roles")
    void findClaimsById_userWithRoles_returnsRolesAndPermissions() {
        Role orgMember = Role.builder()
                .id(UUID.randomUUID())
                .name(Role.ORG_MEMBER)
                .systemRole(true)
                .permissions(Set.of("users:read", "users:write"))
                .build();

        User user = User.builder()
                .id(userId)
                .organizationId(organizationId)
                .email("test@acme.com")
                .firstName("Nguyen")
                .lastName("Van A")
                .roles(Set.of(orgMember))
                .build();

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));

        UserService.UserClaims claims = userService.findClaimsById(userId);

        assertThat(claims.organizationId()).isEqualTo(organizationId);
        assertThat(claims.email()).isEqualTo("test@acme.com");
        assertThat(claims.roles()).containsExactly("ORG_MEMBER");
        assertThat(claims.permissions()).containsExactlyInAnyOrder("users:read", "users:write");
    }

    @Test
    @DisplayName("findClaimsById: returns empty roles/permissions for a user with no roles (LEFT JOIN FETCH)")
    void findClaimsById_userWithNoRoles_returnsEmptyRolesAndPermissions() {
        User user = User.builder()
                .id(userId)
                .organizationId(organizationId)
                .email("noroles@acme.com")
                .firstName("Tran")
                .lastName("Van B")
                .roles(Collections.emptySet())
                .build();

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));

        UserService.UserClaims claims = userService.findClaimsById(userId);

        assertThat(claims.email()).isEqualTo("noroles@acme.com");
        assertThat(claims.roles()).isEmpty();
        assertThat(claims.permissions()).isEmpty();
    }

    @Test
    @DisplayName("findClaimsById: throws ResourceNotFoundException when user not found")
    void findClaimsById_notFound_throwsException() {
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findClaimsById(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByIdAndOrg: success when user belongs to the org")
    void findByIdAndOrg_success() {
        User user = User.builder().id(userId).organizationId(organizationId).build();
        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));

        User result = userService.findByIdAndOrg(userId, organizationId);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("findByIdAndOrg: throws when user does not belong to the org")
    void findByIdAndOrg_wrongOrg_throwsException() {
        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByIdAndOrg(userId, organizationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByIdAndOrg: throws when user is soft-deleted")
    void findByIdAndOrg_softDeleted_throwsException() {
        User user = User.builder().id(userId).organizationId(organizationId).build();
        user.setDeleted(true);
        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByIdAndOrg(userId, organizationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByEmailAndOrg: success")
    void findByEmailAndOrg_success() {
        User user = User.builder()
                .id(userId)
                .organizationId(organizationId)
                .email("test@acme.com")
                .build();

        when(userRepository.findByEmailAndOrganizationIdAndDeletedFalse("test@acme.com", organizationId))
                .thenReturn(Optional.of(user));

        User result = userService.findByEmailAndOrg("test@acme.com", organizationId);
        assertThat(result.getEmail()).isEqualTo("test@acme.com");
    }

    @Test
    @DisplayName("findByEmailAndOrg: throws when not found")
    void findByEmailAndOrg_notFound_throwsException() {
        when(userRepository.findByEmailAndOrganizationIdAndDeletedFalse("test@acme.com", organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmailAndOrg("test@acme.com", organizationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("test@acme.com");
    }

    // ── updateUser ──────────────────────────────────────────────

    @Test
    @DisplayName("updateUser: updates all provided fields")
    void updateUser_success() {
        User user = User.builder()
                .id(userId).organizationId(organizationId)
                .email("old@acme.com").firstName("Old").lastName("Name").build();

        UserDto.UpdateRequest request = new UserDto.UpdateRequest("new@acme.com", "New", "Name", null);

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndOrganizationIdAndDeletedFalse("new@acme.com", organizationId))
                .thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateUser(organizationId, userId, request);

        assertThat(result.getEmail()).isEqualTo("new@acme.com");
        assertThat(result.getFirstName()).isEqualTo("New");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateUser: throws DuplicateResourceException when new email already taken")
    void updateUser_duplicateEmail_throwsException() {
        User user = User.builder()
                .id(userId).organizationId(organizationId).email("old@acme.com").build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndOrganizationIdAndDeletedFalse("taken@acme.com", organizationId))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(organizationId, userId,
                new UserDto.UpdateRequest("taken@acme.com", null, null, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("taken@acme.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser: skips email check when email is unchanged")
    void updateUser_sameEmail_noUniquenessCheck() {
        User user = User.builder()
                .id(userId).organizationId(organizationId).email("same@acme.com").build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(organizationId, userId,
                new UserDto.UpdateRequest("same@acme.com", "Updated", null, null));

        verify(userRepository, never()).existsByEmailAndOrganizationIdAndDeletedFalse(any(), any());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateUserAsAdmin: updates user without org restriction")
    void updateUserAsAdmin_success() {
        User user = User.builder()
                .id(userId).organizationId(organizationId)
                .email("old@acme.com").firstName("Old").lastName("Name").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndOrganizationIdAndDeletedFalse("new@acme.com", organizationId))
                .thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateUserAsAdmin(userId,
                new UserDto.UpdateRequest("new@acme.com", "New", "Name", null));

        assertThat(result.getEmail()).isEqualTo("new@acme.com");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateUserAsAdmin: throws ResourceNotFoundException when user not found")
    void updateUserAsAdmin_notFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserAsAdmin(userId,
                new UserDto.UpdateRequest("x@x.com", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findAll ─────────────────────────────────────────────────

    @Test
    @DisplayName("findAll: returns users across all organizations")
    void findAll_returnsPaged() {
        User u1 = User.builder().id(UUID.randomUUID()).organizationId(UUID.randomUUID()).email("a@a.com").build();
        User u2 = User.builder().id(UUID.randomUUID()).organizationId(UUID.randomUUID()).email("b@b.com").build();
        Page<User> page = new PageImpl<>(List.of(u1, u2), PageRequest.of(0, 20), 2);

        when(userRepository.findAllByDeletedFalse(PageRequest.of(0, 20))).thenReturn(page);

        Page<User> result = userService.findAll(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // ── User helper methods ─────────────────────────────────────

    @Test
    @DisplayName("User.isActive: true when status = ACTIVE")
    void user_isActive_active() {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("User.isActive: false when status is not ACTIVE")
    void user_isActive_notActive() {
        User user = User.builder().status(UserStatus.SUSPENDED).build();
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("User.hasLocalPassword: true when passwordHash is present")
    void user_hasLocalPassword_withPassword() {
        User user = User.builder().passwordHash("$2a$12$hash").build();
        assertThat(user.hasLocalPassword()).isTrue();
    }

    @Test
    @DisplayName("User.hasLocalPassword: false when passwordHash is null (SSO)")
    void user_hasLocalPassword_nullPassword() {
        User user = User.builder().authProvider(AuthProvider.GOOGLE).build();
        assertThat(user.hasLocalPassword()).isFalse();
    }

    @Test
    @DisplayName("User.getFullName: concatenates firstName and lastName")
    void user_getFullName() {
        User user = User.builder().firstName("Nguyen").lastName("Van A").build();
        assertThat(user.getFullName()).isEqualTo("Nguyen Van A");
    }
}
