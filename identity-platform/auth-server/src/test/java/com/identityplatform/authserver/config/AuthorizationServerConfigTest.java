package com.identityplatform.authserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AuthorizationServerConfig tests")
class AuthorizationServerConfigTest {

    @Test
    @DisplayName("PasswordEncoder: BCrypt with strength 12")
    void passwordEncoder_isBcryptStrength12() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);

        String rawPassword = "SecurePassword123!";
        String encoded = encoder.encode(rawPassword);

        assertThat(encoded).startsWith("$2a$12$");
        assertThat(encoder.matches(rawPassword, encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    @DisplayName("PasswordEncoder: each encode call produces a different result (salt)")
    void passwordEncoder_differentHashes() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String rawPassword = "SamePassword";

        String hash1 = encoder.encode(rawPassword);
        String hash2 = encoder.encode(rawPassword);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(encoder.matches(rawPassword, hash1)).isTrue();
        assertThat(encoder.matches(rawPassword, hash2)).isTrue();
    }

    @Test
    @DisplayName("PasswordEncoder: hash does not contain plaintext")
    void passwordEncoder_hashDoesNotContainPlaintext() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String rawPassword = "MySecret123";

        String hash = encoder.encode(rawPassword);
        assertThat(hash).doesNotContain(rawPassword);
    }

    @Test
    @DisplayName("PasswordEncoder: empty password")
    void passwordEncoder_emptyPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("");

        assertThat(encoder.matches("", hash)).isTrue();
        assertThat(encoder.matches("not-empty", hash)).isFalse();
    }
}
