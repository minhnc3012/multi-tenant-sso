package com.identityplatform.adminservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Admin Service — stateless REST API for user, org, and role management.
 * Port: 8081. Validates JWT tokens issued by auth-server (port 8080).
 *
 * scanBasePackages = "com.identityplatform" loads organization + user-management beans
 * (controllers, services, repos) into this Spring context.
 *
 * The main class is in com.identityplatform.adminservice (NOT the root package) so that
 * Spring Modulith's scan scope stays within adminservice — preventing core/organization/
 * usermanagement from being detected as Modulith modules and causing type-ref violations.
 */
@SpringBootApplication(scanBasePackages = "com.identityplatform")
@EntityScan(basePackages = "com.identityplatform")
@EnableJpaRepositories(basePackages = "com.identityplatform")
@EnableJpaAuditing
@EnableAsync
public class AdminServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
