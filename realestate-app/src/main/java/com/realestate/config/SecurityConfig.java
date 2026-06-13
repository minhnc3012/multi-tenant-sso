package com.realestate.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String IDP_LOGOUT_URL      = "http://localhost:8080/connect/logout";
    private static final String POST_LOGOUT_REDIRECT = "http://localhost:8084/";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/dashboard", true)
            )
            .logout(logout -> logout
                .logoutSuccessHandler(oidcRpInitiatedLogout())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            );
        return http.build();
    }

    /**
     * OIDC RP-Initiated Logout: after clearing the local session, redirect to the IdP's
     * /connect/logout so the IdP session is also invalidated. Without this, the IdP session
     * stays alive and subsequent logins skip the login form entirely (SSO bypass).
     */
    private LogoutSuccessHandler oidcRpInitiatedLogout() {
        return new LogoutSuccessHandler() {
            private final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

            @Override
            public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
                StringBuilder url = new StringBuilder(IDP_LOGOUT_URL)
                        .append("?post_logout_redirect_uri=").append(POST_LOGOUT_REDIRECT);

                if (authentication instanceof OAuth2AuthenticationToken token
                        && token.getPrincipal() instanceof OidcUser oidcUser) {
                    url.append("&id_token_hint=").append(oidcUser.getIdToken().getTokenValue());
                }

                redirectStrategy.sendRedirect(request, response, url.toString());
            }
        };
    }
}
