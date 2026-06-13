package com.identityplatform.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Extracts PasswordEncoder into a separate config to avoid circular dependency:
 *
 * AuthorizationServerConfig → MultiTenantTokenCustomizer → UserService → PasswordEncoder
 *                                                                              ↑
 *                                                    (before: this bean lived in AuthorizationServerConfig)
 *
 * After extraction: UserService → PasswordEncoder (from SecurityBeansConfig, independent of auth config)
 */
@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the global AuthenticationManager so the custom password grant provider
     * can delegate user credential validation to the existing DaoAuthenticationProvider.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
