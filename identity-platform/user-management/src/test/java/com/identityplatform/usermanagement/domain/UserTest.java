package com.identityplatform.usermanagement.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User domain tests")
class UserTest {

    @Test
    @DisplayName("isActive: true when status = ACTIVE")
    void isActive_active() {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive: false when status = PENDING_VERIFICATION")
    void isActive_pendingVerification() {
        User user = User.builder().status(UserStatus.PENDING_VERIFICATION).build();
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive: false when status = SUSPENDED")
    void isActive_suspended() {
        User user = User.builder().status(UserStatus.SUSPENDED).build();
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive: false when status = LOCKED")
    void isActive_locked() {
        User user = User.builder().status(UserStatus.LOCKED).build();
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive: false when status = DEACTIVATED")
    void isActive_deactivated() {
        User user = User.builder().status(UserStatus.DEACTIVATED).build();
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("hasLocalPassword: true when passwordHash is present")
    void hasLocalPassword_withHash() {
        User user = User.builder().passwordHash("$2a$12$hash").build();
        assertThat(user.hasLocalPassword()).isTrue();
    }

    @Test
    @DisplayName("hasLocalPassword: false when passwordHash is null (SSO user)")
    void hasLocalPassword_nullPassword() {
        User user = User.builder().authProvider(AuthProvider.GOOGLE).build();
        assertThat(user.hasLocalPassword()).isFalse();
    }

    @Test
    @DisplayName("getFullName: concatenates firstName and lastName with a space")
    void getFullName() {
        User user = User.builder().firstName("Nguyen").lastName("Van A").build();
        assertThat(user.getFullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    @DisplayName("getFullName: handles empty names")
    void getFullName_emptyNames() {
        User user = User.builder().firstName("").lastName("").build();
        assertThat(user.getFullName()).isEqualTo("  ");
    }

    @Test
    @DisplayName("Status enum: has exactly 5 values")
    void status_values() {
        UserStatus[] values = UserStatus.values();
        assertThat(values).hasSize(5);
        assertThat(values).contains(
                UserStatus.PENDING_VERIFICATION,
                UserStatus.ACTIVE,
                UserStatus.SUSPENDED,
                UserStatus.LOCKED,
                UserStatus.DEACTIVATED
        );
    }

    @Test
    @DisplayName("AuthProvider enum: has exactly 6 values")
    void authProvider_values() {
        AuthProvider[] values = AuthProvider.values();
        assertThat(values).hasSize(6);
        assertThat(values).contains(
                AuthProvider.LOCAL,
                AuthProvider.GOOGLE,
                AuthProvider.AZURE_AD,
                AuthProvider.OKTA,
                AuthProvider.SAML,
                AuthProvider.LDAP
        );
    }

    @Test
    @DisplayName("Builder: default values match spec")
    void builder_defaultValues() {
        User user = User.builder()
                .email("test@acme.com")
                .firstName("Test")
                .lastName("User")
                .organizationId(java.util.UUID.randomUUID())
                .build();

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getRoles()).isNotNull().isEmpty();
    }
}
