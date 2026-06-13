# Identity Platform — Solution Implementation Guide
## v1.3.1 — Bugfixes: Vaadin user menu icon, SSO claims, SSO default role, parking-admin RP-Initiated Logout

> Bugfix release — không thay đổi kiến trúc tổng thể (xem [SOLUTION-v1.3.0.md](SOLUTION-v1.3.0.md))

---

## 1. Tổng quan

| # | App | Vấn đề | File |
|---|---|---|---|
| 1 | parking-admin | User menu hiển thị `com.vaadin.flow.component.icon.Icon@2a423270` thay vì icon | `MainLayout.java` |
| 2 | identity-platform / user-management | `ResourceNotFoundException: User not found with id: ...` ném ra trong `MultiTenantTokenCustomizer` dù login thành công | `UserRepository.java` |
| 3 | identity-platform / user-management | User SSO (JIT provisioning) không được gán role mặc định → `user_roles` rỗng → JWT thiếu `roles` claim | `UserService.java` |
| 4 | parking-admin | Logout không hoạt động — auth-server ném `ProviderNotFoundException` cho `OidcLogoutAuthenticationToken` | `SecurityConfig.java` |

---

## 2. Fix #1 — Vaadin user menu Icon.toString()

**Nguyên nhân:** `MenuBar.addItem(String, ...)` được gọi với `VaadinIcon.USER.create().toString()` — `Icon` không override `toString()`, nên hiển thị `Icon@hashcode` thay vì render icon.

**File:** `parking-admin/src/main/java/com/kerb/parkingadmin/ui/MainLayout.java`

**Trước:**
```java
String username = authContext.getPrincipalName().orElse("Unknown");
MenuBar userMenu = new MenuBar();
userMenu.addItem(VaadinIcon.USER.create().toString() + " " + username, e -> {});
userMenu.addItem("Logout", e -> authContext.logout());
```

**Sau:**
```java
String username = authContext.getPrincipalName().orElse("Unknown");
MenuBar userMenu = new MenuBar();
HorizontalLayout userInfo = new HorizontalLayout(VaadinIcon.USER.create(), new Span(username));
userInfo.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
userInfo.setSpacing(true);
userMenu.addItem(userInfo);
userMenu.addItem("Logout", e -> authContext.logout());
```

`MenuBar.addItem(Component)` render đúng `Icon` + `Span` thay vì gọi `toString()`.

---

## 3. Fix #2 — `ResourceNotFoundException` khi user chưa có role

**Nguyên nhân:** `MultiTenantTokenCustomizer.customize()` gọi `UserService.findClaimsById()` → `UserRepository.findByIdWithRoles()` dùng `JOIN FETCH u.roles` (inner join). User chưa có record nào trong `user_roles` (set rỗng) bị **loại khỏi kết quả** → query trả `Optional.empty()` → `ResourceNotFoundException`, dù user vẫn login thành công (claim customizer chỉ log lỗi, không fail request).

**File:** `identity-platform/user-management/src/main/java/com/identityplatform/usermanagement/repository/UserRepository.java`

```java
// Trước — inner JOIN FETCH loại user có roles rỗng
@Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.id = :id AND u.deleted = false")

// Sau — LEFT JOIN FETCH giữ user dù chưa có role
@Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id AND u.deleted = false")
Optional<User> findByIdWithRoles(@Param("id") UUID id);
```

---

## 4. Fix #3 — SSO JIT provisioning thiếu role mặc định

**Nguyên nhân:** `UserService.provisionSsoUser()` (JIT provisioning khi user login SSO lần đầu, không qua invite) tạo `User` mới **không gán role nào** — khác với `inviteUser()`/`provisionDirectUser()` vốn dùng `resolveRolesForInvite()` → fallback `ORG_MEMBER`. Hệ quả: `user_roles` rỗng cho mọi user SSO JIT, JWT thiếu `roles`/`permissions` claims.

**File:** `identity-platform/user-management/src/main/java/com/identityplatform/usermanagement/service/UserService.java`

```java
@Transactional
public User provisionSsoUser(UUID organizationId, UserDto.SsoProvisionRequest request) {
    return userRepository
            .findByExternalSubjectIdAndOrganizationId(request.externalSubjectId(), organizationId)
            .orElseGet(() -> {
                Set<Role> roles = roleRepository.findByNameAndSystemRoleTrue("ORG_MEMBER")
                        .map(Set::of)
                        .orElse(Collections.emptySet());

                User user = User.builder()
                        .organizationId(organizationId)
                        .email(request.email())
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .externalSubjectId(request.externalSubjectId())
                        .authProvider(request.authProvider())
                        .status(UserStatus.ACTIVE)
                        .emailVerifiedAt(Instant.now())
                        .roles(roles)
                        .build();

                user = userRepository.save(user);
                log.info("SSO user provisioned: userId={}, orgId={}, provider={}",
                        user.getId(), organizationId, request.authProvider());
                return user;
            });
}
```

### 4.1 Backfill cho user đã tồn tại trước fix

Các user SSO JIT được tạo **trước** fix này có `user_roles` rỗng. Backfill thủ công:

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT '<user-id>', id
FROM roles WHERE name = 'ORG_MEMBER' AND system_role = true;
```

---

## 5. Fix #4 — parking-admin logout (RP-Initiated Logout)

**Triệu chứng:**
1. Trước fix: click Logout chỉ xóa session local của parking-admin, không kết thúc session SSO ở auth-server (`identity-platform`).
2. Sau khi thêm `OidcClientInitiatedLogoutSuccessHandler` (Spring built-in): auth-server log
   ```
   Login failed: username=..., reason=No AuthenticationProvider found for
   org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken
   ```

**Giải pháp áp dụng:** dùng cách build redirect **thủ công** tới `end_session_endpoint` (`/connect/logout`) của auth-server — giống cách `platform-admin` đã làm và đã chứng minh hoạt động ổn định. Đây vẫn là implementation đúng chuẩn **OpenID Connect RP-Initiated Logout 1.0** (`id_token_hint` + `post_logout_redirect_uri`); khác biệt chỉ là URL được build thủ công thay vì tự-discover qua `OidcClientInitiatedLogoutSuccessHandler`.

**File:** `parking-admin/src/main/java/com/kerb/parkingadmin/config/SecurityConfig.java`

```java
@Value("${spring.security.oauth2.client.provider.identity-platform.issuer-uri}")
private String issuerUri;

@Value("${parking-admin.post-logout-redirect-uri:http://localhost:8095/}")
private String postLogoutRedirectUri;

@Override
protected void configure(HttpSecurity http) throws Exception {
    ...
    http.logout(logout -> logout.logoutSuccessHandler(rpInitiatedLogoutSuccessHandler()));
}

LogoutSuccessHandler rpInitiatedLogoutSuccessHandler() {
    return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
        StringBuilder url = new StringBuilder(issuerUri)
                .append("/connect/logout")
                .append("?post_logout_redirect_uri=").append(postLogoutRedirectUri);
        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser oidcUser) {
            OidcIdToken idToken = oidcUser.getIdToken();
            if (idToken != null) {
                url.append("&id_token_hint=").append(idToken.getTokenValue());
            }
        }
        response.sendRedirect(url.toString());
    };
}
```

### 5.1 Hai cách triển khai RP-Initiated Logout (tham khảo cho project khác)

| Cách | Mô tả | Trạng thái |
|---|---|---|
| **Thủ công** (đang dùng) | Tự build URL `${issuer-uri}/connect/logout?id_token_hint=...&post_logout_redirect_uri=...` và `response.sendRedirect()` | Đã chứng minh hoạt động (platform-admin, parking-admin) |
| `OidcClientInitiatedLogoutSuccessHandler` (Spring built-in) | Tự discover `end_session_endpoint` qua `ClientRegistration` (issuer-uri based OIDC discovery) | Gặp `ProviderNotFoundException` ở auth-server hiện tại — **chưa root-cause được**, cần capture request `/connect/logout?...` thực tế (browser Network tab) để so sánh với request hoạt động, trước khi thử lại |

Project mới nếu muốn dùng `OidcClientInitiatedLogoutSuccessHandler`, nên test trực tiếp trước; nếu lỗi tương tự, dùng cách thủ công ở trên làm fallback.

---

## 6. Unit tests

| Test | File | Mô tả |
|---|---|---|
| `provisionSsoUser_newUser` | `UserServiceTest.java` | SSO JIT provisioning gán role `ORG_MEMBER` mặc định |
| `provisionSsoUser_newUser_noOrgMemberRole_assignsEmptyRoles` | `UserServiceTest.java` | Không lỗi (NPE) khi role `ORG_MEMBER` chưa được seed |
| `findClaimsById_userWithRoles_returnsRolesAndPermissions` | `UserServiceTest.java` | Claims trả đúng roles/permissions |
| `findClaimsById_userWithNoRoles_returnsEmptyRolesAndPermissions` | `UserServiceTest.java` | User chưa có role (LEFT JOIN FETCH) không ném `ResourceNotFoundException` |
| `findClaimsById_notFound_throwsException` | `UserServiceTest.java` | User không tồn tại → `ResourceNotFoundException` |
| `redirectsToConnectLogoutWithIdTokenHint` | `SecurityConfigTest.java` (parking-admin, mới) | Logout redirect đúng `/connect/logout?...&id_token_hint=...` |
| `omitsIdTokenHintForNonOidcAuthentication` | `SecurityConfigTest.java` (parking-admin, mới) | Không có `id_token_hint` khi principal không phải `OidcUser` |

> Lưu ý: `UserServiceTest` trước đây thiếu `@Mock ApplicationEventPublisher events` khiến `inviteUser_success`, `inviteUser_withRoleIds`, `suspendUser_success` fail với NPE — đã fix kèm trong lần này (không liên quan trực tiếp đến 3 bug trên nhưng cần để test suite chạy được).

---

## 7. Pending / Việc cần làm tiếp

- [ ] Restart parking-admin → verify logout redirect về `/` và session SSO ở auth-server bị xóa (cookie `JSESSIONID` ở auth-server invalidate).
- [ ] Backfill `user_roles` cho các user SSO JIT cũ (xem mục 4.1).
- [ ] (Optional) Root-cause `ProviderNotFoundException` cho `OidcLogoutAuthenticationToken` nếu muốn dùng `OidcClientInitiatedLogoutSuccessHandler` chuẩn ở project khác — cần request thực tế để so sánh.

---

*Identity Platform v1.3.1 — Bugfixes: Vaadin icon, SSO claims (LEFT JOIN FETCH), SSO default role, parking-admin RP-Initiated Logout*
