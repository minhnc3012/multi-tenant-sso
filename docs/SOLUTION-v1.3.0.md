# Identity Platform — Solution Implementation Guide
## v1.3.0 — Platform-Admin UI, IDP-First Sync, idp-client-sdk

> Self-hosted Identity Provider (IdP) cho B2B Multi-tenant Applications

---

## 0. Kiến trúc tổng thể (v1.3.0)

```
Browser
  │
  ▼
platform-admin:8090  (Spring Cloud Gateway MVC — BFF)
  │  TokenRelay filter: inject Bearer token tự động
  │  AddRequestHeader: X-Organization-Slug = platform
  │
  ├──► /api/v1/users/**, /organizations/**, /roles/**
  │       → admin-service:8081  (Spring Modulith, stateless JWT)
  │
  └──► /api/v1/registered-clients/**
          → auth-server:8080    (Spring Authorization Server, OAuth2/OIDC)

parking-admin:8095   (Vaadin 24 — single-org tenant app)
  │  OAuth2 SSO via identity-platform (authorization_code)
  │  M2M sync via idp-client-sdk (client_credentials)
  │
  └──► IDP API via idpWebClient (Bearer: M2M token)

idp-client-sdk       (Spring Boot Autoconfigure Starter)
  ├── IdpProperties        (idp.client.base-url, client-id, client-secret, org-slug)
  ├── IdpTokenProvider     (client_credentials token cache)
  ├── IdpOrgResolver       (lazy: slug → UUID, AtomicReference cache)
  └── IdpUserSyncService   (createUser, updateUser, deactivateUser, listAllUsers)
```

---

## 1. Những thay đổi trong v1.3.0

### 1.1 Tổng quan

| Area | Thay đổi |
|---|---|
| IDP — UserController | Thêm `GET /api/v1/users/org/{orgId}` (M2M endpoint) |
| IDP — OrganizationController | Thêm `GET /api/v1/organizations/slug/{slug}` (slug resolution) |
| IDP — UserService | Fix `resolveRolesForInvite()` — broken placeholder query |
| IDP — UserDto.InviteRequest | Thêm field `organizationId (UUID)` (field thứ 6) |
| IDP — DataSeedingConfig | Chỉ seed infrastructure; bỏ tenant-specific data (Kerb, realestate) |
| platform-admin — users.html | Invite modal: dynamic role loading theo org |
| platform-admin — organizations.html | Create org: thêm `adminFirstName`, `adminLastName` |
| platform-admin — clients.html | Per-org OAuth2 client registration UI |
| parking-admin — UserManagementView | "Sync from IDP" button + background sync |
| parking-admin — UserFormDialog | Role selector khi tạo user (ORG_MEMBER / ORG_ADMIN) |
| parking-admin — AppUserService | `syncFromIdp()` + null-safe search |
| parking-admin — AppShell | `@Push` chuyển sang `AppShellConfigurator` (fix Vaadin startup error) |
| idp-client-sdk — IdpProperties | `orgId: UUID` → `orgSlug: String` |
| idp-client-sdk — IdpOrgResolver | New: lazy slug→UUID với AtomicReference cache |
| idp-client-sdk — IdpPage | New: generic paginated response model |
| idp-client-sdk — IdpUserSyncService | `listAllUsers()` + dùng `IdpOrgResolver` thay `props.orgId()` |

---

## 2. IDP — Admin Service changes

### 2.1 Fix: resolveRolesForInvite() — broken placeholder

**Trước (broken):**
```java
// BROKEN: UUID.randomUUID() không bao giờ match
roleRepository.findById(UUID.randomUUID()).orElse(null)
```

**Sau (fixed):**
```java
roleRepository.findByNameAndAccessibleToOrg(name, organizationId).orElse(null)
```

Priority trong `resolveRolesForInvite()`:
1. `roleNames` → lookup by name per org
2. `roleIds` → lookup by UUID
3. Default: `ORG_MEMBER`

### 2.2 UserDto.InviteRequest — thêm field organizationId

```java
public record InviteRequest(
    @NotBlank String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    Set<UUID> roleIds,
    Set<String> roleNames,
    UUID organizationId    // field mới — null = lấy từ X-Organization-Slug header
) {}
```

**Lý do:** `OrgAdminProvisioningListener` gọi `inviteUser()` trực tiếp không qua HTTP request, nên không có X-Organization-Slug header. Field này cho phép truyền orgId explicitly.

### 2.3 Endpoint mới: GET /api/v1/users/org/{orgId}

```java
@GetMapping("/org/{orgId}")
@PreAuthorize("hasAuthority('SCOPE_users:read') or hasRole('PLATFORM_ADMIN') or hasRole('ORG_ADMIN')")
public ResponseEntity<Page<UserDto.Response>> listByOrgForSdk(
        @PathVariable UUID orgId,
        @PageableDefault(size = 100) Pageable pageable) {
    Page<User> users = userService.findByOrganization(orgId, pageable);
    return ResponseEntity.ok(users.map(this::toResponse));
}
```

Dùng bởi `idp-client-sdk.IdpUserSyncService.listAllUsers()`.

### 2.4 Endpoint mới: GET /api/v1/organizations/slug/{slug}

```java
@GetMapping("/slug/{slug}")
@PreAuthorize("hasAuthority('SCOPE_users:read') or hasRole('PLATFORM_ADMIN') or hasRole('ORG_ADMIN')")
public ResponseEntity<OrganizationDto.Response> getBySlug(@PathVariable String slug) {
    Organization org = organizationService.findBySlug(slug);
    return ResponseEntity.ok(toResponse(org));
}
```

Dùng bởi `idp-client-sdk.IdpOrgResolver` để resolve slug → UUID.

### 2.5 DataSeedingConfig — Infrastructure-only seed

**Trước:** Seed platform org + 3 system roles + platform admin user **+ Kerb org + realestate org + demo users + all clients**

**Sau:** Chỉ seed:
1. Platform org (`slug=platform`)
2. 3 system roles: `PLATFORM_ADMIN`, `ORG_ADMIN`, `ORG_MEMBER`
3. Platform admin user (`admin@platform.com`, password từ `ADMIN_PASSWORD` env)
4. Link `platform-admin-client` và `swagger-client` metadata → platform org

**Lý do:** Tenant data (Kerb, realestate) nên được tạo qua platform-admin UI, không hardcode trong seed. Từ v1.3.0, onboard tenant = dùng UI.

---

## 3. Platform-Admin UI (v1.3.0)

### 3.1 organizations.html — Create org với Admin user

Create org modal bổ sung 2 field:

```html
<input id="c-admin-first" placeholder="Admin first name (optional)" />
<input id="c-admin-last"  placeholder="Admin last name (optional)" />
```

`createOrg()` gửi `adminFirstName` + `adminLastName` → IDP `OrgAdminProvisioningListener` dùng để tạo ORG_ADMIN user ngay sau khi org được tạo.

### 3.2 users.html — Dynamic role loading khi invite

Khi chọn org trong invite modal, JS gọi `GET /api/v1/roles/org/{orgId}` để load roles:

```javascript
async function onInviteOrgChange(orgId) {
    const res = await fetch(`/api/v1/roles/org/${orgId}`);
    const roles = await res.json();
    const select = document.getElementById('i-role');
    select.innerHTML = roles.map(r => `<option value="${r.name}">${r.name}</option>`).join('');
}
```

`inviteUser()` gửi `roleNames: [roleName]` trong body.

### 3.3 clients.html — Per-org OAuth2 client registration

- Org filter dropdown để chọn org
- `clientType` dropdown: WEB_CLIENT / API_CLIENT / MOBILE_CLIENT / M2M_CLIENT
- Redirect URIs textarea (một URI mỗi dòng)
- `POST /api/v1/organizations/{orgId}/clients`
- Secret-shown-once modal sau khi register (copy button)

---

## 4. parking-admin (v1.3.0)

### 4.1 Fix: lower(bytea) PostgreSQL error

**Nguyên nhân:** JPQL `:search IS NULL` với null parameter → PostgreSQL bind null as `bytea`, `lower(bytea)` không hợp lệ.

**Fix:** Bỏ null check khỏi JPQL, xử lý ở service layer:

```java
// AppUserRepository.java
@Query("SELECT u FROM AppUser u WHERE lower(u.email) LIKE lower(concat('%', :keyword, '%')) ...")
Page<AppUser> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

// AppUserService.java
public Page<AppUser> search(String keyword, Pageable pageable) {
    if (!StringUtils.hasText(keyword)) {
        return userRepository.findAll(pageable);        // no filter
    }
    return userRepository.searchByKeyword(keyword, pageable);  // with filter
}
```

### 4.2 Fix: @Push Vaadin startup error

**Nguyên nhân:** Vaadin yêu cầu `@Push` phải đặt trên class implement `AppShellConfigurator`, không phải trên `AppLayout`.

**Fix:**
```java
// AppShell.java (NEW)
@Push
public class AppShell implements AppShellConfigurator {}

// MainLayout.java — xóa @Push annotation
```

### 4.3 Tính năng mới: Role selector khi tạo user

`UserFormDialog` (create mode only) — `Select<String>` với options:
- `ORG_MEMBER` (default)
- `ORG_ADMIN`

`FormData` record bổ sung `String idpRole`. Giá trị được đưa vào `CreateUserRequest.idpRoles = Set.of(idpRole)`.

### 4.4 Tính năng mới: Sync from IDP

Button "Sync from IDP" (Lumo contrast) trong `UserManagementView`.

Flow:
1. Click → button disabled, text = "Syncing..."
2. Background thread: `appUserService.syncFromIdp()`
3. `syncFromIdp()` gọi `idpUserSyncService.listAllUsers()` → lấy tất cả users của org từ IDP
4. Upsert theo `idpUserId`:
   - **Không có local**: tạo mới, `idpSyncStatus=SYNCED`
   - **Có local, data khác**: update name/email, `idpSyncStatus=SYNCED`
   - **Có local, data giống**: skip
5. `ui.access()` → update button, show notification với kết quả

```java
public record SyncResult(int total, int created, int updated, int skipped) {}
```

**IDP-first principle:** IDP là source of truth. Sync KHÔNG xóa local users — chỉ tạo/cập nhật.

---

## 5. idp-client-sdk (v1.3.0)

### 5.1 IdpProperties — orgSlug thay orgId

```java
@ConfigurationProperties(prefix = "idp.client")
public record IdpProperties(
    String baseUrl,
    String clientId,
    String clientSecret,
    String orgSlug    // was: UUID orgId
) {}
```

**Lý do chọn slug thay UUID:**

| | UUID trực tiếp | Slug → resolve UUID |
|---|---|---|
| Config | Phải tra UUID thủ công khi setup | Chỉ cần nhớ slug (`kerb`, `realestate`) |
| Environment | Dev/prod có thể khác UUID nếu recreate DB | Slug ổn định qua mọi môi trường |
| Tenant context | Phải inject riêng | `IdpOrgResolver` bean — inject ở bất kỳ đâu |
| Startup | Không cần IDP | Cần IDP up lần đầu (hoặc khi resolve) |

`IdpOrgResolver` là Spring bean — inject vào bất kỳ service nào cần org UUID (tenant filtering, header, v.v.).

### 5.2 IdpOrgResolver — Lazy slug → UUID resolution

```java
@Slf4j
@RequiredArgsConstructor
public class IdpOrgResolver {
    private final IdpProperties props;
    private final IdpTokenProvider tokenProvider;
    private final WebClient webClient;

    private final AtomicReference<UUID> cached = new AtomicReference<>();

    public UUID getOrgId() {
        UUID id = cached.get();
        if (id != null) return id;
        synchronized (this) {
            id = cached.get();
            if (id != null) return id;
            id = resolve();      // GET /api/v1/organizations/slug/{slug}
            cached.set(id);
        }
        return id;
    }
}
```

- Double-checked locking: thread-safe, chỉ resolve 1 lần
- UUID cached mãi trong JVM lifetime
- Resolve thất bại → `IllegalStateException` (app không start được nếu IDP không có slug)

### 5.3 IdpPage<T> — Generic paginated response

```java
public record IdpPage<T>(
    List<T> content,
    int number,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {}
```

Dùng với `ParameterizedTypeReference<IdpPage<IdpUserResponse>>` trong `listAllUsers()`.

### 5.4 IdpUserSyncService — listAllUsers() + orgResolver

```java
public List<IdpUserResponse> listAllUsers() {
    UUID orgId = orgResolver.getOrgId();  // không còn props.orgId()
    List<IdpUserResponse> result = new ArrayList<>();
    int page = 0;
    boolean last = false;
    while (!last) {
        // GET /api/v1/users/org/{orgId}?page={page}&size=100
        IdpPage<IdpUserResponse> pageResult = ...;
        result.addAll(pageResult.content());
        last = pageResult.last();
        page++;
    }
    return result;
}
```

### 5.5 Bean wiring

Cả `IdpClientAutoConfiguration` (autoconfigure path) lẫn `IdpSdkConfig` (parking-admin explicit wiring) đều expose `IdpOrgResolver` bean:

```java
@Bean
public IdpOrgResolver idpOrgResolver(IdpProperties props,
                                     IdpTokenProvider idpTokenProvider,
                                     WebClient idpWebClient) {
    return new IdpOrgResolver(props, idpTokenProvider, idpWebClient);
}

@Bean
public IdpUserSyncService idpUserSyncService(IdpProperties props,
                                             IdpTokenProvider tokenProvider,
                                             WebClient idpWebClient,
                                             IdpOrgResolver idpOrgResolver) {
    return new IdpUserSyncService(props, tokenProvider, idpWebClient, idpOrgResolver);
}
```

---

## 6. Configuration

### 6.1 parking-admin application.yml (v1.3.0)

```yaml
idp:
  client:
    base-url: ${IDP_BASE_URL:http://localhost:8080}
    client-id: parking-admin-api-client
    client-secret: ${PARKING_ADMIN_API_CLIENT_SECRET:parking-admin-api-secret}
    org-slug: ${IDP_ORG_SLUG:kerb}    # was: org-id: ${KERB_ORG_ID:00000000-...}
```

### 6.2 OAuth2 clients cần thiết cho parking-admin

| clientId | grant type | scope | Dùng cho |
|---|---|---|---|
| `parking-admin-web-client` | `authorization_code` | `openid profile email` | SSO user login |
| `parking-admin-api-client` | `client_credentials` | `users:read users:write` | M2M sync API |

Cả 2 client phải được tạo từ platform-admin → Clients tab, linked to Kerb org.

---

## 7. Luồng Onboard Tenant mới (từ v1.3.0 — UI-based)

Không còn cần sửa code hay seed data. Mọi thứ qua UI:

```
1. Đăng nhập platform-admin (http://localhost:8090)
   └── admin@platform.com / admin

2. Organizations tab → Create
   ├── name: "Kerb Parking"
   ├── slug: "kerb"
   ├── primaryDomain: "kerb.com.au"
   ├── adminFirstName: "Org"
   └── adminLastName: "Admin"
   → IDP tự tạo ORG_ADMIN user: orgadmin@kerb.com.au (email invite)

3. Clients tab → Select org "Kerb" → Register Client
   ├── parking-admin-web-client (WEB_CLIENT, scope: openid profile email)
   │   redirectUri: http://localhost:8095/login/oauth2/code/identity-platform
   └── parking-admin-api-client (M2M_CLIENT, scope: users:read users:write)

4. Users tab → Invite → chọn org "Kerb" → roles load động
   └── Invite admin user với role ORG_ADMIN

5. parking-admin (http://localhost:8095) → đăng nhập bằng ORG_ADMIN user
   └── Tạo thêm user nội bộ hoặc dùng "Sync from IDP"
```

---

## 8. Cách chạy local (v1.3.0)

### 8.1 Thứ tự khởi động

```bash
# 1. Build SDK (sau mỗi lần thay đổi)
cd idp-client-sdk
mvn install -DskipTests

# 2. Start Identity Platform (IDP + admin-service)
cd identity-platform
mvn -pl user-management -am install -DskipTests
cd auth-server && mvn spring-boot:run
# → http://localhost:8080

# 3. Start platform-admin
cd platform-admin && mvn spring-boot:run
# → http://localhost:8090
# Onboard tenant Kerb qua UI

# 4. Start parking-admin
cd parking-admin && mvn spring-boot:run
# → http://localhost:8095
```

### 8.2 Environment variables

```bash
# IDP
export ADMIN_PASSWORD=admin

# parking-admin
export PARKING_ADMIN_WEB_CLIENT_SECRET=parking-admin-web-secret
export PARKING_ADMIN_API_CLIENT_SECRET=parking-admin-api-secret
export IDP_ORG_SLUG=kerb         # optional, default = kerb
```

### 8.3 Databases

| App | Port | DB name |
|---|---|---|
| identity-platform | 5433 | `identity_platform` |
| parking-admin | 5433 | `parking_admin` |

---

## 9. Pending / Việc cần làm tiếp

### 9.1 Còn cần test

- [ ] Restart IDP → verify `GET /api/v1/organizations/slug/kerb` trả về đúng
- [ ] Restart parking-admin → verify `IdpOrgResolver` resolve thành công
- [ ] Click "Sync from IDP" → verify users sync xuống `parking_admin` DB
- [ ] Tạo user từ parking-admin với role ORG_ADMIN/ORG_MEMBER

### 9.2 Cải tiến tiềm năng

| Tính năng | Mô tả |
|---|---|
| Cache invalidation | `IdpOrgResolver` cached UUID — thêm `/admin/refresh-org-cache` endpoint nếu cần reset |
| Sync webhook | Thay polling bằng webhook từ IDP push user events xuống parking-admin |
| Sync scheduled | `@Scheduled` auto-sync mỗi N phút thay phải bấm button |
| Delete/suspend sync | Hiện tại sync chỉ create/update, chưa handle user bị suspend ở IDP |
| Role mapping | Map IDP roles → parking-admin roles linh hoạt hơn (hiện hardcode ORG_ADMIN/ORG_MEMBER) |

---

*Identity Platform v1.3.0 — Platform-Admin UI, IDP-First Sync, idp-client-sdk slug resolution*  
*Từ version này: không còn seed tenant data — mọi thứ setup qua platform-admin UI*
