package com.identityplatform.authserver.config;

import com.identityplatform.authserver.security.LoginRateLimitFilter;
import com.identityplatform.authserver.security.OrgClientAuthorizationFilter;
import com.identityplatform.authserver.service.RegisteredClientMetadataService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    // ── Externalised configuration ────────────────────────────────────────────
    // All values are resolved from application.yml which reads env vars with
    // safe local-dev defaults (see app: section at the bottom of application.yml).

    @Value("${app.auth.issuer}")
    private String issuer;

    @Value("${app.oauth2.secrets.platform-admin}")
    private String platformAdminSecret;

    @Value("${app.oauth2.secrets.swagger}")
    private String swaggerSecret;

    @Value("${app.oauth2.secrets.realestate-web}")
    private String realestateWebSecret;

    @Value("${app.oauth2.secrets.realestate-api}")
    private String realestateApiSecret;

    @Value("${app.oauth2.secrets.parking-admin-web}")
    private String parkingAdminWebSecret;

    @Value("${app.oauth2.secrets.parking-admin-api}")
    private String parkingAdminApiSecret;

    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

    // ── Security filter chains ────────────────────────────────────────────────

    /**
     * Filter chain for Authorization Server endpoints
     * (.well-known/openid-configuration, /oauth2/authorize, /oauth2/token, etc.)
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            RegisteredClientRepository clientRepository,
            @Lazy RegisteredClientMetadataService metadataService) throws Exception {

        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());

        // Enforces org-to-client access before the authorization code is issued.
        // Anchored after SecurityContextHolderFilter so security context is populated.
        http.addFilterAfter(
                new OrgClientAuthorizationFilter(clientRepository, metadataService),
                SecurityContextHolderFilter.class);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Filter chain for the remaining platform endpoints (form login + REST API).
     * Accepts both session-based auth (form login) and JWT Bearer tokens.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        // Only save browser navigation requests as the "continue after login" target.
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(new NegatedRequestMatcher(
                new OrRequestMatcher(
                        new AntPathRequestMatcher("/.well-known/**"),
                        new AntPathRequestMatcher("/favicon.ico"),
                        new AntPathRequestMatcher("/**/*.json"),
                        new AntPathRequestMatcher("/**/*.js"),
                        new AntPathRequestMatcher("/**/*.css")
                )
        ));

        http
                .requestCache(cache -> cache.requestCache(requestCache))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login", "/logout",
                                "/error/org-access-denied",
                                "/.well-known/**",
                                "/invite/**",
                                "/api/auth/**",
                                "/api/v1/auth/**",
                                "/api/v1/users/invite/accept",
                                "/api/v1/users/password/reset-request",
                                "/api/v1/users/password/reset",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(orgAwareSuccessHandler(requestCache))
                        .permitAll()
                )
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesClaimConverter())))
                .exceptionHandling(ex -> ex.accessDeniedPage("/"));

        return http.build();
    }

    // ── RSA key — persistent across restarts ──────────────────────────────────

    /**
     * JWK source backed by the auth_key_pair table (created by V14 migration).
     *
     * Startup behaviour:
     *   1. Load the active key from DB  → use it (stable kid, valid tokens survive restarts)
     *   2. No key in DB yet             → generate 2048-bit RSA, persist, use it
     *   3. DB unreachable on first run  → generate ephemeral key, log warning
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(JdbcTemplate jdbcTemplate) {
        RSAKey rsaKey = loadOrPersistRsaKey(jdbcTemplate);
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private RSAKey loadOrPersistRsaKey(JdbcTemplate jdbc) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT key_id, private_key_pem, public_key_pem " +
                    "FROM auth_key_pair WHERE active = true ORDER BY created_at DESC LIMIT 1");

            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                String keyId          = (String) row.get("key_id");
                RSAPrivateKey privKey = parseRsaPrivateKey((String) row.get("private_key_pem"));
                RSAPublicKey  pubKey  = parseRsaPublicKey((String)  row.get("public_key_pem"));
                log.info("[JWT] RSA key pair loaded from database (kid={})", keyId);
                return new RSAKey.Builder(pubKey).privateKey(privKey).keyID(keyId).build();
            }
        } catch (Exception e) {
            log.warn("[JWT] Could not load RSA key from DB: {} — generating new key pair", e.getMessage());
        }

        // First boot: generate and persist
        KeyPair kp     = generateRsaKey();
        String  keyId  = UUID.randomUUID().toString();
        RSAPublicKey  pubKey  = (RSAPublicKey)  kp.getPublic();
        RSAPrivateKey privKey = (RSAPrivateKey) kp.getPrivate();

        try {
            jdbc.update(
                    "INSERT INTO auth_key_pair (id, key_id, private_key_pem, public_key_pem) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID().toString(), keyId, pemEncode(privKey), pemEncode(pubKey));
            log.info("[JWT] New RSA key pair generated and persisted to database (kid={})", keyId);
        } catch (Exception e) {
            log.warn("[JWT] Could not persist RSA key to DB: {} — using ephemeral key (tokens invalid after restart!)", e.getMessage());
        }

        return new RSAKey.Builder(pubKey).privateKey(privKey).keyID(keyId).build();
    }

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }

    @Bean
    public TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(1))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)
                .build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * Seeds OAuth2 clients into DB on startup (idempotent — skips if clientId already exists).
     * Secrets are injected from environment variables via application.yml.
     * Redirect URI changes are handled via Flyway migrations, NOT by re-seeding.
     */
    @Bean
    @Order(5)
    public ApplicationRunner defaultClientRunner(RegisteredClientRepository clientRepo,
                                                 JdbcTemplate jdbcTemplate) {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

            seedClient(clientRepo, jdbcTemplate, RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("platform-admin-client")
                    .clientSecret(encoder.encode(platformAdminSecret))
                    .clientName("Platform Admin Portal")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:8090/login/oauth2/code/identity-platform")
                    .postLogoutRedirectUri("http://localhost:8090/")
                    .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope(OidcScopes.EMAIL)
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .tokenSettings(tokenSettings())
                    .build());

            seedClient(clientRepo, jdbcTemplate, RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("swagger-client")
                    .clientSecret(encoder.encode(swaggerSecret))
                    .clientName("Platform Swagger UI")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:8080/swagger-ui/oauth2-redirect.html")
                    .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope(OidcScopes.EMAIL)
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .tokenSettings(tokenSettings())
                    .build());

            seedClient(clientRepo, jdbcTemplate, RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("realestate-web-client")
                    .clientSecret(encoder.encode(realestateWebSecret))
                    .clientName("Real Estate Management — Web App")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:8084/login/oauth2/code/identity-platform")
                    .postLogoutRedirectUri("http://localhost:8084/")
                    .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope(OidcScopes.EMAIL)
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .tokenSettings(tokenSettings())
                    .build());

            seedClient(clientRepo, jdbcTemplate, RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("realestate-api-client")
                    .clientSecret(encoder.encode(realestateApiSecret))
                    .clientName("Real Estate Management — API Client")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("openid").scope("api.read").scope("api.write")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .tokenSettings(tokenSettings())
                    .build());

            // ── Kerb org: parking-admin app ───────────────────────────────────
            seedClient(clientRepo, jdbcTemplate, RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("parking-admin-web-client")
                    .clientSecret(encoder.encode(parkingAdminWebSecret))
                    .clientName("Kerb Parking Admin — Web App")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:8095/login/oauth2/code/identity-platform")
                    .postLogoutRedirectUri("http://localhost:8095/")
                    .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope(OidcScopes.EMAIL)
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .tokenSettings(tokenSettings())
                    .build());

            seedClient(clientRepo, jdbcTemplate, RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("parking-admin-api-client")
                    .clientSecret(encoder.encode(parkingAdminApiSecret))
                    .clientName("Kerb Parking Admin — API (M2M)")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("users:read").scope("users:write")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .tokenSettings(tokenSettings())
                    .build());

            // Public PKCE client — no secret
            seedClient(clientRepo, jdbcTemplate, RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("realestate-mobile-client")
                    .clientName("Real Estate Management — Mobile App")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:8081/callback")
                    .redirectUri("http://localhost:8082/callback")
                    .redirectUri("http://localhost:8082/callback.html")
                    .redirectUri("com.realestate.app://callback")
                    .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope(OidcScopes.EMAIL)
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(false)
                            .requireProofKey(true)
                            .build())
                    .tokenSettings(tokenSettings())
                    .build());
        };
    }

    private void seedClient(RegisteredClientRepository repo, JdbcTemplate jdbc, RegisteredClient client) {
        if (repo.findByClientId(client.getClientId()) == null) {
            repo.save(client);
            log.info("[Seed] OAuth2 client registered: {}", client.getClientId());
        }
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    /**
     * Registers the rate-limit filter at servlet level, before Spring Security (order -2147483638).
     * URL patterns: /login, /oauth2/token, /api/auth/token — POST only (checked inside the filter).
     */
    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter(StringRedisTemplate redisTemplate) {
        FilterRegistrationBean<LoginRateLimitFilter> bean =
                new FilterRegistrationBean<>(new LoginRateLimitFilter(redisTemplate));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.setUrlPatterns(List.of("/login", "/oauth2/token", "/api/auth/token"));
        return bean;
    }

    // ── CORS ──────────────────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                Arrays.stream(corsAllowedOrigins.split(","))
                      .map(String::strip)
                      .filter(s -> !s.isEmpty())
                      .toList());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/oauth2/token",    config);
        source.registerCorsConfiguration("/oauth2/jwks",     config);
        source.registerCorsConfiguration("/.well-known/**",  config);
        source.registerCorsConfiguration("/api/auth/**",     config);
        return source;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private JwtAuthenticationConverter rolesClaimConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var all = new ArrayList<org.springframework.security.core.GrantedAuthority>();
            var scopes = scopeConverter.convert(jwt);
            if (scopes != null) all.addAll(scopes);
            Optional.ofNullable(jwt.<List<String>>getClaim("roles"))
                    .orElse(List.of())
                    .stream()
                    .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                    .forEach(all::add);
            return all;
        });
        return converter;
    }

    private AuthenticationSuccessHandler orgAwareSuccessHandler(HttpSessionRequestCache requestCache) {
        return (request, response, authentication) -> {
            SavedRequestAwareAuthenticationSuccessHandler delegate =
                    new SavedRequestAwareAuthenticationSuccessHandler();
            delegate.setRequestCache(requestCache);
            delegate.setAlwaysUseDefaultTargetUrl(false);
            boolean isPlatformAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
            delegate.setDefaultTargetUrl(isPlatformAdmin ? "http://localhost:8090/" : "/");
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }

    private static RSAPrivateKey parseRsaPrivateKey(String pem) {
        try {
            String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA private key from DB", e);
        }
    }

    private static RSAPublicKey parseRsaPublicKey(String pem) {
        try {
            String b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                            .replace("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA public key from DB", e);
        }
    }

    private static String pemEncode(Key key) {
        String type = key instanceof PrivateKey ? "PRIVATE KEY" : "PUBLIC KEY";
        String b64  = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded());
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----";
    }
}
