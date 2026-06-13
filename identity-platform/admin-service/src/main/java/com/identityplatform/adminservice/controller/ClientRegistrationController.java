package com.identityplatform.adminservice.controller;

import com.identityplatform.adminservice.dto.ClientRegistrationDto;
import com.identityplatform.adminservice.service.ClientRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Registers new OAuth2 clients for org apps.
 * GET (list) is handled by RegisteredClientController to avoid duplicate mapping.
 *
 * <p>Typical flow:
 * <ol>
 *   <li>Platform admin creates an org (POST /api/v1/organizations)</li>
 *   <li>Platform admin or org admin calls POST /api/v1/organizations/{orgId}/clients</li>
 *   <li>Response contains clientId + clientSecret (shown ONCE — store securely)</li>
 *   <li>Org's app configures these credentials in application.yml / env vars</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/clients")
@RequiredArgsConstructor
@Tag(name = "Client Registration", description = "Register OAuth2 clients for org apps")
public class ClientRegistrationController {

    private final ClientRegistrationService registrationService;

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORG_ADMIN')")
    @Operation(summary = "Register a new OAuth2 client for this organization's app")
    public ResponseEntity<ClientRegistrationDto.RegisterResponse> register(
            @PathVariable UUID orgId,
            @Valid @RequestBody ClientRegistrationDto.RegisterRequest request) {

        ClientRegistrationDto.RegisterResponse response = registrationService.registerClient(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
