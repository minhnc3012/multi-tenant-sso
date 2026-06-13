# Identity Platform

Self-hosted Identity Provider (IdP) cho B2B multi-tenant applications.
Thay thế hoàn toàn Zitadel/Keycloak/Auth0 — bạn kiểm soát toàn bộ.

## Kiến trúc

```
identity-platform/
├── core/                   # Shared domain: BaseEntity, TenantContext, Exceptions
├── organization/           # Multi-tenant: Organization, IdP federation config
├── user-management/        # Users, Roles, Permissions, Invite flow
└── auth-server/            # OIDC/OAuth2 server (Spring Authorization Server)
                            # Entry point của toàn bộ hệ thống
```

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

## OIDC Endpoints (tự động expose)

| Endpoint | Mô tả |
|---|---|
| `GET /.well-known/openid-configuration` | Discovery document |
| `GET /oauth2/jwks` | Public keys để verify JWT |
| `GET /oauth2/authorize` | Authorization endpoint |
| `POST /oauth2/token` | Token endpoint |
| `POST /oauth2/revoke` | Revoke token |
| `POST /oauth2/introspect` | Introspect token |
| `GET /userinfo` | User info |

## Chạy local

```bash
# 1. Start infra
docker-compose --profile dev up -d

# 2. Chạy application
cd auth-server
mvn spring-boot:run

# 3. Truy cập
# API:      http://localhost:8080
# Swagger:  http://localhost:8080/swagger-ui.html
# OIDC:     http://localhost:8080/.well-known/openid-configuration
# MailHog:  http://localhost:8025
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
| `realestate-app` | `authorization_code` + secret | `WEB_CLIENT` | 8081 |
| `api-client-sample` | `client_credentials` | `API_CLIENT` | 8083 |
| `mobile-client-sample` | `authorization_code` + PKCE, no secret | `MOBILE_CLIENT` | 8082 |

```
multi-tenant/
├── identity-platform/        ← IdP (port 8080)
├── realestate-app/           ← web app — user login qua browser
├── api-client-sample/        ← backend service — M2M, không cần user
└── mobile-client-sample/     ← mobile/SPA — PKCE, không cần client_secret
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
