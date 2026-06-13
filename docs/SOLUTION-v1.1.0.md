# Identity Platform — Solution Implementation Guide

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
  ├── RegisteredClientController  (OAuth2 clients)
  └── Reads users/orgs from shared DB for authentication

PostgreSQL (Shared DB)
  ├── Row Level Security (V11 migration)
  │     defense-in-depth: tenant isolation at DB layer
  └── Flyway migrations chạy chỉ tại auth-server startup
```

### Technology decisions

| Concern | Solution | Lý do |
|---|---|---|
| BFF proxy | Spring Cloud Gateway MVC + TokenRelay | Zero boilerplate, token never in browser |
| Rate limiting | RequestRateLimiter (Redis-backed) per tenant | Bảo vệ từng tenant độc lập |
| Module boundaries | Spring Modulith `@ApplicationModule` | Compile-time enforcement, test từng module |
| Loose coupling | Domain Events → `@ApplicationModuleListener` | Swap sang Kafka không đổi code publish |
| Tenant isolation | RLS (DB) + TenantAwareDataSource + TenantContext | Defense-in-depth: DB enforces isolation even if app layer has a bug |
| Performance | Virtual Threads (Java 21) | I/O-bound workload, free throughput |
| Extract path | Modulith → Microservice | Đổi route URI trong YAML, zero code change |

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
│   ├── config/             # AuthorizationServerConfig, DataSeedingConfig, DataSourceConfig (RLS)
│   ├── security/           # MultiTenantUserDetailsService, MultiTenantTokenCustomizer
│   │                       # OrgClientAuthorizationFilter
│   ├── filter/             # TenantResolutionFilter
│   ├── controller/         # AuthController, AuditController, RegisteredClientController
│   │                       # LoginController (incl. /error/org-access-denied)
│   └── resources/
│       ├── application.yml # virtual threads enabled
│       ├── templates/      # login.html, org-access-denied.html
│       └── db/migration/   # Flyway migrations V1→V11
│
└── admin-service/          # Admin API (port 8081) — Spring Modulith app
    ├── AdminServiceApplication.java  # in com.identityplatform (Modulith root)
    ├── adminservice/
    │   ├── config/SecurityConfig     # Stateless JWT resource server
    │   ├── config/DataSourceConfig   # BeanPostProcessor wraps DataSource → RLS activation
    │   ├── filter/TenantResolutionFilter
    │   └── audit/AuditEventListener  # @ApplicationModuleListener for all domain events
    └── resources/
        └── application.yml  # virtual threads, flyway.enabled=false

platform-admin/             # BFF Portal (port 8090) — Spring Cloud Gateway MVC
├── config/
│   ├── SecurityConfig      # OAuth2 OIDC login, ROLE_PLATFORM_ADMIN required
│   └── GatewayConfig       # TenantKeyResolver, OAuth2AuthorizedClientManager
├── controller/PageController  # Thymeleaf pages (/admin/users, /admin/orgs...)
└── resources/application.yml  # gateway routes, TokenRelay, RateLimiter
```

### 1.3 Luồng request tổng quát

```
HTTP Request
    │
    ▼
TenantResolutionFilter          ← Detect tenant từ subdomain/header/path
    │  set TenantContext (ThreadLocal UUID)
    ▼
Spring Security Filter Chain    ← Authenticate / authorize
    │
    ▼
@Transactional Service          ← Business logic
    │
    ▼
TenantAwareDataSource           ← getConnection() → SET app.tenant_id = '<uuid>'
    │  SET app.is_platform_admin = 'true/false'     (reads TenantContext + SecurityContext)
    ▼
PostgreSQL + RLS policies       ← organization_id::text = current_setting('app.tenant_id')
    │  Defense-in-depth: DB enforces isolation independent of application code
    ▼
Result scoped to tenant
```

**Hai lớp bảo vệ tenant isolation:**
- **Lớp 1 (App)**: `TenantContext` ThreadLocal → JPA repositories filter by `organization_id`
- **Lớp 2 (DB)**: `TenantAwareDataSource` → PostgreSQL RLS via `current_setting('app.tenant_id')`

Nếu app layer có bug (quên filter), DB layer vẫn chặn dữ liệu của tenant khác.

---

## 2. Các quyết định thiết kế quan trọng

### 2.1 Multi-tenancy model: Shared Database, Tenant Column

Mỗi bảng có cột `organization_id`. Mọi query đều filter theo tenant hiện tại từ `TenantContext`.

**Tại sao không dùng schema-per-tenant?**

| | Shared DB + Column | Schema per tenant |
|---|---|---|
| Số lượng tenant lớn | ✅ Không giới hạn | ❌ Giới hạn số schema/DB connection |
| Migration | ✅ Một lần cho tất cả | ❌ Phải chạy cho từng tenant |
| Isolation | ✅ Logic (application-level) | ✅ Physical |
| Chi phí vận hành | ✅ Thấp | ❌ Cao |

> **Quan trọng**: Luôn filter `AND organization_id = ?` trong mọi query. Đây là hàng rào bảo mật chính.

### 2.2 Tenant Detection — 3 cơ chế theo thứ tự ưu tiên

```
Priority 1: Header         X-Organization-Slug: acme-corp
Priority 2: Subdomain      acme-corp.yourplatform.com
Priority 3: Path prefix    /api/v1/t/{acme-corp}/users
Fallback:   Email domain   user@acme.com → org với primaryDomain = acme.com
```

`TenantResolutionFilter` chạy trước mọi thứ, set `TenantContext` (ThreadLocal), và **bắt buộc clear** trong `finally` block để tránh leak sang request khác trong thread pool.

### 2.3 OIDC/OAuth2 với Spring Authorization Server

Sử dụng **Spring Authorization Server 1.2.x** — implement chuẩn RFC 6749 (OAuth2) + OpenID Connect 1.0.

Platform tự động expose các endpoint chuẩn:

```
GET  /.well-known/openid-configuration   # Discovery document
GET  /oauth2/jwks                        # Public keys
GET  /oauth2/authorize                   # Authorization code flow
POST /oauth2/token                       # Exchange code → token
POST /oauth2/revoke                      # Revoke token
POST /oauth2/introspect                  # Token introspection
GET  /userinfo                           # User profile
```

Mọi client library OIDC (Java, Node.js, Python...) đều tương thích ngay mà không cần cấu hình đặc biệt.

### 2.4 JWT Custom Claims — Tenant context trong token

`MultiTenantTokenCustomizer` inject thêm vào mọi JWT:

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@acme.com",
  "name": "Nguyen Van A",
  "org_id": "org-uuid-here",
  "roles": ["ORG_ADMIN"],
  "permissions": ["users:read", "users:write"],
  "iss": "https://auth.yourplatform.com",
  "exp": 1234567890
}
```

Resource server của platform chỉ cần đọc `org_id` từ JWT để biết đang phục vụ tenant nào — không cần DB roundtrip.

### 2.5 MFA — Tự implement TOTP (RFC 6238)

Không dùng thư viện ngoài, implement trực tiếp HMAC-SHA1 + dynamic truncation:

```
Secret (Base64) → HMAC-SHA1(secret, floor(time/30)) → 6-digit code
```

Tương thích Google Authenticator, Authy, Microsoft Authenticator. Window ±1 time step (30s) để bù clock skew giữa server và device.

### 2.6 Password Reset — Redis Token Store

Token reset mật khẩu được lưu vào **Redis với TTL 1 giờ**, không lưu DB:

- Không làm bloat bảng users
- TTL tự động expire, không cần cleanup job
- Single-use: xóa ngay sau khi dùng
- Anti-enumeration: luôn trả `204 No Content` dù email có tồn tại hay không

### 2.7 Audit Log — Async + REQUIRES_NEW

```java
@Async                              // Không block request chính
@Transactional(propagation = REQUIRES_NEW)  // Luôn ghi kể cả khi tx chính rollback
public void log(AuditEventType eventType, ...) { ... }
```

Đặc biệt quan trọng với login failure: transaction chính fail (sai password) nhưng audit log vẫn phải được ghi.

---

## 3. Domain Model

### 3.1 Organization (Tenant)

```
Organization
├── id: UUID (PK)
├── name: String
├── slug: String (unique) ← dùng làm subdomain
├── primaryDomain: String ← auto-detect tenant từ email
├── status: PENDING_SETUP | ACTIVE | SUSPENDED | DEACTIVATED
├── mfaRequired: boolean ← enforce MFA cho toàn org
├── selfRegistrationAllowed: boolean
├── logoUrl: String ← branding
├── primaryColor: String ← branding
└── identityProviderConfig: IdentityProviderConfig (1-1, optional)
```

### 3.2 User

```
User
├── id: UUID (PK)
├── organizationId: UUID (FK → Organization) ← TENANT ISOLATION
├── email: String (unique per org)
├── firstName, lastName: String
├── passwordHash: String (null nếu dùng SSO)
├── status: PENDING_VERIFICATION | ACTIVE | SUSPENDED | LOCKED | DEACTIVATED
├── authProvider: LOCAL | GOOGLE | AZURE_AD | OKTA | SAML | LDAP
├── externalSubjectId: String ← link với SSO account
├── mfaEnabled: boolean
├── mfaSecret: String (Base64, encrypted nên dùng @Convert)
├── lastLoginAt: Instant
├── inviteToken: String (tạm, xóa sau khi accept)
└── roles: Set<Role> (M-N)
```

### 3.3 Role & Permission

```
Role
├── id: UUID
├── organizationId: UUID (null = system role)
├── name: String (unique per org)
├── systemRole: boolean ← không thể sửa/xóa
└── permissions: Set<String> ← flat permission strings

Built-in system roles:
  PLATFORM_ADMIN  → quản trị toàn platform
  ORG_ADMIN       → quản trị một organization
  ORG_MEMBER      → user thường

Custom org roles (ví dụ):
  BILLING_MANAGER → permissions: ["billing:read", "billing:write"]
  VIEWER          → permissions: ["*:read"]
```

### 3.4 RegisteredClientMetadata

Extension table — không sửa `oauth2_registered_client` (Spring Authorization Server owns that schema).
Link qua `registered_client_id` FK với `ON DELETE CASCADE`.

```
RegisteredClientMetadata
├── id: UUID (PK)
├── registeredClientId: String (UNIQUE FK → oauth2_registered_client.id)
├── organizationId: UUID (NOT NULL FK → organizations) ← mọi client phải thuộc 1 org
├── clientType: WEB_CLIENT | API_CLIENT | MOBILE_CLIENT | M2M_CLIENT
└── description: String

Client types:
  WEB_CLIENT    → authorization_code + client_secret (browser app)
  API_CLIENT    → client_credentials (server-to-server, M2M)
  MOBILE_CLIENT → authorization_code + PKCE, no secret (native/SPA)
  M2M_CLIENT    → client_credentials (daemon, background job)
```

Seeded clients (DataSeedingConfig `@Order(20)` → orgs + users, `@Order(30)` → client metadata):

| clientId | type | org |
|---|---|---|
| `platform-admin-client` | WEB_CLIENT | platform |
| `swagger-client` | WEB_CLIENT | platform |
| `realestate-web-client` | WEB_CLIENT | realestate-corp |
| `realestate-api-client` | API_CLIENT | realestate-corp |
| `realestate-mobile-client` | MOBILE_CLIENT | realestate-corp |

> **Quy tắc:** Mọi client PHẢI có entry trong `registered_client_metadata` với `organization_id` NOT NULL.
> `OrgClientAuthorizationFilter` sẽ deny + log ERROR nếu thiếu. Khi thêm client mới → nhớ seed metadata.

### 3.5 IdentityProviderConfig

```
IdentityProviderConfig (1-1 với Organization)
├── type: OIDC | SAML | LDAP
├── issuerUrl: String
├── clientId, clientSecret: String (OIDC)
├── samlMetadata: TEXT (SAML)
├── emailAttribute, firstNameAttribute, lastNameAttribute ← attribute mapping
└── enabled: boolean
```

---

## 4. Security Architecture

### 4.1 Security Filter Chains

```
Chain 1 (Order 1): OAuth2/OIDC endpoints — /oauth2/**, /.well-known/**, /userinfo
  │
  ├── SecurityContextHolderFilter
  ├── OrgClientAuthorizationFilter   ← enforce org ↔ client access trước khi cấp code
  │     Nếu user.orgId ≠ client.orgId → redirect /error/org-access-denied
  └── OAuth2AuthorizationEndpointFilter → cấp authorization code

Chain 2 (Order 2): Application endpoints
  ├── /login, /logout, /error/org-access-denied, /invite/**, /api/v1/auth/** → permitAll
  └── anyRequest → authenticated (session hoặc JWT Bearer)
```

**OrgClientAuthorizationFilter** (`OncePerRequestFilter`):
- Chỉ chạy trên `GET /oauth2/authorize`
- Nếu user chưa authenticated → pass through (Spring Auth Server sẽ redirect sang /login)
- Nếu user đã authenticated → check `user.organizationId == client.organizationId`
- Nếu không match → `response.sendRedirect("/error/org-access-denied")`
- Nếu client không có metadata hoặc `organizationId == null` → log ERROR + deny

### 4.2 Tenant Isolation Enforcement

Ba lớp bảo vệ:

```
Layer 1: TenantResolutionFilter    → set TenantContext từ request
Layer 2: Repository queries        → luôn có AND organization_id = ?
Layer 3: @PreAuthorize             → check role + ownership
```

Ví dụ `@PreAuthorize`:
```java
@PreAuthorize("hasRole('ORG_ADMIN') or @orgAccessChecker.isSelf(#userId)")
```

### 4.3 Token Validation Flow

```
Client gửi request với Bearer token
    ↓
Spring Security extract JWT
    ↓
Validate signature bằng JWKS public key (cached)
    ↓
Extract claims: sub, org_id, roles, permissions
    ↓
TenantContext.setCurrentTenant(orgId)  ← từ JWT claim
    ↓
Authorization check (@PreAuthorize)
    ↓
Repository query với organizationId filter
```

---

## 5. Key API Endpoints

### 5.1 Organization Management

```
POST   /api/v1/organizations              Tạo tenant mới (PLATFORM_ADMIN)
GET    /api/v1/organizations/{id}         Lấy thông tin tenant
PUT    /api/v1/organizations/{id}         Cập nhật tenant
POST   /api/v1/organizations/{id}/activate    Kích hoạt
POST   /api/v1/organizations/{id}/suspend     Tạm ngưng
GET    /api/v1/organizations/{id}/clients     Liệt kê OAuth2 clients của org (ORG_ADMIN+)
```

### 5.2 User Management

```
POST   /api/v1/users/invite               Invite user (ORG_ADMIN)
GET    /api/v1/users                      Danh sách users (phân trang)
GET    /api/v1/users/{userId}             Chi tiết user
POST   /api/v1/users/{userId}/suspend     Tạm ngưng user

POST   /api/v1/users/password/reset-request   Forgot password
POST   /api/v1/users/password/reset           Đặt lại password bằng token
POST   /api/v1/users/password/change          Đổi password (self)

POST   /api/v1/users/mfa/setup            Bắt đầu setup MFA → trả QR URI
POST   /api/v1/users/mfa/enable           Confirm MFA bằng TOTP code
POST   /api/v1/users/mfa/disable          Tắt MFA
```

### 5.3 Audit Logs

```
GET    /api/v1/audit-logs                 Tất cả logs của org (phân trang)
GET    /api/v1/audit-logs/by-user/{id}    Logs theo user
GET    /api/v1/audit-logs/by-event/{type} Logs theo event type
GET    /api/v1/audit-logs/range?from=&to= Logs theo khoảng thời gian
```

---

## 6. Database Schema

### 6.1 Thứ tự migration (Flyway)

```
V1__create_organizations.sql
V2__create_users.sql                          ← FK → organizations
V3__create_roles.sql                          ← bảng roles, role_permissions, user_roles
V4__create_identity_provider_configs.sql      ← FK → organizations (unique)
V5__create_audit_logs.sql
V6__create_oauth2_authorization_tables.sql    ← OAuth2 registered clients, authorizations
V7__seed_default_client.sql                   ← placeholder (seeding done via DataSeedingConfig)
V8__create_registered_client_metadata.sql     ← extension table: org ↔ client mapping + client_type
V9__add_post_logout_redirect_uri.sql
V10__add_system_user_flag.sql                 ← cột is_system_user trên bảng users
V11__add_row_level_security.sql               ← PostgreSQL RLS cho users, roles, audit_logs
```

> Flyway tự động chạy migration khi app khởi động theo thứ tự version. File đặt tại
> `src/main/resources/db/migration/`. Naming convention: `V{version}__{description}.sql`.
> **Không dùng Liquibase.**

### 6.2 Index quan trọng

```sql
-- Tenant lookup
CREATE UNIQUE INDEX idx_org_slug ON organizations(slug);
CREATE INDEX idx_org_domain ON organizations(primary_domain);

-- User lookup (email unique per org)
CREATE UNIQUE INDEX idx_user_email_org ON users(email, organization_id);
CREATE INDEX idx_user_org ON users(organization_id);

-- Audit query performance
CREATE INDEX idx_audit_org_time ON audit_logs(organization_id, occurred_at DESC);
CREATE INDEX idx_audit_user ON audit_logs(actor_user_id);
```

---

## 7. Cách chạy local

### 7.1 Yêu cầu

- Java 21+
- Maven 3.8+
- Docker & Docker Compose

### 7.2 Start infrastructure

```bash
# PostgreSQL + Redis + MailHog (xem email trong dev)
docker-compose --profile dev up -d

# Kiểm tra
docker-compose ps
```

### 7.3 Cấu hình environment variables

```bash
# .env hoặc export trực tiếp
export DB_USERNAME=identity_user
export DB_PASSWORD=identity_pass
export REDIS_HOST=localhost
export MAIL_HOST=localhost
export MAIL_PORT=1025
```

### 7.4 Chạy application

```bash
# Build user-management trước (tránh stale artifacts khi các module khác compile)
cd identity-platform
mvn -pl user-management -am install -DskipTests

cd auth-server
mvn spring-boot:run

# Hoặc build JAR trước
mvn clean package -DskipTests
java -jar target/auth-server-1.0.0-SNAPSHOT.jar
```

### 7.5 Verify

```bash
# OIDC Discovery
curl http://localhost:8080/.well-known/openid-configuration

# Swagger UI
open http://localhost:8080/swagger-ui.html

# MailHog (xem invite email)
open http://localhost:8025
```

---

## 8. Onboard Tenant mới

### 8.1 Tạo organization qua API

```bash
curl -X POST http://localhost:8080/api/v1/organizations \
  -H "Authorization: Bearer {PLATFORM_ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Corp",
    "slug": "acme-corp",
    "primaryDomain": "acme.com",
    "adminEmail": "admin@acme.com",
    "mfaRequired": false,
    "selfRegistrationAllowed": false
  }'
```

### 8.2 Kích hoạt organization

```bash
curl -X POST http://localhost:8080/api/v1/organizations/{orgId}/activate \
  -H "Authorization: Bearer {PLATFORM_ADMIN_TOKEN}"
```

### 8.3 Invite admin user

```bash
curl -X POST http://localhost:8080/api/v1/users/invite \
  -H "Authorization: Bearer {ORG_ADMIN_TOKEN}" \
  -H "X-Organization-Slug: acme-corp" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "manager@acme.com",
    "firstName": "Nguyen",
    "lastName": "Van A"
  }'
# → Hệ thống tự gửi email invite với link 72 giờ
```

### 8.4 Configure IdP federation (nếu tenant dùng Azure AD riêng)

```bash
curl -X POST http://localhost:8080/api/v1/organizations/{orgId}/idp \
  -H "Authorization: Bearer {ORG_ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OIDC",
    "issuerUrl": "https://login.microsoftonline.com/{tenant-id}/v2.0",
    "clientId": "azure-app-client-id",
    "clientSecret": "azure-app-secret"
  }'
# → Từ lúc này user của org này sẽ login bằng Azure AD
```

---

## 9. Tích hợp vào Platform chính

### 9.1 Cấu hình platform app làm Resource Server

```yaml
# application.yml của platform chính
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.yourplatform.com
          # Spring tự fetch JWKS từ /.well-known/openid-configuration
```

### 9.2 Extract tenant từ JWT trong platform

```java
@GetMapping("/api/bookings")
public List<Booking> getBookings(JwtAuthenticationToken auth) {
    // org_id đã được inject vào JWT bởi MultiTenantTokenCustomizer
    String orgId = auth.getToken().getClaimAsString("org_id");

    return bookingService.findByOrganization(UUID.fromString(orgId));
}
```

### 9.3 Cấu hình login flow

```yaml
# Platform app redirect sang IdP để login
spring:
  security:
    oauth2:
      client:
        registration:
          identity-platform:
            client-id: web-client
            client-secret: secret
            scope: openid, profile, email
        provider:
          identity-platform:
            issuer-uri: https://auth.yourplatform.com
```

---

## 10. Production Checklist

### 10.1 Security

- [ ] Thay RSA key generation bằng load từ KMS (AWS KMS / HashiCorp Vault)
- [ ] Encrypt `mfaSecret` trong DB bằng `@Convert` + AES-256
- [ ] Encrypt `clientSecret` của IdP config
- [ ] Enable HTTPS (TLS termination tại load balancer hoặc nginx)
- [ ] Set `Secure`, `HttpOnly`, `SameSite=Strict` cho session cookie
- [ ] Rate limiting trên `/oauth2/token` và `/api/v1/users/password/reset-request`
- [x] Registered client repository lưu DB (`JdbcRegisteredClientRepository` + `registered_client_metadata`)

### 10.2 High Availability

```
                    ┌──────────────┐
                    │ Load Balancer│
                    └──────┬───────┘
               ┌───────────┴───────────┐
               ▼                       ▼
    ┌─────────────────┐     ┌─────────────────┐
    │  auth-server #1 │     │  auth-server #2 │
    └────────┬────────┘     └────────┬────────┘
             │                       │
    ┌────────▼───────────────────────▼────────┐
    │           PostgreSQL (Primary)           │
    │              + Read Replica              │
    └──────────────────────────────────────────┘
             │
    ┌────────▼────────┐
    │   Redis Cluster  │  ← Session + Password reset tokens
    └──────────────────┘
```

- [ ] PostgreSQL với replication (Primary + Read Replica)
- [ ] Redis Sentinel hoặc Redis Cluster
- [ ] Ít nhất 2 instance auth-server (Spring Session Redis giúp stateless)
- [ ] Health check: `GET /actuator/health`

### 10.3 Observability

- [ ] Cấu hình Micrometer + Prometheus metrics
- [ ] Log aggregation (ELK hoặc Loki)
- [ ] Alert khi login failure rate tăng bất thường
- [ ] Monitor Redis memory (token accumulation)

---

## 11. Roadmap mở rộng

| Tính năng | Độ phức tạp | Ghi chú |
|---|---|---|
| Custom login page per tenant | Thấp | Thymeleaf template, resolve theo `slug` |
| FIDO2 / WebAuthn (Passkey) | Cao | Thay thế TOTP, không cần app |
| SCIM 2.0 user sync | Trung bình | Đồng bộ user từ HR system tự động |
| Rate limiting per tenant | Thấp | Bucket4j + Redis |
| Webhook on user events | Trung bình | Gọi callback URL khi login/create user |
| Admin dashboard UI | Cao | React SPA, gọi các API đã có |
| LDAP connector | Trung bình | Spring LDAP, dành cho on-premise AD |
| Session management UI | Thấp | Xem/revoke active sessions |

---

## 12. Application Ecosystem

Các application mẫu nằm ngoài `identity-platform/`, mỗi loại minh họa một OAuth2 client type khác nhau.

### 12.1 Tổng quan

```
multi-tenant/
├── identity-platform/        ← IdP (port 8080) — cấp token cho tất cả
├── realestate-app/           ← WEB_CLIENT  (port 8081) — browser, có user session
├── api-client-sample/        ← API_CLIENT  (port 8083) — M2M, không cần user
└── mobile-client-sample/     ← MOBILE_CLIENT (port 8082) — PKCE, không có secret
```

### 12.2 So sánh ba loại client

| | `realestate-app` | `api-client-sample` | `mobile-client-sample` |
|---|---|---|---|
| **Client type** | WEB_CLIENT | API_CLIENT | MOBILE_CLIENT |
| **Grant type** | `authorization_code` | `client_credentials` | `authorization_code` + PKCE |
| **Client secret** | ✅ có (server-side) | ✅ có (server-side) | ❌ không (public client) |
| **User login** | ✅ cần | ❌ không cần | ✅ cần |
| **PKCE** | Optional | N/A | ✅ bắt buộc |
| **Token owner** | user (sub = userId) | service (sub = clientId) | user (sub = userId) |

### 12.3 Organization ↔ Client mapping

Mỗi client được link với một organization qua `registered_client_metadata`:

```
Organization: realestate-corp
    ├── realestate-web-client    (WEB_CLIENT)
    ├── realestate-api-client    (API_CLIENT)
    └── realestate-mobile-client (MOBILE_CLIENT)

Organization: platform
    ├── platform-admin-client    (WEB_CLIENT — platform portal)
    └── swagger-client           (WEB_CLIENT — API docs)
```

API query: `GET /api/v1/organizations/{orgId}/clients`

### 12.4 Chạy local

```bash
# Identity Platform (port 8080)
cd identity-platform/auth-server && mvn spring-boot:run

# Web app — login qua browser (port 8081)
cd realestate-app && mvn spring-boot:run
# → http://localhost:8081

# API client — M2M demo (port 8083) — auth-server phải chạy trước
cd api-client-sample && mvn spring-boot:run

# Public endpoints (no auth):
# → GET http://localhost:8083/demo/token        xem JWT của service account
# → GET http://localhost:8083/demo/audit-logs   gọi IdP API bằng M2M token

# Protected endpoints (Bearer token required):
# → GET http://localhost:8083/api/me            trả claims của JWT caller
#   Lấy token: POST http://localhost:8080/oauth2/token
#              Basic Auth: realestate-api-client / realestate-api-secret
#              Body (form): grant_type=client_credentials&scope=openid api.read api.write

# Mobile PKCE demo — static HTML (port 8082)
cd mobile-client-sample && npx serve . -p 8082
# → http://localhost:8082
```

---

*Identity Platform v1.1.0 — Self-hosted IdP for B2B Multi-tenant*
