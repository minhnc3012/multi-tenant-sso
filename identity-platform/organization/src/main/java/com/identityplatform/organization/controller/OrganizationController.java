package com.identityplatform.organization.controller;

import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.dto.OrganizationDto;
import com.identityplatform.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Manage tenant organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List all organizations (paginated)")
    public ResponseEntity<Page<OrganizationDto.Response>> list(
            @PageableDefault(size = 50) Pageable pageable) {
        Page<Organization> orgs = organizationService.findAll(pageable);
        return ResponseEntity.ok(orgs.map(this::toResponse));
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Create a new organization (onboard a new tenant)")
    public ResponseEntity<OrganizationDto.Response> create(
            @Valid @RequestBody OrganizationDto.CreateRequest request) {

        Organization org = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(org));
    }

    @GetMapping("/slug/{slug}")
    @PreAuthorize("hasAuthority('SCOPE_users:read') or hasRole('PLATFORM_ADMIN') or hasRole('ORG_ADMIN')")
    @Operation(summary = "Look up organization by slug (used by M2M SDK clients to resolve org UUID)")
    public ResponseEntity<OrganizationDto.Response> getBySlug(@PathVariable String slug) {
        Organization org = organizationService.findBySlug(slug);
        return ResponseEntity.ok(toResponse(org));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @orgAccessChecker.canAccess(#id)")
    @Operation(summary = "Get organization details")
    public ResponseEntity<OrganizationDto.Response> getById(@PathVariable UUID id) {
        Organization org = organizationService.findById(id);
        return ResponseEntity.ok(toResponse(org));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @orgAccessChecker.isOrgAdmin(#id)")
    @Operation(summary = "Update organization details")
    public ResponseEntity<OrganizationDto.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody OrganizationDto.UpdateRequest request) {

        Organization org = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(toResponse(org));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Activate an organization")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        organizationService.activateOrganization(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Suspend an organization")
    public ResponseEntity<Void> suspend(@PathVariable UUID id) {
        organizationService.suspendOrganization(id);
        return ResponseEntity.noContent().build();
    }

    private OrganizationDto.Response toResponse(Organization org) {
        return new OrganizationDto.Response(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getPrimaryDomain(),
                org.getStatus(),
                org.isMfaRequired(),
                org.isSelfRegistrationAllowed(),
                org.getLogoUrl(),
                org.getPrimaryColor(),
                org.getCreatedAt(),
                org.getUpdatedAt()
        );
    }
}
