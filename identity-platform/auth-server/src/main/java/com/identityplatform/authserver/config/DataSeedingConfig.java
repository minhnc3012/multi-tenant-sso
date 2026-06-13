package com.identityplatform.authserver.config;

import com.identityplatform.authserver.domain.ClientType;
import com.identityplatform.authserver.service.RegisteredClientMetadataService;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.domain.OrganizationStatus;
import com.identityplatform.organization.repository.OrganizationRepository;
import com.identityplatform.usermanagement.domain.AuthProvider;
import com.identityplatform.usermanagement.domain.Role;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.domain.UserStatus;
import com.identityplatform.usermanagement.repository.RoleRepository;
import com.identityplatform.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.Set;

/**
 * Seeds infrastructure-only data on first startup:
 * - Organization "platform" (primaryDomain=platform.local)
 * - 3 system roles: PLATFORM_ADMIN, ORG_ADMIN, ORG_MEMBER
 * - Default platform admin: admin@platform.local / [ADMIN_PASSWORD]
 *
 * All tenant orgs (Kerb, etc.) are created through the platform-admin UI at http://localhost:8090.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeedingConfig {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegisteredClientMetadataService metadataService;
    private final RegisteredClientRepository registeredClientRepository;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Bean
    @Order(20)
    public ApplicationRunner seedDefaultData() {
        return args -> {
            Organization platform = seedPlatformOrg();
            seedSystemRoles();
            seedAdminUser(platform);
        };
    }

    @Bean
    @Order(30)
    public ApplicationRunner seedClientMetadata() {
        return args -> {
            Organization platform = organizationRepository.findBySlugAndDeletedFalse("platform")
                    .orElseThrow(() -> new IllegalStateException("platform org not found"));

            seedMeta("platform-admin-client", platform.getId(), ClientType.WEB_CLIENT,    "Platform Admin Portal (port 8090)");
            seedMeta("swagger-client",        platform.getId(), ClientType.WEB_CLIENT,    "Platform Swagger UI — local API testing");
        };
    }

    private void seedMeta(String clientId, java.util.UUID orgId, ClientType type, String description) {
        var client = registeredClientRepository.findByClientId(clientId);
        if (client != null) {
            metadataService.ensure(client.getId(), orgId, type, description);
            log.info("[Seed] Client metadata linked: {} → {} ({})", clientId, type, orgId);
        }
    }

    private Organization seedPlatformOrg() {
        return organizationRepository.findBySlugAndDeletedFalse("platform")
                .orElseGet(() -> {
                    Organization org = Organization.builder()
                            .name("Platform Administration")
                            .slug("platform")
                            .primaryDomain("platform.local")
                            .status(OrganizationStatus.ACTIVE)
                            .build();
                    Organization saved = organizationRepository.save(org);
                    log.info("[Seed] Platform organization created (id={})", saved.getId());
                    return saved;
                });
    }

    private void seedSystemRoles() {
        seedSystemRole("PLATFORM_ADMIN", "Platform-wide administrator",
                Set.of("*"));
        seedSystemRole("ORG_ADMIN", "Organization administrator",
                Set.of("users:read", "users:write", "users:delete",
                       "roles:read", "roles:write",
                       "billing:read", "audit:read"));
        seedSystemRole("ORG_MEMBER", "Organization member",
                Set.of("users:read"));
    }

    private Role seedSystemRole(String name, String description, Set<String> permissions) {
        return roleRepository.findByNameAndSystemRoleTrue(name)
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name(name)
                            .description(description)
                            .systemRole(true)
                            .permissions(permissions)
                            .build();
                    Role saved = roleRepository.save(role);
                    log.info("[Seed] System role created: {}", name);
                    return saved;
                });
    }

    private void seedAdminUser(Organization platform) {
        if (userRepository.existsByEmailAndOrganizationIdAndDeletedFalse(
                "admin@platform.local", platform.getId())) {
            return;
        }

        Role platformAdminRole = roleRepository.findByNameAndSystemRoleTrue("PLATFORM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("PLATFORM_ADMIN role not found"));

        User admin = User.builder()
                .organizationId(platform.getId())
                .email("admin@platform.local")
                .firstName("Platform")
                .lastName("Admin")
                .passwordHash(passwordEncoder.encode(adminPassword))
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerifiedAt(Instant.now())
                .systemUser(true)
                .roles(Set.of(platformAdminRole))
                .build();

        userRepository.save(admin);

        log.warn("╔══════════════════════════════════════════════╗");
        log.warn("║         DEFAULT ADMIN USER CREATED           ║");
        log.warn("╠══════════════════════════════════════════════╣");
        log.warn("║  URL      : http://localhost:8090            ║");
        log.warn("║  Email    : admin@platform.local             ║");
        log.warn("║  Password : [see ADMIN_PASSWORD env var]     ║");
        log.warn("╠══════════════════════════════════════════════╣");
        log.warn("║  ⚠ SET ADMIN_PASSWORD env var on production! ║");
        log.warn("╚══════════════════════════════════════════════╝");
    }
}
