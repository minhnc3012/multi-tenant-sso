package com.identityplatform.platformadmin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String AUTH_SERVER_LOGOUT_URI = "http://localhost:8080/connect/logout";
    private static final String POST_LOGOUT_REDIRECT    = "http://localhost:8090/";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                // Every other endpoint requires PLATFORM_ADMIN (mapped from JWT roles claim)
                .anyRequest().hasRole("PLATFORM_ADMIN")
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(ui -> ui.userAuthoritiesMapper(rolesMapper()))
                .defaultSuccessUrl("/admin/users", true)
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessHandler(rpInitiatedLogout())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }

    /**
     * Maps the custom `roles` claim from the ID token into Spring Security GrantedAuthorities.
     * Auth-server's MultiTenantTokenCustomizer writes e.g. ["PLATFORM_ADMIN"] into the ID token.
     * Without this mapper, only SCOPE_* authorities would be created, breaking @PreAuthorize checks.
     */
    private GrantedAuthoritiesMapper rolesMapper() {
        return (authorities) -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            authorities.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(OidcUserAuthority.class::cast)
                .map(OidcUserAuthority::getIdToken)
                .<List<String>>map(t -> t.getClaim("roles"))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .forEach(mapped::add);
            return mapped;
        };
    }

    /**
     * RP-Initiated Logout: after clearing the local session, redirect to auth-server's
     * end_session endpoint with the id_token_hint so the IdP session is also cleared.
     */
    private LogoutSuccessHandler rpInitiatedLogout() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            StringBuilder url = new StringBuilder(AUTH_SERVER_LOGOUT_URI)
                .append("?post_logout_redirect_uri=").append(POST_LOGOUT_REDIRECT);
            if (authentication instanceof OAuth2AuthenticationToken token
                    && token.getPrincipal() instanceof OidcUser oidcUser) {
                OidcIdToken idToken = oidcUser.getIdToken();
                if (idToken != null) {
                    url.append("&id_token_hint=").append(idToken.getTokenValue());
                }
            }
            response.sendRedirect(url.toString());
        };
    }
}
