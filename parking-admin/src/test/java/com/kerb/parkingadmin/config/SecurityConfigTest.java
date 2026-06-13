package com.kerb.parkingadmin.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("parking-admin SecurityConfig logout tests")
class SecurityConfigTest {

    private static final String ISSUER_URI = "http://localhost:8080";
    private static final String POST_LOGOUT_REDIRECT_URI = "http://localhost:8095/";

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "issuerUri", ISSUER_URI);
        ReflectionTestUtils.setField(securityConfig, "postLogoutRedirectUri", POST_LOGOUT_REDIRECT_URI);
    }

    @Test
    @DisplayName("rpInitiatedLogoutSuccessHandler: redirects to auth-server /connect/logout with id_token_hint")
    void redirectsToConnectLogoutWithIdTokenHint() throws Exception {
        OidcIdToken idToken = new OidcIdToken(
                "raw-id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", "user-123"));

        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(oidcUser, authorities, "identity-platform");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        LogoutSuccessHandler handler = securityConfig.rpInitiatedLogoutSuccessHandler();
        handler.onLogoutSuccess(request, response, authentication);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(urlCaptor.capture());

        String redirectUrl = urlCaptor.getValue();
        assertThat(redirectUrl).startsWith(ISSUER_URI + "/connect/logout?");
        assertThat(redirectUrl).contains("post_logout_redirect_uri=" + POST_LOGOUT_REDIRECT_URI);
        assertThat(redirectUrl).contains("id_token_hint=raw-id-token-value");
    }

    @Test
    @DisplayName("rpInitiatedLogoutSuccessHandler: omits id_token_hint when principal is not an OidcUser")
    void omitsIdTokenHintForNonOidcAuthentication() throws Exception {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("user", "credentials", "ROLE_USER");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        LogoutSuccessHandler handler = securityConfig.rpInitiatedLogoutSuccessHandler();
        handler.onLogoutSuccess(request, response, authentication);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(urlCaptor.capture());

        String redirectUrl = urlCaptor.getValue();
        assertThat(redirectUrl).isEqualTo(ISSUER_URI + "/connect/logout?post_logout_redirect_uri=" + POST_LOGOUT_REDIRECT_URI);
        assertThat(redirectUrl).doesNotContain("id_token_hint");
    }
}
