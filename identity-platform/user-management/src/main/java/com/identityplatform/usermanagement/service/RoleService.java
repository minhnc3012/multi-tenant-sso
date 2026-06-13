package com.identityplatform.usermanagement.service;

import com.identityplatform.core.exception.DuplicateResourceException;
import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.repository.RoleRepository;
import com.identityplatform.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    /**
     * Creates a custom role for an organization.
     * In addition to built-in roles (ORG_ADMIN, ORG_MEMBER),
     * an org admin can create additional custom roles.
     */
    @Transactional
    public Role createRole(UUID organizationId, String name,
                           String description, Set<String> permissions) {
        if (roleRepository.existsByNameAndOrganizationId(name, organizationId)) {
            throw new DuplicateResourceException(
                    "Role '" + name + "' already exists in this organization");
        }

        Role role = Role.builder()
                .organizationId(organizationId)
                .name(name)
                .description(description)
                .permissions(permissions)
                .systemRole(false)
                .build();

        return roleRepository.save(role);
    }

    @Transactional
    public void assignRoleToUser(UUID organizationId, UUID userId, UUID roleId) {
        User user = findUserInOrg(userId, organizationId);
        Role role = findRoleAccessible(roleId, organizationId);

        user.getRoles().add(role);
        userRepository.save(user);
        log.info("Role {} assigned to user {} in org {}", roleId, userId, organizationId);
    }

    @Transactional
    public void removeRoleFromUser(UUID organizationId, UUID userId, UUID roleId) {
        User user = findUserInOrg(userId, organizationId);
        user.getRoles().removeIf(r -> r.getId().equals(roleId));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<Role> findRolesByOrganization(UUID organizationId) {
        // Returns both system roles + org-specific roles
        return roleRepository.findByOrganizationIdOrSystemRoleTrue(organizationId);
    }

    @Transactional
    public Role updatePermissions(UUID organizationId, UUID roleId, Set<String> permissions) {
        Role role = findRoleAccessible(roleId, organizationId);

        if (role.isSystemRole()) {
            throw new IllegalArgumentException("Cannot modify permissions of system roles");
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(UUID organizationId, UUID roleId) {
        Role role = findRoleAccessible(roleId, organizationId);

        if (role.isSystemRole()) {
            throw new IllegalArgumentException("Cannot delete system roles");
        }

        role.setDeleted(true);
        roleRepository.save(role);
    }

    private User findUserInOrg(UUID userId, UUID organizationId) {
        return userRepository.findByIdAndOrganizationIdAndDeletedFalse(userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Role findRoleAccessible(UUID roleId, UUID organizationId) {
        return roleRepository.findById(roleId)
                .filter(r -> !r.isDeleted())
                .filter(r -> r.isSystemRole() || organizationId.equals(r.getOrganizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    }
}
