package com.identityplatform.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Auth Server — OIDC/OAuth2 Authorization Server (Authentication API).
 * UserController/RoleController (user-management) and OrganizationController (organization)
 * are excluded here so they are exposed only by admin-service (Management API, port 8081).
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.identityplatform",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = {
                                "com\\.identityplatform\\.usermanagement\\.controller\\..*",
                                "com\\.identityplatform\\.organization\\.controller\\..*"
                        }
                )
        }
)
@EntityScan(basePackages = "com.identityplatform")
@EnableJpaRepositories(basePackages = "com.identityplatform")
@EnableJpaAuditing
@EnableAsync
public class IdentityPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityPlatformApplication.class, args);
    }
}
