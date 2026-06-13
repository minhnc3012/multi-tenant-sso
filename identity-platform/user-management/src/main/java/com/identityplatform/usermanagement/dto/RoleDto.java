package com.identityplatform.usermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public class RoleDto {

    public record CreateRequest(
            @NotBlank String name,
            String description,
            Set<String> permissions
    ) {}

    public record UpdatePermissionsRequest(
            @NotNull Set<String> permissions
    ) {}

    public record Response(
            UUID id,
            UUID organizationId,
            String name,
            String description,
            Set<String> permissions,
            boolean systemRole
    ) {}
}
