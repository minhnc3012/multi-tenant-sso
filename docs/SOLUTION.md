# Identity Platform — Solution Index

> Mỗi version thay đổi được lưu thành file riêng. Đọc file version mới nhất để có thông tin đầy đủ nhất.

## Versions

| Version | File | Ngày | Nội dung chính |
|---|---|---|---|
| v1.4.0 | [SOLUTION-v1.4.0.md](SOLUTION-v1.4.0.md) | 2026-06-13 | Tách Authentication API (auth-server) vs Management API (admin-service) theo mô hình Auth0/Okta: chuyển AuditController/RegisteredClientController/ClientRegistrationController sang admin-service, loại trùng UserController/RoleController/OrganizationController khỏi auth-server, cập nhật gateway routes + idp-client-sdk managementBaseUrl |
| v1.3.1 | [SOLUTION-v1.3.1.md](SOLUTION-v1.3.1.md) | 2026-06-11 | Bugfixes: Vaadin user menu icon, SSO claims (LEFT JOIN FETCH), SSO JIT default role, parking-admin RP-Initiated Logout |
| v1.3.0 | [SOLUTION-v1.3.0.md](SOLUTION-v1.3.0.md) | 2026-06-09 | Platform-admin UI (invite role, org clients), parking-admin Sync from IDP, idp-client-sdk slug resolution, bỏ tenant seed data |
| v1.2.0 | [SOLUTION-v1.2.0.md](SOLUTION-v1.2.0.md) | 2026-06-09 | Mobile form login (MobileAuthController), LoginRateLimitFilter, RSA key persistence (V14), externalize secrets, configurable issuer + CORS |
| v1.1.0 | [SOLUTION-v1.1.0.md](SOLUTION-v1.1.0.md) | 2026-06-09 | OrgClientAuthorizationFilter, org-access-denied page, api-client-sample /api/me, V11 RLS migration |
| v1.0.0 | [SOLUTION-v1.0.0.md](SOLUTION-v1.0.0.md) | baseline | Initial implementation: IdP core, multi-tenant, OIDC/OAuth2, MFA, audit log |

**Latest:** [SOLUTION-v1.4.0.md](SOLUTION-v1.4.0.md)

---

*Xem thêm: [../identity-platform/README.md](../identity-platform/README.md) — overview module identity-platform*
