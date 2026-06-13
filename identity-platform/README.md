# Identity Platform

Self-hosted Identity Provider (IdP) cho B2B multi-tenant applications.
Thay thế hoàn toàn Zitadel/Keycloak/Auth0 — bạn kiểm soát toàn bộ.

## Kiến trúc

```
identity-platform/
├── core/                   # Shared domain: BaseEntity, TenantContext, Exceptions
├── organization/           # Multi-tenant: Organization, IdP federation config
├── user-management/        # Users, Roles, Permissions, Invite flow
├── auth-server/            # Authentication API — OIDC/OAuth2 (Spring Authorization Server)
│                           # /oauth2/*, /.well-known/*, /userinfo, /api/v1/auth/* — port 8080
└── admin-service/          # Management API — Users/Orgs/Roles/Registered Clients/Audit
                            # /api/v1/users/**, /organizations/**, /roles/**,
                            # /registered-clients/**, /audit-logs/** — port 8081
```

Theo mô hình Auth0/Okta: `auth-server` = Authentication API (protocol plane),
`admin-service` = Management API (admin plane). Xem chi tiết:
[../docs/SOLUTION-v1.4.0.md](../docs/SOLUTION-v1.4.0.md).

## Tính năng

- ✅ **OIDC/OAuth2 chuẩn** — tương thích mọi client library
- ✅ **Multi-tenant native** — mỗi Organization là một tenant độc lập
- ✅ **Tenant isolation** — data hoàn toàn tách biệt
- ✅ **Delegated admin** — tenant admin tự quản lý user của họ
- ✅ **IdP federation** — tenant có thể dùng Azure AD/Okta riêng
- ✅ **Just-in-time provisioning** — tự tạo user khi lần đầu SSO
- ✅ **Invite flow** — admin invite user qua email
- ✅ **MFA per organization** — cấu hình MFA riêng cho từng tenant
- ✅ **Custom branding** — logo/màu riêng cho login page mỗi tenant
- ✅ **Flyway migrations** — schema versioning (V1 → V8)
- ✅ **Redis session** — stateless, scale horizontally
- ✅ **Organization ↔ Application mapping** — org đăng ký client nào, typed: WEB / API / MOBILE / M2M

## OIDC Endpoints (auth-server — Authentication API, port 8080)

| Endpoint | Mô tả |
|---|---|
| `GET /.well-known/openid-configuration` | Discovery document |
| `GET /oauth2/jwks` | Public keys để verify JWT |
| `GET /oauth2/authorize` | Authorization endpoint |
| `POST /oauth2/token` | Token endpoint |
| `POST /oauth2/revoke` | Revoke token |
| `POST /oauth2/introspect` | Introspect token |
| `GET /userinfo` | User info |
| `GET /api/v1/auth/me`, `POST /logout` | Session/token-bound, AuthController |

## Management API Endpoints (admin-service — port 8081)

| Endpoint | Mô tả |
|---|---|
| `/api/v1/users/**` | Quản lý user (CRUD, invite, provisioning) |
| `/api/v1/organizations/**` | Quản lý organization, slug resolution |
| `/api/v1/roles/**` | Quản lý role |
| `/api/v1/registered-clients/**` | Quản lý OAuth2 registered clients ("Applications") |
| `/api/v1/audit-logs/**` | Audit log |

## Chạy local

```bash
# 1. Start infra
docker-compose --profile dev up -d

# 2. Chạy application (build core/organization/user-management trước)
mvn -pl core,organization,user-management -am install -DskipTests

cd auth-server
mvn spring-boot:run        # Authentication API — port 8080

cd ../admin-service
mvn spring-boot:run        # Management API — port 8081

# 3. Truy cập
# Authentication API: http://localhost:8080
# Management API:     http://localhost:8081
# Swagger:             http://localhost:8080/swagger-ui.html, http://localhost:8081/swagger-ui.html
# OIDC:                http://localhost:8080/.well-known/openid-configuration
# MailHog:             http://localhost:8025
```

## Tenant detection (theo thứ tự ưu tiên)

1. **Header**: `X-Organization-Slug: company-name`
2. **Subdomain**: `company-name.yourplatform.com`
3. **Path**: `/api/v1/t/{slug}/...`
4. **Email domain**: `user@company.com` → tìm org có `primaryDomain = company.com`

## JWT Token Claims

```json
{
  "sub": "user-uuid",
  "email": "user@company.com",
  "name": "Nguyen Van A",
  "org_id": "org-uuid",
  "roles": ["ORG_ADMIN", "ORG_MEMBER"],
  "permissions": ["users:read", "users:write"],
  "iss": "https://auth.yourplatform.com",
  "exp": 1234567890
}
```

## Application Ecosystem

Các application mẫu áp dụng Identity Platform:

| Application | Grant type | Client type | Port |
|---|---|---|---|
| `identity-platform/admin-service` | (resource server, validate JWT của auth-server) | — | 8081 |
| `platform-admin` | `authorization_code` (BFF — Spring Cloud Gateway MVC) | `WEB_CLIENT` | 8090 |
| `parking-admin` | `authorization_code` (SSO) + `client_credentials` (idp-client-sdk sync) | `WEB_CLIENT` + `M2M_CLIENT` | 8095 |
| `realestate-app` | `authorization_code` + secret | `WEB_CLIENT` | 8084 |
| `api-client-sample` | `client_credentials` | `API_CLIENT` | 8083 |
| `mobile-client-sample` | `authorization_code` + PKCE, no secret | `MOBILE_CLIENT` | 8082 |

> `realestate-app` đã đổi từ port `8081` → `8084` (xung đột với `admin-service`, từ v1.4.0
> port `8081` là Management API). Xem [../docs/SOLUTION-v1.4.0.md](../docs/SOLUTION-v1.4.0.md).
> Migration `V15__realestate_app_port_8081_to_8084.sql` cập nhật redirect URI đã seed
> trong DB cho `realestate-web-client`.
> Riêng `realestate-mobile-client` (Expo) vẫn giữ redirect `localhost:8081/callback`
> — đó là port mặc định của Expo web bundler, không liên quan tới `realestate-app`/`admin-service`.

```
multi-tenant/
├── identity-platform/
│   ├── auth-server/          ← Authentication API (port 8080)
│   └── admin-service/         ← Management API (port 8081)
├── platform-admin/            ← Gateway/BFF cho platform admin (port 8090)
├── parking-admin/             ← tenant app (Vaadin), idp-client-sdk consumer (port 8095)
├── realestate-app/            ← web app — user login qua browser (port 8084)
├── api-client-sample/         ← backend service — M2M, không cần user (port 8083)
└── mobile-client-sample/      ← mobile/SPA — PKCE, không cần client_secret (port 8082)
```

## Roadmap mở rộng

- [ ] TOTP/FIDO2 MFA
- [ ] SCIM 2.0 user sync
- [ ] Audit log per organization
- [ ] Custom login page per tenant (Thymeleaf templates)
- [ ] Rate limiting per tenant
- [ ] Webhook on user events
- [ ] Admin dashboard UI
- [ ] OrganizationDatasourceConfig — per-org DB connection cho multi-tenant apps
