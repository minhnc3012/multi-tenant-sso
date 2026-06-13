package com.identityplatform.organization.dto;

import com.identityplatform.organization.domain.OrganizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class OrganizationDto {

    // Records use canonical constructor — no @Builder needed
    public record CreateRequest(
            @NotBlank
            @Size(min = 2, max = 100)
            String name,

            @NotBlank
            @Size(min = 2, max = 50)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug may only contain lowercase letters, digits, and hyphens")
            String slug,

            String primaryDomain,

            String adminEmail,

            String adminFirstName,

            String adminLastName,

            boolean mfaRequired,

            boolean selfRegistrationAllowed
    ) {}

    public record UpdateRequest(
            @Size(min = 2, max = 100)
            String name,

            String primaryDomain,

            Boolean mfaRequired,

            Boolean selfRegistrationAllowed,

            String logoUrl,

            String primaryColor
    ) {}

    public record Response(
            UUID id,
            String name,
            String slug,
            String primaryDomain,
            OrganizationStatus status,
            boolean mfaRequired,
            boolean selfRegistrationAllowed,
            String logoUrl,
            String primaryColor,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
