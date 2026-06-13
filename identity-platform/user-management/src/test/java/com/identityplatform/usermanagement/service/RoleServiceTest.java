package com.identityplatform.usermanagement.service;

import com.identityplatform.core.exception.DuplicateResourceException;
import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.repository.RoleRepository;
import com.identityplatform.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService tests")
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleService roleService;

    private UUID organizationId;
    private UUID roleId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // ── createRole ─────────────────────────────────────────────

    @Test
    @DisplayName("createRole: success when role name does not yet exist")
    void createRole_success() {
        Set<String> permissions = Set.of("users:read", "users:write", "billing:read");

        when(roleRepository.existsByNameAndOrganizationId("BILLING_MANAGER", organizationId))
                .thenReturn(false);
        when(roleRepository.save(any())).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(roleId);
            return role;
        });

        Role result = roleService.createRole(organizationId, "BILLING_MANAGER", "Billing Manager", permissions);

        assertThat(result.getName()).isEqualTo("BILLING_MANAGER");
        assertThat(result.getDescription()).isEqualTo("Billing Manager");
        assertThat(result.getPermissions()).isEqualTo(permissions);
        assertThat(result.isSystemRole()).isFalse();
        assertThat(result.getOrganizationId()).isEqualTo(organizationId);
        verify(roleRepository).save(any());
    }

    @Test
    @DisplayName("createRole: throws DuplicateResourceException when role name already exists in the org")
    void createRole_duplicateName_throwsException() {
        when(roleRepository.existsByNameAndOrganizationId("VIEWER", organizationId))
                .thenReturn(true);

        assertThatThrownBy(() -> roleService.createRole(
                        organizationId, "VIEWER", "Viewer", Set.of("*:read")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("VIEWER");

        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRole: null permissions does not cause NPE")
    void createRole_nullPermissions() {
        when(roleRepository.existsByNameAndOrganizationId("VIEWER", organizationId))
                .thenReturn(false);
        when(roleRepository.save(any())).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(roleId);
            return role;
        });

        Role result = roleService.createRole(organizationId, "VIEWER", "Viewer", null);

        assertThat(result).isNotNull();
        // MapStruct/Lombok will default to a HashSet for the permissions field
        verify(roleRepository).save(any());
    }

    // ── assignRoleToUser ────────────────────────────────────────

    @Test
    @DisplayName("assignRoleToUser: success")
    void assignRoleToUser_success() {
        User user = User.builder().id(userId).organizationId(organizationId).build();
        Role role = Role.builder().id(roleId).organizationId(organizationId).name("VIEWER").build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);

        roleService.assignRoleToUser(organizationId, userId, roleId);

        assertThat(user.getRoles()).contains(role);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("assignRoleToUser: throws when user does not belong to the org")
    void assignRoleToUser_userNotFound_throwsException() {
        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.assignRoleToUser(organizationId, userId, roleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("assignRoleToUser: throws when role does not exist")
    void assignRoleToUser_roleNotFound_throwsException() {
        User user = User.builder().id(userId).organizationId(organizationId).build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.assignRoleToUser(organizationId, userId, roleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("assignRoleToUser: throws when role belongs to a different org")
    void assignRoleToUser_roleWrongOrg_throwsException() {
        User user = User.builder().id(userId).organizationId(organizationId).build();
        Role wrongOrgRole = Role.builder()
                .id(roleId)
                .organizationId(UUID.randomUUID())
                .name("WRONG")
                .build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(wrongOrgRole));

        assertThatThrownBy(() -> roleService.assignRoleToUser(organizationId, userId, roleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("assignRoleToUser: can assign a system role")
    void assignRoleToUser_systemRole() {
        User user = User.builder().id(userId).organizationId(organizationId).build();
        Role systemRole = Role.builder()
                .id(roleId)
                .name(Role.ORG_MEMBER)
                .systemRole(true)
                .build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(systemRole));
        when(userRepository.save(any())).thenReturn(user);

        // Does not throw — system roles are allowed
        roleService.assignRoleToUser(organizationId, userId, roleId);
        assertThat(user.getRoles()).contains(systemRole);
    }

    // ── removeRoleFromUser ──────────────────────────────────────

    @Test
    @DisplayName("removeRoleFromUser: success")
    void removeRoleFromUser_success() {
        Role role = Role.builder().id(roleId).build();
        User user = User.builder().id(userId).roles(Set.of(role)).build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        roleService.removeRoleFromUser(organizationId, userId, roleId);

        assertThat(user.getRoles()).doesNotContain(role);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("removeRoleFromUser: does not throw when role is not assigned to the user")
    void removeRoleFromUser_roleNotAssigned_doesNothing() {
        Role otherRole = Role.builder().id(UUID.randomUUID()).build();
        User user = User.builder().id(userId).roles(Set.of(otherRole)).build();

        when(userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        roleService.removeRoleFromUser(organizationId, userId, roleId);

        assertThat(user.getRoles()).contains(otherRole);
    }

    // ── findRolesByOrganization ────────────────────────────────

    @Test
    @DisplayName("findRolesByOrganization: returns system roles + org roles")
    void findRolesByOrganization_returnsMixedRoles() {
        Role orgRole = Role.builder().id(UUID.randomUUID()).organizationId(organizationId).name("CUSTOM").build();
        Role systemRole = Role.builder().id(UUID.randomUUID()).systemRole(true).name("ORG_ADMIN").build();

        when(roleRepository.findByOrganizationIdOrSystemRoleTrue(organizationId))
                .thenReturn(List.of(orgRole, systemRole));

        List<Role> result = roleService.findRolesByOrganization(organizationId);

        assertThat(result).hasSize(2);
    }

    // ── updatePermissions ───────────────────────────────────────

    @Test
    @DisplayName("updatePermissions: success for a custom role")
    void updatePermissions_success() {
        Role role = Role.builder()
                .id(roleId)
                .organizationId(organizationId)
                .systemRole(false)
                .permissions(Set.of("users:read"))
                .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Set<String> newPerms = Set.of("users:read", "users:write", "billing:write");
        Role result = roleService.updatePermissions(organizationId, roleId, newPerms);

        assertThat(result.getPermissions()).isEqualTo(newPerms);
        verify(roleRepository).save(role);
    }

    @Test
    @DisplayName("updatePermissions: throws when attempting to modify a system role")
    void updatePermissions_systemRole_throwsException() {
        Role systemRole = Role.builder()
                .id(roleId)
                .name(Role.PLATFORM_ADMIN)
                .systemRole(true)
                .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> roleService.updatePermissions(organizationId, roleId, Set.of("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot modify permissions of system roles");
    }

    @Test
    @DisplayName("updatePermissions: throws when role does not exist")
    void updatePermissions_roleNotFound_throwsException() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.updatePermissions(organizationId, roleId, Set.of("x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteRole ──────────────────────────────────────────────

    @Test
    @DisplayName("deleteRole: soft-delete custom role")
    void deleteRole_success() {
        Role role = Role.builder()
                .id(roleId)
                .organizationId(organizationId)
                .systemRole(false)
                .build();

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        roleService.deleteRole(organizationId, roleId);

        assertThat(role.isDeleted()).isTrue();
        verify(roleRepository).save(role);
    }

    @Test
    @DisplayName("deleteRole: throws when attempting to delete a system role")
    void deleteRole_systemRole_throwsException() {
        Role systemRole = Role.builder()
                .id(roleId)
                .name(Role.ORG_ADMIN)
                .systemRole(true)
                .build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> roleService.deleteRole(organizationId, roleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot delete system roles");

        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteRole: throws when role does not exist")
    void deleteRole_notFound_throwsException() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.deleteRole(organizationId, roleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── System role constants ───────────────────────────────────

    @Test
    @DisplayName("Role.PLATFORM_ADMIN constant exists")
    void systemRole_constantsExist() {
        assertThat(Role.PLATFORM_ADMIN).isEqualTo("PLATFORM_ADMIN");
        assertThat(Role.ORG_ADMIN).isEqualTo("ORG_ADMIN");
        assertThat(Role.ORG_MEMBER).isEqualTo("ORG_MEMBER");
    }
}
