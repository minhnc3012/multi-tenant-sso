# Identity Platform — auth-server Implementation Guide
## v1.2.0 — Mobile Form Login + Production Hardening

> Self-hosted Identity Provider (IdP) cho B2B Multi-tenant Applications

---

## 1. Tổng quan kiến trúc

### 1.1 Mục tiêu

Xây dựng **Identity Provider (IdP) tự host** phục vụ nền tảng B2B multi-tenant. Đây là **production foundation** — không phải demo.

### 1.2 Sơ đồ module

```
identity-platform/
├── core/                   # Shared kernel
│   ├── domain/             # BaseEntity (UUID PK, audit fields, soft-delete)
│   ├── tenant/             # TenantContext (ThreadLocal) + TenantAwareDataSource (RLS)
│   ├── exception/          # GlobalExceptionHandler, custom exceptions
│   └── audit/              # AuditLog entity, AuditService, AuditEventType
│
├── organization/
│   ├── domain/             # Organization, IdentityProviderConfig
│   ├── repository/
│   ├── service/            # OrganizationService
│   ├── controller/
│   └── dto/
│
├── user-management/
│   ├── domain/             # User, Role, UserStatus, AuthProvider
│   ├── repository/
│   ├── service/            # UserService, RoleService, MfaService, InviteService, PasswordResetService
│   ├── controller/
│   └── dto/
│
└── auth-server/            # OIDC/OAuth2 Authorization Server (port 8080)
    ├── config/
    │   ├── AuthorizationServerConfig   # Filter chains, JWK, CORS, rate limit registration
    │   ├── SecurityBeansConfig         # AuthenticationManager bean
    │   ├── DataSeedingConfig           # Seed orgs, users, client metadata
    │   └── DataSourceConfig            # RLS activation
    ├── security/
    │   ├── MultiTenantUserDetailsService  # Detect tenant từ email domain
    │   ├── MultiTenantTokenCustomizer     # Inject org_id, roles, permissions vào JWT
    │   ├── OrgClientAuthorizationFilter   # Enforce org↔client access
    │   ├── LoginRateLimitFilter           # Redis rate limit (v1.2)
    │   └── UserPrincipal
    ├── filter/
    │   └── TenantResolutionFilter
    ├── controller/
    │   ├── MobileAuthController    # POST /api/auth/token (v1.2)
    │   ├── AuthController
    │   ├── AuditController
    │   ├── RegisteredClientController
    │   └── LoginController         # /error/org-access-denied
    └── resources/
        ├── application.yml         # env-var config (v1.2)
        ├── templates/
        └── db/migration/           # V1→V14
```

### 1.3 Luồng request

```
POST /api/auth/token (form login — mobile-client-sample-form)
    │
    ▼
LoginRateLimitFilter         ← 10 attempts / 60s per IP
    │
    ▼
MobileAuthController
    ├── AuthenticationManager.authenticate(username, password)
    │       └── MultiTenantUserDetailsService → PostgreSQL
    ├── UserService.findClaimsById(userId)
    └── JwtEncoder.encode(JwtClaimsSet) ← cùng RSA key với /oauth2/token
    → return {access_token, token_type, expires_in, scope}

GET /oauth2/authorize (PKCE — mobile-client-sample)
    │
    ▼
OrgClientAuthorizationFilter ← check user.orgId == client.orgId
    │
    ▼
OAuth2AuthorizationEndpointFilter → issue authorization code
    │
    ▼ (client exchanges code)
POST /oauth2/token
    │
    ▼
MultiTenantTokenCustomizer → inject org_id, roles, permissions
```

---

## 2. Các quyết định thiết kế

### 2.1 RSA Key Persistence (v1.2)

**Vấn đề:** Key generate random mỗi lần restart → tất cả JWT hiện tại bị invalid.

**Giải pháp:** Lưu key vào bảng `auth_key_pair` (V14 migration):

```java
@Bean
public JWKSource<SecurityContext> jwkSource(JdbcTemplate jdbcTemplate) {
    // 1. SELECT FROM auth_key_pair WHERE active=true → load
    // 2. Nếu chưa có → generate + INSERT INTO auth_key_pair → dùng
    RSAKey rsaKey = loadOrPersistRsaKey(jdbcTemplate);
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
}
```

Key rotation: INSERT row mới (active=true), UPDATE row cũ (active=false), redeploy.

Fail-safe: nếu DB unreachable trong `loadOrPersistRsaKey` → generate ephemeral + log warning (server không crash).

### 2.2 Externalize Secrets (v1.2)

Tất cả secrets đọc từ env vars qua `application.yml`:

```yaml
app:
  auth:
    issuer: ${AUTH_SERVER_ISSUER:http://localhost:8080}
  oauth2:
    secrets:
      platform-admin: ${PLATFORM_ADMIN_CLIENT_SECRET:platform-admin-secret}
      swagger:        ${SWAGGER_CLIENT_SECRET:swagger-secret}
      realestate-web: ${REALESTATE_WEB_CLIENT_SECRET:realestate-web-secret}
      realestate-api: ${REALESTATE_API_CLIENT_SECRET:realestate-api-secret}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:8081,...}
  seed:
    admin-password: ${ADMIN_PASSWORD:admin}
    demo-password:  ${DEMO_PASSWORD:demo123}
```

`@Value("${app.oauth2.secrets.platform-admin}")` inject vào `AuthorizationServerConfig`.  
`@Value("${app.seed.admin-password}")` inject vào `DataSeedingConfig`.

**Quan trọng:** Seed users/clients là **insert-only-if-not-exists**. Thay đổi password/secret sau initial deploy → cần DB UPDATE trực tiếp hoặc API.

### 2.3 Rate Limiting (v1.2)

`LoginRateLimitFilter` — servlet-level, đăng ký qua `FilterRegistrationBean`:

```java
@Bean
public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter(StringRedisTemplate rt) {
    var bean = new FilterRegistrationBean<>(new LoginRateLimitFilter(rt));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);  // trước Spring Security
    bean.setUrlPatterns(List.of("/login", "/oauth2/token", "/api/auth/token"));
    return bean;
}
```

Giới hạn: 10 POST attempts / 60s per IP. Response khi vượt:
```json
HTTP 429
{"error":"too_many_requests","error_description":"Too many login attempts. Try again in 1 minute."}
```

Fail-open: Redis down → allow request (Redis outage không block auth). IP detection: `X-Forwarded-For` → `X-Real-IP` → `remoteAddr`.

### 2.4 MobileAuthController (v1.2)

Tại sao không dùng OAuth2 password grant?

Spring Authorization Server 1.2.2 internal APIs (`OAuth2AuthorizationGrantAuthenticationToken`, `DefaultOAuth2TokenContext.Builder`, `OAuth2TokenEndpointConfigurer.authenticationConverter()`) gây cascading compile errors. Password grant bị deprecated trong OAuth 2.1 (RFC 9700).

Giải pháp: REST controller thuần dùng `AuthenticationManager` + `JwtEncoder` (standard Spring Security, không dùng Spring AS internals):

```java
@PostMapping(value = "/token", consumes = APPLICATION_FORM_URLENCODED_VALUE)
public ResponseEntity<?> passwordLogin(@RequestParam String username, @RequestParam String password) {
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(username, password));
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    // build JwtClaimsSet với org_id, roles, permissions
    Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet));
    return ResponseEntity.ok(Map.of("access_token", jwt.getTokenValue(), ...));
}
```

**Limitation:** Tokens không tracked trong `oauth2_authorization` table → không revoke/introspect được.

### 2.5 OrgClientAuthorizationFilter (v1.1)

`OncePerRequestFilter` chạy trên `GET /oauth2/authorize`. Check `user.organizationId == client.organizationId` (qua `RegisteredClientMetadata`). Nếu không match → `sendRedirect("/error/org-access-denied")`.

Anchor `addFilterAfter(SecurityContextHolderFilter.class)` vì `OAuth2AuthorizationEndpointFilter` không có registered order.

### 2.6 Multi-tenancy

```
Layer 1: TenantResolutionFilter    — Header > Subdomain > Path > Email domain
Layer 2: Repository (AND org_id)
Layer 3: @PreAuthorize
Layer 4: PostgreSQL RLS (V11)
```

`TenantContext` bắt buộc clear trong `finally` block.

### 2.7 Seeding order

```
@Order(5)  defaultClientRunner    — seed OAuth2 clients (insert-only-if-not-exists)
@Order(20) seedDefaultData        — seed orgs, roles, admin + demo users
@Order(30) seedClientMetadata     — link clients → orgs
```

**Tại sao không delete+reinsert client?** `registered_client_metadata` có FK `ON DELETE CASCADE` → xóa client sẽ xóa metadata. Dùng insert-only + Flyway migration cho redirect URI changes.

---

## 3. Domain Model

### 3.1 RegisteredClientMetadata

```
RegisteredClientMetadata
├── registeredClientId: String (UNIQUE FK ON DELETE CASCADE)
├── organizationId: UUID (NOT NULL)
├── clientType: WEB_CLIENT | API_CLIENT | MOBILE_CLIENT | M2M_CLIENT
└── description: String
```

Mọi client PHẢI có entry. `OrgClientAuthorizationFilter` log ERROR + deny nếu thiếu.

---

## 4. Security Architecture

### 4.1 Filter Chains

```
Servlet Chain (trước Spring Security):
  LoginRateLimitFilter [HIGHEST_PRECEDENCE+10]

Spring Security Chain 1 (Order 1): /oauth2/**, /.well-known/**
  SecurityContextHolderFilter
  → OrgClientAuthorizationFilter (GET /oauth2/authorize only)
  → OAuth2AuthorizationEndpointFilter

Spring Security Chain 2 (Order 2): everything else
  permitAll: /login, /logout, /api/auth/**, /error/**, /invite/**, /actuator/health, /swagger-ui/**, /v3/api-docs/**
  anyRequest: authenticated (session OR JWT Bearer)
  CSRF disabled for /api/**
```

### 4.2 CORS

`CorsConfigurationSource` bean — config cho `/oauth2/token`, `/oauth2/jwks`, `/.well-known/**`, `/api/auth/**`. Allowed origins từ `CORS_ALLOWED_ORIGINS` env var.

### 4.3 JWT

- Signing: RSA-256, 2048-bit key từ `auth_key_pair` table
- Decoder: `OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)`
- Encoder: `NimbusJwtEncoder(jwkSource)` — explicit `@Bean` (Spring AS không auto-expose)
- Custom claims: `MultiTenantTokenCustomizer` (PKCE flow) + `MobileAuthController` (form flow)

---

## 5. Database Schema

### 5.1 Flyway Migrations (V1→V14)

```
V1  create_organizations
V2  create_users
V3  create_roles
V4  create_identity_provider_configs
V5  create_audit_logs
V6  create_oauth2_authorization_tables
V7  seed_default_client (placeholder)
V8  create_registered_client_metadata
V9  add_post_logout_redirect_uri
V10 add_system_user_flag
V11 add_row_level_security          ← PostgreSQL RLS
V12 add_mobile_client_redirect_uris ← add localhost:8081/callback (v1.2)
V13 add_password_grant_to_mobile_client (v1.2)
V14 create_auth_key_pair_table      ← RSA key persistence (v1.2)
```

### 5.2 auth_key_pair (V14)

```sql
CREATE TABLE auth_key_pair (
    id              VARCHAR(36)  PRIMARY KEY,
    key_id          VARCHAR(100) NOT NULL,
    algorithm       VARCHAR(20)  NOT NULL DEFAULT 'RSA',
    private_key_pem TEXT         NOT NULL,   -- PKCS#8 PEM
    public_key_pem  TEXT         NOT NULL,   -- X.509 PEM
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);
```

---

## 6. Cách chạy local

### 6.1 Build & Run

```bash
cd identity-platform
mvn -pl user-management -am install -DskipTests
cd auth-server
mvn spring-boot:run
```

### 6.2 Verify

```bash
curl http://localhost:8080/.well-known/openid-configuration
# Kiểm tra "issuer" == "http://localhost:8080"

curl http://localhost:8080/oauth2/jwks
# Kiểm tra có "keys" array với kid stable (không thay đổi sau restart)
```

### 6.3 Test form login

```bash
curl -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=demo@realestate.local&password=demo123"
# → {"access_token":"eyJ...","token_type":"Bearer","expires_in":3600,"scope":"..."}
```

---

## 7. Production Deployment

### 7.1 Environment Variables

```bash
# Bắt buộc
AUTH_SERVER_ISSUER=https://auth.yourcompany.com
ADMIN_PASSWORD=<strong-random>
PLATFORM_ADMIN_CLIENT_SECRET=<random-32-chars>
SWAGGER_CLIENT_SECRET=<random-32-chars>
REALESTATE_WEB_CLIENT_SECRET=<random-32-chars>
REALESTATE_API_CLIENT_SECRET=<random-32-chars>
DEMO_PASSWORD=<random>

# Infrastructure
DB_USERNAME=... DB_PASSWORD=...
REDIS_HOST=... REDIS_PORT=6379 REDIS_PASSWORD=...

# CORS — comma-separated
CORS_ALLOWED_ORIGINS=https://app.yourcompany.com,https://admin.yourcompany.com
```

### 7.2 Production Checklist

- [x] RSA key persistence (V14 + `auth_key_pair` table)
- [x] Externalize all secrets (env vars)
- [x] Configurable issuer URL
- [x] Rate limiting on auth endpoints (Redis 10/60s)
- [x] CORS origins configurable
- [x] DB-backed OAuth2 client repository
- [ ] HTTPS (TLS termination)
- [ ] `Secure`, `HttpOnly`, `SameSite=Strict` cookies
- [ ] Encrypt `mfaSecret` (`@Convert` + AES-256)
- [ ] PostgreSQL Primary + Read Replica
- [ ] Redis Sentinel / Cluster
- [ ] 2+ auth-server instances (stateless via Spring Session Redis)

---

## 8. Known Limitations

| Limitation | Impact | Resolution |
|---|---|---|
| `MobileAuthController` tokens not tracked | No revocation/introspection for form-login tokens | Track in `oauth2_authorization` table (future) |
| Seed passwords insert-only | Changing `ADMIN_PASSWORD` after initial deploy has no effect | Update via `POST /api/v1/users/password/change` |
| Rate limit resets on Redis restart | Brief window of no rate limiting | Redis persistence (`appendonly yes`) |
| Single active RSA key | All tokens use same key | Multi-key JWKS (future: rotate without invalidating existing tokens) |

---

*Identity Platform v1.2.0 — auth-server*  
*Changelog: MobileAuthController (/api/auth/token), LoginRateLimitFilter, RSA key persistence (V14), externalized secrets, configurable issuer + CORS*
