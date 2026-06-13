package com.kerb.parkingadmin.config;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends VaadinWebSecurity {

    @Value("${spring.security.oauth2.client.provider.identity-platform.issuer-uri}")
    private String issuerUri;

    @Value("${parking-admin.post-logout-redirect-uri:http://localhost:8095/}")
    private String postLogoutRedirectUri;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Permit Vaadin static resources + actuator health
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/actuator/health")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/images/**")).permitAll()
        );

        // Let VaadinWebSecurity handle the rest (Vaadin routes + CSRF for Vaadin)
        super.configure(http);

        // Redirect unauthenticated users to IDP via OAuth2 login
        setOAuth2LoginPage(http, "/oauth2/authorization/identity-platform");

        // On logout, also end the SSO session at the identity provider
        http.logout(logout -> logout.logoutSuccessHandler(rpInitiatedLogoutSuccessHandler()));
    }

    /**
     * RP-Initiated Logout: after clearing the local session, redirect to the auth-server's
     * end_session endpoint with the id_token_hint so the IdP session is also cleared.
     */
    LogoutSuccessHandler rpInitiatedLogoutSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            StringBuilder url = new StringBuilder(issuerUri)
                    .append("/connect/logout")
                    .append("?post_logout_redirect_uri=").append(postLogoutRedirectUri);
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
