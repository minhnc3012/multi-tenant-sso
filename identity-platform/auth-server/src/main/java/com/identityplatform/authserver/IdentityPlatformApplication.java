package com.identityplatform.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.identityplatform")
@EntityScan(basePackages = "com.identityplatform")
@EnableJpaRepositories(basePackages = "com.identityplatform")
@EnableJpaAuditing
@EnableAsync
public class IdentityPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityPlatformApplication.class, args);
    }
}
