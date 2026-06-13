package com.identityplatform.usermanagement.controller;

import com.identityplatform.core.tenant.TenantContext;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.dto.RoleDto;
import com.identityplatform.usermanagement.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role and permission management within an organization")
public class RoleController {

    private final RoleService roleService;

    /** List roles for the current org (resolved via X-Organization-Slug header). */
    @GetMapping
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List roles for the organization (including system roles)")
    public ResponseEntity<List<RoleDto.Response>> list() {
        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(roleService.findRolesByOrganization(orgId)
                .stream().map(this::toResponse).toList());
    }

    /**
     * Platform admin: list roles for a specific org by ID.
     * Use this when inviting users without an org context header.
     */
    @GetMapping("/org/{orgId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List roles for a specific org (PLATFORM_ADMIN only)")
    public ResponseEntity<List<RoleDto.Response>> listForOrg(@PathVariable UUID orgId) {
        return ResponseEntity.ok(roleService.findRolesByOrganization(orgId)
                .stream().map(this::toResponse).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Create a custom role for the organization")
    public ResponseEntity<RoleDto.Response> create(
            @Valid @RequestBody RoleDto.CreateRequest request) {
        UUID orgId = TenantContext.getCurrentTenant();
        Role role = roleService.createRole(
                orgId,
                request.name(),
                request.description(),
                request.permissions() != null ? request.permissions() : new HashSet<>()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(role));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Update role permissions (does not apply to system roles)")
    public ResponseEntity<RoleDto.Response> updatePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody RoleDto.UpdatePermissionsRequest request) {
        UUID orgId = TenantContext.getCurrentTenant();
        Role role = roleService.updatePermissions(orgId, id, request.permissions());
        return ResponseEntity.ok(toResponse(role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Delete a custom role (system roles cannot be deleted)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID orgId = TenantContext.getCurrentTenant();
        roleService.deleteRole(orgId, id);
        return ResponseEntity.noContent().build();
    }

    private RoleDto.Response toResponse(Role role) {
        return new RoleDto.Response(
                role.getId(),
                role.getOrganizationId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions(),
                role.isSystemRole()
        );
    }
}
