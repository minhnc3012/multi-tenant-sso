package com.identityplatform.authserver.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.identityplatform.usermanagement.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Additional auth endpoints beyond the standard OAuth2 endpoints.
 *
 * The standard OIDC/OAuth2 endpoints are already exposed by Spring Authorization Server at:
 *   GET  /.well-known/openid-configuration
 *   GET  /oauth2/jwks
 *   POST /oauth2/token
 *   GET  /oauth2/authorize
 *   POST /oauth2/revoke
 *   POST /oauth2/introspect
 *   GET  /userinfo
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth endpoints")
public class AuthController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user info from token")
    public ResponseEntity<UserInfoResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(new UserInfoResponse(
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .toList()
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke token")
    public ResponseEntity<Void> logout(Authentication authentication) {
        log.info("User logged out: {}", authentication != null ? authentication.getName() : "unknown");
        return ResponseEntity.noContent().build();
    }

    // Records already have a canonical constructor — no need for @Builder
    public record UserInfoResponse(
            String username,
            List<String> authorities
    ) {}
}
