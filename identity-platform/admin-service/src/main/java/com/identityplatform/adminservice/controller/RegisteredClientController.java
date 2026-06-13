package com.identityplatform.adminservice.controller;

import com.identityplatform.adminservice.service.RegisteredClientMetadataService;
import com.identityplatform.adminservice.service.RegisteredClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Registered Clients", description = "Manage OAuth2/OIDC registered clients")
public class RegisteredClientController {

    private final RegisteredClientService clientService;
    private final RegisteredClientMetadataService metadataService;

    // ── Platform-level endpoints (PLATFORM_ADMIN only) ──────────────────────

    @GetMapping("/api/v1/registered-clients")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List all registered clients (with metadata)")
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(clientService.listAllWithMetadata());
    }

    @GetMapping("/api/v1/registered-clients/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Get client details by internal ID")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        Map<String, Object> client = clientService.getByIdWithMetadata(id);
        return client == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(client);
    }

    @PostMapping("/api/v1/registered-clients")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Create a new registered client")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateClientRequest request) {
        Map<String, Object> client = clientService.create(
                request.clientId(), request.clientName(), request.clientSecret(),
                request.redirectUris(), request.scopes(),
                request.authorizationGrantTypes(), request.clientAuthenticationMethods());
        return ResponseEntity.status(201).body(client);
    }

    @PutMapping("/api/v1/registered-clients/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Update client information")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id, @RequestBody UpdateClientRequest request) {
        clientService.update(id, request.clientName(), request.redirectUris(), request.scopes());
        return ResponseEntity.ok(clientService.getByIdWithMetadata(id));
    }

    @PostMapping("/api/v1/registered-clients/rotate-secret/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Rotate client secret")
    public ResponseEntity<Void> rotateSecret(
            @PathVariable String id, @RequestBody RotateSecretRequest request) {
        clientService.rotateSecret(id, request.newSecret());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/registered-clients/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Delete registered client")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Org-scoped endpoints (ORG_ADMIN sees only their org's clients) ───────

    @GetMapping("/api/v1/organizations/{orgId}/clients")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORG_ADMIN')")
    @Operation(summary = "List clients registered for a specific organization")
    public ResponseEntity<List<Map<String, Object>>> listByOrg(@PathVariable UUID orgId) {
        return ResponseEntity.ok(clientService.listByOrganization(orgId));
    }

    // ── Request/Response records ─────────────────────────────────────────────

    public record CreateClientRequest(
            String clientId, String clientName, String clientSecret,
            String redirectUris, String scopes,
            String authorizationGrantTypes, String clientAuthenticationMethods) {}

    public record UpdateClientRequest(String clientName, String redirectUris, String scopes) {}

    public record RotateSecretRequest(String newSecret) {}
}
