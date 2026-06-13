# Identity Platform — Solution Implementation Guide
## v1.2.0 — Mobile Form Login + Production Hardening

> Self-hosted Identity Provider (IdP) cho B2B Multi-tenant Applications  
> Thay thế Zitadel / Keycloak / Auth0 — kiểm soát hoàn toàn

---

## 0. Kiến trúc tổng thể (2026)

```
Browser
  │
  ▼
platform-admin:8090  (Spring Cloud Gateway MVC — BFF)
  │  TokenRelay filter: inject Bearer token tự động
  │  AddRequestHeader: X-Organization-Slug = platform
  │  RequestRateLimiter: per-tenant via Redis
  │
  ├──► /api/v1/users/**, /organizations/**, /roles/**
  │       → admin-service:8081  (Spring Modulith, stateless JWT)
  │
  └──► /api/v1/registered-clients/**
          → auth-server:8080    (Spring Authorization Server, OAuth2/OIDC)

admin-service:8081  (Spring Modulith — bounded contexts)
  ├── adminservice module  (SecurityConfig, TenantFilter, AuditEventListener)
  ├── organization module  (OrganizationController, OrganizationService)
  │     publishes → OrgCreatedEvent, OrgSuspendedEvent
  └── usermanagement module  (UserController, RoleController, UserService)
        publishes → UserInvitedEvent, UserSuspendedEvent, UserActivatedEvent

auth-server:8080  (Pure OAuth2/OIDC)
  ├── Spring Authorization Server core
  ├── Login / Logout UI
  ├── MobileAuthController  (POST /api/auth/token — form login cho native apps)
  ├── RegisteredClientController  (OAuth2 clients)
  └── Reads users/orgs from shared DB for authentication

PostgreSQL (Shared DB)
  ├── Row Level Security (V11 migration)
  │     defense-in-depth: tenant isolation at DB layer
  ├── auth_key_pair (V14) — RSA JWT signing key persistence
  └── Flyway migrations chạy chỉ tại auth-server startup
```

### Technology decisions

| Concern | Solution | Lý do |
|---|---|---|
| BFF proxy | Spring Cloud Gateway MVC + TokenRelay | Zero boilerplate, token never in browser |
| Rate limiting (login) | `LoginRateLimitFilter` Redis-based | Brute force protection, fail-open nếu Redis down |
| Rate limiting (gateway) | RequestRateLimiter per tenant | Bảo vệ từng tenant độc lập |
| Module boundaries | Spring Modulith `@ApplicationModule` | Compile-time enforcement |
| Loose coupling | Domain Events → `@ApplicationModuleListener` | Swap sang Kafka không đổi code publish |
| Tenant isolation | RLS (DB) + TenantAwareDataSource + TenantContext | Defense-in-depth |
| Performance | Virtual Threads (Java 21) | I/O-bound workload, free throughput |
| JWT key persistence | `auth_key_pair` table (V14) | Tokens valid across restarts |
| Secrets management | Env vars → application.yml `${ENV:default}` | Deploy-time injection, no hardcode |

---

## 1. Tổng quan kiến trúc

### 1.1 Mục tiêu

Xây dựng một **Identity Provider (IdP) tự host** phục vụ nền tảng B2B multi-tenant, cho phép:

- Mỗi khách hàng (tenant) có không gian identity độc lập
- Tenant tự quản lý user, role, policy của họ
- Platform không phụ thuộc vào Zitadel, Keycloak, Okta hay bất kỳ third-party nào
- Dễ mở rộng tính năng theo nhu cầu riêng

### 1.2 Sơ đồ module

```
identity-platform/
├── core/                   # Shared kernel
│   ├── domain/             # BaseEntity (UUID PK, audit fields, soft-delete)
│   ├── tenant/             # TenantContext (ThreadLocal) + TenantAwareDataSource (RLS activation)
│   ├── exception/          # GlobalExceptionHandler, custom exceptions
│   └── audit/              # AuditLog entity, AuditService, AuditEventType
│
├── organization/           # Org bounded context (@ApplicationModule)
│   ├── domain/             # Organization, IdentityProviderConfig
│   ├── repository/         # OrganizationRepository, IdpConfigRepository
│   ├── service/            # OrganizationService — publishes OrgCreatedEvent, OrgSuspendedEvent
│   ├── controller/         # OrganizationController (REST API)
│   ├── events/             # OrgCreatedEvent, OrgSuspendedEvent
│   └── dto/                # OrganizationDto (CreateRequest, Response...)
│
├── user-management/        # Users bounded context (@ApplicationModule, depends: organization)
│   ├── domain/             # User, Role, UserStatus, AuthProvider
│   ├── repository/         # UserRepository, RoleRepository
│   ├── service/            # UserService — publishes UserInvited/Suspended/ActivatedEvent
│   │                       # RoleService, MfaService, InviteService, PasswordResetService
│   ├── controller/         # UserController, RoleController (REST API)
│   ├── events/             # UserInvitedEvent, UserSuspendedEvent, UserActivatedEvent
│   └── dto/                # UserDto
│
├── auth-server/            # OIDC/OAuth2 Authorization Server (port 8080)
│   ├── config/             # AuthorizationServerConfig, DataSeedingConfig, SecurityBeansConfig
│   │                       # DataSourceConfig (RLS)
│   ├── security/           # MultiTenantUserDetailsService, MultiTenantTokenCustomizer
│   │                       # OrgClientAuthorizationFilter, LoginRateLimitFilter (v1.2)
│   ├── filter/             # TenantResolutionFilter
│   ├── controller/         # AuthController, AuditController, RegisteredClientController
│   │                       # LoginController (incl. /error/org-access-denied)
│   │                       # MobileAuthController — POST /api/auth/token (v1.2)
│   └── resources/
│       ├── application.yml # virtual threads, env-var config (v1.2)
│       ├── templates/      # login.html, org-access-denied.html
│       └── db/migration/   # Flyway migrations V1→V14
│
└── admin-service/          # Admin API (port 8081) — Spring Modulith app
    ├── adminservice/
    │   ├── config/SecurityConfig     # Stateless JWT resource server
    │   ├── config/DataSourceConfig   # RLS activation
    │   ├── filter/TenantResolutionFilter
    │   └── audit/AuditEventListener
    └── resources/application.yml
```

### 1.3 Luồng request tổng quát

```
HTTP Request
    │
    ▼
LoginRateLimitFilter (servlet level)  ← Rate limit POST /login, /oauth2/token, /api/auth/token
    │  10 attempts / 60s per IP (Redis). Fail-open nếu Redis down.
    ▼
TenantResolutionFilter
    │  set TenantContext (ThreadLocal UUID)
    ▼
Spring Security Filter Chain
    │
    ▼
@Transactional Service
    │
    ▼
TenantAwareDataSource → SET app.tenant_id = '<uuid>'
    │
    ▼
PostgreSQL + RLS policies
```

---

## 2. Các quyết định thiết kế quan trọng

### 2.1 Multi-tenancy model: Shared Database, Tenant Column

Mỗi bảng có cột `organization_id`. Mọi query đều filter theo tenant hiện tại từ `TenantContext`.

### 2.2 Tenant Detection — 3 cơ chế theo thứ tự ưu tiên

```
Priority 1: Header         X-Organization-Slug: acme-corp
Priority 2: Subdomain      acme-corp.yourplatform.com
Priority 3: Path prefix    /api/v1/t/{acme-corp}/users
Fallback:   Email domain   user@acme.com → org với primaryDomain = acme.com
```

### 2.3 OIDC/OAuth2 với Spring Authorization Server

Spring Authorization Server 1.2.2 — implement chuẩn RFC 6749 + OpenID Connect 1.0.

```
GET  /.well-known/openid-configuration
GET  /oauth2/jwks
GET  /oauth2/authorize
POST /oauth2/token
POST /oauth2/revoke
POST /oauth2/introspect
GET  /userinfo
POST /api/auth/token   ← custom endpoint cho native mobile app (v1.2)
```

### 2.4 JWT Custom Claims

`MultiTenantTokenCustomizer` inject thêm vào mọi JWT:

```json
{
  "sub": "user-uuid",
  "email": "user@acme.com",
  "name": "Nguyen Van A",
  "org_id": "org-uuid",
  "roles": ["ORG_ADMIN"],
  "permissions": ["users:read", "users:write"],
  "iss": "https://auth.yourplatform.com",
  "exp": 1234567890
}
```

### 2.5 MFA — Tự implement TOTP (RFC 6238)

```
Secret (Base64) → HMAC-SHA1(secret, floor(time/30)) → 6-digit code
```

Tương thích Google Authenticator, Authy. Window ±1 time step để bù clock skew.

### 2.6 Password Reset — Redis Token Store

Token Redis TTL 1 giờ, single-use, anti-enumeration (204 dù email tồn tại hay không).

### 2.7 Audit Log — Async + REQUIRES_NEW

```java
@Async
@Transactional(propagation = REQUIRES_NEW)
public void log(AuditEventType eventType, ...) { ... }
```

### 2.8 RSA Key Persistence (v1.2)

Bảng `auth_key_pair` (V14 migration) lưu RSA key pair vào PostgreSQL. `jwkSource(JdbcTemplate)` load key khi startup:

```
Boot 1 (first): generate 2048-bit RSA → INSERT INTO auth_key_pair → build JWKSource
Boot 2+:        SELECT FROM auth_key_pair WHERE active=true → load → build JWKSource
```

Key `kid` ổn định → JWT `kid` header nhất quán → verification không bị broken sau restart.

Key rotation: `INSERT` row mới (active=true), `UPDATE` row cũ (active=false), redeploy.

---

## 3. Domain Model

### 3.1 Organization (Tenant)

```
Organization
├── id: UUID (PK)
├── name: String
├── slug: String (unique)
├── primaryDomain: String
├── status: PENDING_SETUP | ACTIVE | SUSPENDED | DEACTIVATED
├── mfaRequired: boolean
├── selfRegistrationAllowed: boolean
├── logoUrl, primaryColor: String
└── identityProviderConfig (1-1, optional)
```

### 3.2 User

```
User
├── id: UUID (PK)
├── organizationId: UUID (FK → Organization) ← TENANT ISOLATION
├── email: String (unique per org)
├── firstName, lastName: String
├── passwordHash: String (null nếu SSO)
├── status: PENDING_VERIFICATION | ACTIVE | SUSPENDED | LOCKED | DEACTIVATED
├── authProvider: LOCAL | GOOGLE | AZURE_AD | OKTA | SAML | LDAP
├── mfaEnabled: boolean, mfaSecret: String
├── lastLoginAt: Instant
└── roles: Set<Role> (M-N)
```

### 3.3 Role & Permission

```
Built-in system roles:
  PLATFORM_ADMIN  → quản trị toàn platform  (permissions: ["*"])
  ORG_ADMIN       → quản trị một org
  ORG_MEMBER      → user thường
```

### 3.4 RegisteredClientMetadata

Extension table không sửa `oauth2_registered_client`. FK `ON DELETE CASCADE`.

```
RegisteredClientMetadata
├── registeredClientId: String (UNIQUE FK → oauth2_registered_client.id)
├── organizationId: UUID (NOT NULL) ← mọi client phải thuộc 1 org
├── clientType: WEB_CLIENT | API_CLIENT | MOBILE_CLIENT | M2M_CLIENT
└── description: String
```

| clientId | type | org |
|---|---|---|
| `platform-admin-client` | WEB_CLIENT | platform |
| `swagger-client` | WEB_CLIENT | platform |
| `realestate-web-client` | WEB_CLIENT | realestate-corp |
| `realestate-api-client` | API_CLIENT | realestate-corp |
| `realestate-mobile-client` | MOBILE_CLIENT | realestate-corp |

---

## 4. Security Architecture

### 4.1 Security Filter Chains

```
Servlet Filter Chain (trước Spring Security):
  LoginRateLimitFilter [HIGHEST_PRECEDENCE+10]
    └── POST /login, /oauth2/token, /api/auth/token
        10 attempts / 60s per IP (Redis). Returns 429 JSON nếu vượt.
        Fail-open: Redis down → allow request (không block auth).

Chain 1 (Order 1): OAuth2/OIDC endpoints
  ├── SecurityContextHolderFilter
  ├── OrgClientAuthorizationFilter
  │     GET /oauth2/authorize: check user.orgId == client.orgId
  │     Nếu không match → redirect /error/org-access-denied
  └── OAuth2AuthorizationEndpointFilter

Chain 2 (Order 2): Application endpoints
  ├── permitAll: /login, /logout, /api/auth/**, /error/org-access-denied, ...
  └── anyRequest → authenticated (session hoặc JWT Bearer)
```

### 4.2 MobileAuthController — /api/auth/token (v1.2)

Endpoint cho first-party native mobile app (`mobile-client-sample-form`):

```
POST /api/auth/token
Content-Type: application/x-www-form-urlencoded

username=demo@realestate.local&password=demo123
```

Response:
```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile email"
}
```

Flow: `AuthenticationManager` → `MultiTenantUserDetailsService` → `JwtEncoder` (cùng RSA key). Custom claims giống PKCE flow (`org_id`, `roles`, `permissions`).

**Trade-off vs PKCE:**
- PRO: Không mở browser, native UX
- CON: App xử lý raw credentials (chỉ dùng cho first-party)
- CON: Tokens không tracked trong `oauth2_authorization` (không revoke được)
- CON: OAuth 2.1 không khuyến nghị password grant; PKCE là tiêu chuẩn mới

### 4.3 Tenant Isolation

```
Layer 1: TenantResolutionFilter
Layer 2: Repository queries (AND organization_id = ?)
Layer 3: @PreAuthorize
Layer 4: PostgreSQL RLS (V11)
```

---

## 5. Key API Endpoints

### 5.1 Organization Management

```
POST   /api/v1/organizations              Tạo tenant mới (PLATFORM_ADMIN)
GET    /api/v1/organizations/{id}
PUT    /api/v1/organizations/{id}
POST   /api/v1/organizations/{id}/activate
POST   /api/v1/organizations/{id}/suspend
GET    /api/v1/organizations/{id}/clients
```

### 5.2 User Management

```
POST   /api/v1/users/invite
GET    /api/v1/users
GET    /api/v1/users/{userId}
POST   /api/v1/users/{userId}/suspend

POST   /api/v1/users/password/reset-request
POST   /api/v1/users/password/reset
POST   /api/v1/users/password/change

POST   /api/v1/users/mfa/setup
POST   /api/v1/users/mfa/enable
POST   /api/v1/users/mfa/disable
```

### 5.3 Auth (v1.2)

```
POST   /api/auth/token    Form login cho native mobile app
                          Body: username=&password= (x-www-form-urlencoded)
                          Returns: {access_token, token_type, expires_in, scope}
```

### 5.4 Audit Logs

```
GET    /api/v1/audit-logs
GET    /api/v1/audit-logs/by-user/{id}
GET    /api/v1/audit-logs/by-event/{type}
GET    /api/v1/audit-logs/range?from=&to=
```

---

## 6. Database Schema

### 6.1 Thứ tự migration (Flyway — V1→V14)

```
V1__create_organizations.sql
V2__create_users.sql
V3__create_roles.sql
V4__create_identity_provider_configs.sql
V5__create_audit_logs.sql
V6__create_oauth2_authorization_tables.sql
V7__seed_default_client.sql                   ← placeholder
V8__create_registered_client_metadata.sql
V9__add_post_logout_redirect_uri.sql
V10__add_system_user_flag.sql
V11__add_row_level_security.sql               ← PostgreSQL RLS
V12__add_mobile_client_redirect_uris.sql      ← add localhost:8081/callback (v1.2)
V13__add_password_grant_to_mobile_client.sql  ← add 'password' grant type (v1.2)
V14__create_auth_key_pair_table.sql           ← RSA key persistence (v1.2)
```

> **Không dùng Liquibase.** Naming: `V{version}__{description}.sql`.

### 6.2 auth_key_pair table (V14)

```sql
CREATE TABLE auth_key_pair (
    id              VARCHAR(36)  PRIMARY KEY,
    key_id          VARCHAR(100) NOT NULL,
    algorithm       VARCHAR(20)  NOT NULL DEFAULT 'RSA',
    private_key_pem TEXT         NOT NULL,
    public_key_pem  TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);
```

---

## 7. Cách chạy local

### 7.1 Yêu cầu

- Java 21+, Maven 3.8+, Node.js 18+
- PostgreSQL port 5433, db=`identity_platform`
- Redis trên localhost:6379

### 7.2 Environment variables (tùy chọn — có defaults cho dev)

```bash
# Bắt buộc set trên production, optional trên local (có defaults)
export AUTH_SERVER_ISSUER=http://localhost:8080
export ADMIN_PASSWORD=admin
export DEMO_PASSWORD=demo123
export PLATFORM_ADMIN_CLIENT_SECRET=platform-admin-secret
export REALESTATE_WEB_CLIENT_SECRET=realestate-web-secret
export REALESTATE_API_CLIENT_SECRET=realestate-api-secret
export CORS_ALLOWED_ORIGINS=http://localhost:8081,http://localhost:8082,http://localhost:8083,http://localhost:8090
```

### 7.3 Chạy Identity Platform (port 8080)

```bash
cd identity-platform
mvn -pl user-management -am install -DskipTests
cd auth-server && mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8080/.well-known/openid-configuration
```

### 7.4 Chạy Web App (port 8081)

```bash
cd realestate-app && mvn spring-boot:run
# → http://localhost:8081
```

### 7.5 Chạy API Client Sample (port 8083)

```bash
cd api-client-sample && mvn spring-boot:run
# GET http://localhost:8083/demo/token      — M2M token
# GET http://localhost:8083/demo/audit-logs — gọi IdP bằng M2M token
# GET http://localhost:8083/api/me          — protected (cần Bearer token)
```

### 7.6 Chạy Mobile PKCE Sample (port 8082)

```bash
cd mobile-client-sample && npx serve . -p 8082
# → http://localhost:8082
```

### 7.7 Chạy Mobile Form Login Sample (port 8081 web) — v1.2

```bash
cd mobile-client-sample-form
npm install
npx expo start --web
# → http://localhost:8081

# Android emulator
npx expo start --android

# Demo account: demo@realestate.local / demo123
```

---

## 8. Onboard Tenant mới

```bash
# 1. Tạo org
curl -X POST http://localhost:8080/api/v1/organizations \
  -H "Authorization: Bearer {TOKEN}" \
  -d '{"name":"Acme Corp","slug":"acme-corp","primaryDomain":"acme.com"}'

# 2. Kích hoạt
curl -X POST http://localhost:8080/api/v1/organizations/{orgId}/activate \
  -H "Authorization: Bearer {TOKEN}"

# 3. Invite admin
curl -X POST http://localhost:8080/api/v1/users/invite \
  -H "Authorization: Bearer {TOKEN}" -H "X-Organization-Slug: acme-corp" \
  -d '{"email":"admin@acme.com","firstName":"Admin","lastName":"User"}'
```

---

## 9. Tích hợp vào Platform chính

```yaml
# Resource server
spring.security.oauth2.resourceserver.jwt.issuer-uri: https://auth.yourplatform.com
```

```java
// Extract tenant từ JWT
String orgId = auth.getToken().getClaimAsString("org_id");
return service.findByOrganization(UUID.fromString(orgId));
```

---

## 10. Production Checklist

### 10.1 Security

- [x] **RSA key persistence** — V14 migration + `jwkSource(JdbcTemplate)` (v1.2)
- [x] **Externalize client secrets** — env vars: `PLATFORM_ADMIN_CLIENT_SECRET`, etc. (v1.2)
- [x] **Configurable issuer URL** — `AUTH_SERVER_ISSUER` env var (v1.2)
- [x] **Rate limiting on auth endpoints** — `LoginRateLimitFilter` Redis 10/60s (v1.2)
- [x] **CORS origins configurable** — `CORS_ALLOWED_ORIGINS` env var (v1.2)
- [x] Registered client repository lưu DB (`JdbcRegisteredClientRepository`)
- [ ] Encrypt `mfaSecret` trong DB (`@Convert` + AES-256)
- [ ] Encrypt `clientSecret` của IdP config
- [ ] Enable HTTPS (TLS termination tại load balancer)
- [ ] `Secure`, `HttpOnly`, `SameSite=Strict` cho session cookie
- [ ] Rate limiting per tenant (Bucket4j trên password reset endpoint)

### 10.2 High Availability

```
Load Balancer → auth-server #1 + auth-server #2 → PostgreSQL Primary + Read Replica
                                                 → Redis Cluster (Session + Rate limit)
```

- [ ] PostgreSQL Primary + Read Replica
- [ ] Redis Sentinel hoặc Redis Cluster
- [ ] Ít nhất 2 instance auth-server (Spring Session Redis → stateless)
- [ ] Health check: `GET /actuator/health`

### 10.3 Observability

- [ ] Micrometer + Prometheus
- [ ] Log aggregation (ELK / Loki)
- [ ] Alert khi login failure rate tăng bất thường
- [ ] Monitor Redis memory

---

## 11. Roadmap mở rộng

| Tính năng | Độ phức tạp | Ghi chú |
|---|---|---|
| Custom login page per tenant | Thấp | Thymeleaf resolve theo `slug` |
| FIDO2 / WebAuthn (Passkey) | Cao | Thay thế TOTP |
| SCIM 2.0 user sync | Trung bình | Đồng bộ user từ HR system |
| Webhook on user events | Trung bình | Callback URL khi login/create |
| Admin dashboard UI | Cao | React SPA |
| LDAP connector | Trung bình | Spring LDAP, on-premise AD |
| Session management UI | Thấp | Xem/revoke active sessions |
| MobileAuthController token revocation | Trung bình | Track trong `oauth2_authorization` |

---

## 12. Application Ecosystem

### 12.1 Tổng quan

```
multi-tenant/
├── identity-platform/              ← IdP (port 8080)
├── realestate-app/                 ← WEB_CLIENT  (port 8081) — browser, user session
├── api-client-sample/              ← API_CLIENT  (port 8083) — M2M, không cần user
├── mobile-client-sample/           ← MOBILE_CLIENT — PKCE (tiêu chuẩn, no secret)
└── mobile-client-sample-form/      ← MOBILE_CLIENT — Form login (native, first-party)
```

### 12.2 So sánh các loại client

| | `realestate-app` | `api-client-sample` | `mobile-client-sample` | `mobile-client-sample-form` |
|---|---|---|---|---|
| **Client type** | WEB_CLIENT | API_CLIENT | MOBILE_CLIENT | MOBILE_CLIENT |
| **Grant type** | `authorization_code` | `client_credentials` | `authorization_code` + PKCE | custom `/api/auth/token` |
| **Client secret** | ✅ có | ✅ có | ❌ không | ❌ không |
| **User login** | ✅ browser | ❌ | ✅ browser popup | ✅ native form |
| **PKCE** | Optional | N/A | ✅ bắt buộc | N/A |
| **Dùng khi nào** | Web SPA/SSR | Service-to-service | Public mobile app | First-party internal tool |

### 12.3 Organization ↔ Client mapping

```
Organization: realestate-corp
    ├── realestate-web-client    (WEB_CLIENT)
    ├── realestate-api-client    (API_CLIENT)
    └── realestate-mobile-client (MOBILE_CLIENT — dùng cho cả 2 mobile samples)

Organization: platform
    ├── platform-admin-client
    └── swagger-client
```

### 12.4 mobile-client-sample vs mobile-client-sample-form

**mobile-client-sample** (PKCE — tiêu chuẩn):
- Dùng `expo-auth-session` + `WebBrowser`
- Redirect sang `/oauth2/authorize` → login qua browser popup
- Nhận authorization code → exchange token
- ✅ Phù hợp: app public, SSO multi-org, OAuth2.1 compliant

**mobile-client-sample-form** (Form login — first-party):
- Native `TextInput` email/password form, không mở browser
- Gọi trực tiếp `POST /api/auth/token` (MobileAuthController)
- ✅ Phù hợp: app nội bộ công ty, single org, UX liền mạch

---

*Identity Platform v1.2.0 — Self-hosted IdP for B2B Multi-tenant*  
*Changelog: Mobile Form Login (MobileAuthController), Production Hardening (5 production fixes), V12–V14 migrations*
