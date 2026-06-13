# Identity Platform — Solution Implementation Guide
## v1.4.0 — Authentication API vs Management API (Auth0/Okta model)

> Self-hosted Identity Provider (IdP) cho B2B Multi-tenant Applications

---

## 0. Kiến trúc tổng thể (v1.4.0)

Trước v1.4.0, `auth-server` (8080) và `admin-service` (8081) đều dùng
`@SpringBootApplication(scanBasePackages = "com.identityplatform")` nên
`UserController`, `RoleController`, `OrganizationController` bị **đăng ký
trùng** ở cả 2 service — gây nhầm lẫn về việc admin-service "để làm gì".

v1.4.0 tách rõ 2 plane theo chuẩn Auth0/Okta:

```
Browser
  │
  ▼
platform-admin:8090  (Spring Cloud Gateway MVC — BFF)
  │  TokenRelay filter: inject Bearer token tự động
  │  AddRequestHeader: X-Organization-Slug = platform
  │
  ├──► admin-api  (Management API)
  │     Path=/api/v1/users/**, /organizations/**, /roles/**,
  │          /registered-clients/**, /audit-logs/**
  │       → admin-service:8081
  │
  └──► auth-api   (Authentication API)
        Path=/api/v1/auth/**
          → auth-server:8080

auth-server:8080   — Authentication API (Auth0 "Authentication API" tương đương)
  ├── OIDC/OAuth2 protocol: /oauth2/*, /.well-known/*, /userinfo, /connect/logout
  ├── AuthController: /api/v1/auth/me, /logout (session/token-bound)
  └── RegisteredClientMetadataService/Repository (giữ lại — dùng bởi
      OrgClientAuthorizationFilter + DataSeedingConfig, là 1 phần của
      OAuth2 filter chain runtime)

admin-service:8081 — Management API (Auth0 "Management API" tương đương)
  ├── UserController, RoleController, OrganizationController
  ├── AuditController            (mới chuyển từ auth-server)
  ├── RegisteredClientController, ClientRegistrationController
  │     (mới chuyển từ auth-server — "Applications" management)
  └── RegisteredClientMetadataService/Repository (local copy, cùng bảng
      registered_client_metadata, flyway.enabled=false)
```

---

## 1. Những thay đổi trong v1.4.0

### 1.1 Tổng quan

| Area | Thay đổi |
|---|---|
| auth-server — `IdentityPlatformApplication` | `scanBasePackages` → `@ComponentScan` với `excludeFilters` (REGEX) loại `usermanagement.controller.*` và `organization.controller.*` |
| auth-server | Xóa `AuditController`, `RegisteredClientController`, `ClientRegistrationController`, `RegisteredClientService`, `ClientRegistrationService`, `dto/ClientRegistrationDto` (chuyển sang admin-service) |
| auth-server | **Giữ nguyên** `RegisteredClientMetadataService/Repository/RegisteredClientMetadata/ClientType`, `AuthorizationServerConfig`, `AuthController`, `SecurityBeansConfig`, `WebExceptionHandler` |
| admin-service — `pom.xml` | Thêm dependency `spring-security-oauth2-authorization-server` (chỉ để dùng `JdbcRegisteredClientRepository`, không kích hoạt authorization-server auto-config) |
| admin-service | Thêm `config/RegisteredClientConfig` (bean `RegisteredClientRepository` + `PasswordEncoder`) |
| admin-service | Chuyển vào: `AuditController`, `RegisteredClientController`, `ClientRegistrationController`, `RegisteredClientService`, `ClientRegistrationService`, `dto/ClientRegistrationDto` (package đổi thành `com.identityplatform.adminservice.*`) |
| admin-service | Thêm local copy `RegisteredClientMetadataService/Repository/RegisteredClientMetadata/ClientType` (cùng bảng `registered_client_metadata`, an toàn vì `flyway.enabled=false`) |
| platform-admin — `application.yml` | Gộp route `admin-api` (users/organizations/roles/registered-clients/audit-logs → :8081) và route mới `auth-api` (`/api/v1/auth/**` → :8080); bỏ route `auth-clients` cũ |
| idp-client-sdk — `IdpProperties` | Thêm field `managementBaseUrl` + helper `resolvedManagementBaseUrl()` (fallback về `baseUrl` nếu chưa cấu hình) |
| idp-client-sdk — `IdpUserSyncService`, `IdpOrgResolver` | Đổi các call `/api/v1/users/**`, `/api/v1/organizations/slug/**` từ `props.baseUrl()` → `props.resolvedManagementBaseUrl()` |
| idp-client-sdk — `IdpTokenProvider` | Không đổi — `/oauth2/token` vẫn ở `baseUrl` (auth-server) |
| parking-admin — `application.yml` | Thêm `idp.client.management-base-url: ${IDP_MANAGEMENT_BASE_URL:http://localhost:8081}` |
| api-client-sample — `application.yml` | Thêm `identity-platform.management-base-url: http://localhost:8081` |
| api-client-sample — `IdentityPlatformClient` | `listOrgClients()` và `listAuditLogs()` gọi qua `managementBaseUrl` (helper `get(base, path, type)`) |

---

## 2. auth-server — chỉ còn Authentication API

**File:** `identity-platform/auth-server/src/main/java/com/identityplatform/authserver/IdentityPlatformApplication.java`

```java
@SpringBootApplication
@ComponentScan(
        basePackages = "com.identityplatform",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = {
                                "com\\.identityplatform\\.usermanagement\\.controller\\..*",
                                "com\\.identityplatform\\.organization\\.controller\\..*"
                        }
                )
        }
)
@EntityScan(basePackages = "com.identityplatform")
@EnableJpaRepositories(basePackages = "com.identityplatform")
@EnableJpaAuditing
@EnableAsync
public class IdentityPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityPlatformApplication.class, args);
    }
}
```

Service/repository/domain/event của `usermanagement` và `organization` **không** bị loại
(khác sub-package) — vẫn cần cho `AuthController`, `MultiTenantUserDetailsService`,
`DataSeedingConfig`.

Email link trong `InviteService`/`PasswordResetService` dùng placeholder
`https://yourplatform.com/...`, không phải `localhost:8080` → không bị ảnh hưởng.

---

## 3. admin-service — Management API đầy đủ

### 3.1 `pom.xml`

```xml
<!-- JdbcRegisteredClientRepository cho quản lý OAuth2 registered clients
     (chung bảng oauth2_registered_client; auth-server giữ Flyway schema).
     Không kích hoạt OAuth2AuthorizationServerAutoConfiguration vì admin-service
     không có AuthorizationServerSettings bean và không gọi applyDefaultSecurity(). -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-authorization-server</artifactId>
</dependency>
```

### 3.2 Package layout mới (`admin-service/src/main/java/com/identityplatform/adminservice/`)

```
config/RegisteredClientConfig.java            (mới)
controller/AuditController.java               (chuyển)
controller/RegisteredClientController.java    (chuyển)
controller/ClientRegistrationController.java  (chuyển)
service/RegisteredClientService.java          (chuyển)
service/ClientRegistrationService.java        (chuyển)
service/RegisteredClientMetadataService.java  (local copy)
repository/RegisteredClientMetadataRepository.java (local copy)
domain/RegisteredClientMetadata.java          (local copy)
domain/ClientType.java                        (local copy)
dto/ClientRegistrationDto.java                (chuyển)
```

### 3.3 `RegisteredClientConfig`

```java
@Configuration
public class RegisteredClientConfig {
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

admin-service trước đó không có `PasswordEncoder` bean — cần cho hashing client-secret
trong `RegisteredClientService`.

`SecurityConfig.java` không cần đổi — các controller chuyển sang đã có sẵn
`@PreAuthorize`, `@EnableMethodSecurity` đã bật.

---

## 4. platform-admin — Gateway routes

**File:** `platform-admin/src/main/resources/application.yml`

```yaml
  cloud:
    gateway:
      mvc:
        routes:
          # Management API (admin-service) - users, orgs, roles, registered-clients, audit-logs
          - id: admin-api
            uri: ${services.admin-api.base-url}
            predicates:
              - Path=/api/v1/users/**, /api/v1/organizations/**, /api/v1/roles/**, /api/v1/registered-clients/**, /api/v1/audit-logs/**
            filters:
              - TokenRelay=
              - AddRequestHeader=X-Organization-Slug, platform

          # Authentication API (auth-server) - session/token-bound endpoints
          - id: auth-api
            uri: ${services.auth-server.base-url}
            predicates:
              - Path=/api/v1/auth/**
            filters:
              - TokenRelay=
              - AddRequestHeader=X-Organization-Slug, platform

services:
  admin-api:
    base-url: http://localhost:8081
  auth-server:
    base-url: http://localhost:8080
```

`/api/v1/organizations/{orgId}/clients` (ClientRegistrationController) nằm trong
`/api/v1/organizations/**` → route `admin-api`, không cần route riêng.

---

## 5. idp-client-sdk — Management API base URL

**`IdpProperties`** — thêm field thứ 5:

```java
@ConfigurationProperties(prefix = "idp.client")
public record IdpProperties(
        String baseUrl,           // Authentication API (OIDC token endpoint)
        String clientId,
        String clientSecret,
        String orgSlug,
        String managementBaseUrl  // Management API (users/orgs/roles/...)
) {
    public String resolvedManagementBaseUrl() {
        return (managementBaseUrl != null && !managementBaseUrl.isBlank()) ? managementBaseUrl : baseUrl;
    }
}
```

`IdpUserSyncService` (`createUser`, `updateUser`, `deactivateUser`, `listAllUsers`)
và `IdpOrgResolver.resolve()` dùng `resolvedManagementBaseUrl()`.
`IdpTokenProvider` không đổi (`/oauth2/token` vẫn ở `baseUrl`).

### 5.1 Consumer config

**parking-admin** (`application.yml`):
```yaml
idp:
  client:
    base-url: ${IDP_BASE_URL:http://localhost:8080}
    management-base-url: ${IDP_MANAGEMENT_BASE_URL:http://localhost:8081}
```

**api-client-sample** (`application.yml`):
```yaml
identity-platform:
  base-url: http://localhost:8080
  management-base-url: http://localhost:8081
```

`IdentityPlatformClient.listOrgClients()` và `listAuditLogs()` gọi qua `managementBaseUrl`
(:8081) thay vì `baseUrl` (:8080).

> Lưu ý (pre-existing, chưa fix): scope seed của `realestate-api-client`
> (`openid, api.read, api.write`) có thể không thỏa `@PreAuthorize("hasRole('PLATFORM_ADMIN')
> or hasRole('ORG_ADMIN')")` trên `ClientRegistrationController`/`AuditController` —
> nếu api-client-sample gọi `listOrgClients`/`listAuditLogs` trả 403, đây là gap
> role-seeding sẵn có, không phải lỗi do refactor này.

---

## 6. Verify đã thực hiện (2026-06-13)

Build (compile, không lỗi): `core`, `organization`, `user-management`, `auth-server`,
`admin-service`, `idp-client-sdk`, `api-client-sample`, `parking-admin`.

Chạy thực tế (Postgres + Redis):

| Check | Kết quả |
|---|---|
| `auth-server:8080/.well-known/openid-configuration` | 200 |
| `auth-server:8080/api/v1/users` \| `/organizations` \| `/audit-logs` (Bearer M2M token) | **404** (đã loại khỏi Authentication API) |
| `auth-server:8080/api/v1/auth/me` (Bearer M2M token) | 200 (AuthController vẫn còn) |
| `admin-service:8081/api/v1/users` \| `/organizations` \| `/audit-logs` \| `/registered-clients` (Bearer M2M token, thiếu role admin) | **403** (endpoint tồn tại, đúng route, đúng role-check) |
| `platform-admin:8090/api/v1/organizations` (chưa login) | 302 → login OIDC (đúng) |
| `parking-admin:8095/actuator/health` | 200 |

Không có `ERROR`/`Exception` trong log của 4 service khi start.

### 6.1 Follow-up: resolve port conflict `realestate-app` ↔ `admin-service` (cả hai 8081)

Từ v1.4.0, `admin-service` chạy ở port `8081` (Management API) — trùng với port có sẵn
của `realestate-app`. Đổi `realestate-app`: `8081` → `8084`.

| File | Thay đổi |
|---|---|
| `realestate-app/src/main/resources/application.yml` | `server.port: 8084`, `redirect-uri: http://localhost:8084/login/oauth2/code/identity-platform` |
| `realestate-app/src/main/java/com/realestate/config/SecurityConfig.java` | `POST_LOGOUT_REDIRECT = "http://localhost:8084/"` |
| `identity-platform/auth-server/.../config/AuthorizationServerConfig.java` | seed `realestate-web-client`: `redirectUri`/`postLogoutRedirectUri` → `8084` (áp dụng cho DB mới) |
| `identity-platform/auth-server/.../db/migration/V15__realestate_app_port_8081_to_8084.sql` | **mới** — UPDATE `oauth2_registered_client` cho `realestate-web-client` đã seed trước đó (redirect_uris, post_logout_redirect_uris: `8081` → `8084`) |
| `identity-platform/auth-server/src/main/resources/application.yml` | `cors.allowed-origins`: bỏ `8081`, thêm `8084` |

`realestate-mobile-client` (`redirectUri("http://localhost:8081/callback")`, seed từ V12)
**không đổi** — đó là port mặc định của Expo web bundler khi chạy `mobile-client-sample`
ở web mode, khác với port server của `realestate-app`.

Các port khác đã review, không còn conflict: `auth-server` 8080, `admin-service` 8081,
`platform-admin` 8090, `parking-admin` 8095, `api-client-sample` 8083, `realestate-app` 8084.

---

## 7. Pending / Việc cần làm tiếp

- [ ] Test platform-admin UI end-to-end: org/user/role/registered-clients/audit pages
      route đúng sang :8081 (xem Network tab), logout vẫn hit :8080.
- [ ] Test parking-admin (idp-client-sdk consumer): provisioning/update/deactivate user
      và resolve org slug hit :8081; `/oauth2/token` vẫn ở :8080.
- [ ] Test api-client-sample: `listOrgClients`/`listAuditLogs` hit :8081 — kiểm tra
      403 do role-seeding gap (mục 5.1) nếu có, không phải do refactor.
- [ ] Regression OAuth2: `/oauth2/authorize` + `/oauth2/token` cho `platform-admin-client`,
      `realestate-web-client`, `parking-admin-web-client`, client-credentials —
      `OrgClientAuthorizationFilter`/`RegisteredClientMetadataService`/`DataSeedingConfig`
      không bị đổi, restart auth-server để confirm seeding idempotent.

---

*Identity Platform v1.4.0 — Authentication API (auth-server:8080) vs Management API
(admin-service:8081), theo mô hình Auth0/Okta*
