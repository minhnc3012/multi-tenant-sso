package com.identityplatform.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService tests")
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private UUID userId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── requestPasswordReset ───────────────────────────────────

    @Test
    @DisplayName("requestPasswordReset: sends email when email exists")
    void requestPasswordReset_emailExists_sendsEmail() {
        User user = User.builder()
                .id(userId)
                .email("user@acme.com")
                .organizationId(orgId)
                .build();

        when(userRepository.findByEmailAndOrganizationIdAndDeletedFalse("user@acme.com", orgId))
                .thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        passwordResetService.requestPasswordReset("user@acme.com", orgId);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("requestPasswordReset: does not throw when email does not exist (anti-enumeration)")
    void requestPasswordReset_emailNotFound_returnsQuietly() {
        when(userRepository.findByEmailAndOrganizationIdAndDeletedFalse("nobody@acme.com", orgId))
                .thenReturn(Optional.empty());

        // Does not throw — anti-enumeration
        passwordResetService.requestPasswordReset("nobody@acme.com", orgId);

        verify(redisTemplate, never()).opsForValue();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("requestPasswordReset: Redis token has the prefix 'pwd_reset:'")
    void requestPasswordReset_tokenHasPrefix() {
        User user = User.builder()
                .id(userId)
                .email("user@acme.com")
                .organizationId(orgId)
                .build();

        when(userRepository.findByEmailAndOrganizationIdAndDeletedFalse("user@acme.com", orgId))
                .thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        passwordResetService.requestPasswordReset("user@acme.com", orgId);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), any(Duration.class));

        assertThat(keyCaptor.getValue()).startsWith("pwd_reset:");
    }

    // ── resetPassword ──────────────────────────────────────────

    @Test
    @DisplayName("resetPassword: success with a valid token")
    void resetPassword_success() {
        String token = "valid-reset-token";
        String newPassword = "newSecurePass123!";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd_reset:" + token)).thenReturn(userId.toString());
        when(passwordEncoder.encode(newPassword)).thenReturn("$2a$12$encoded");

        passwordResetService.resetPassword(token, newPassword);

        verify(passwordEncoder).encode(newPassword);
        verify(redisTemplate).delete("pwd_reset:" + token);
    }

    @Test
    @DisplayName("resetPassword: throws when token does not exist in Redis")
    void resetPassword_invalidToken_throwsException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd_reset:invalid")).thenReturn(null);

        assertThatThrownBy(() -> passwordResetService.resetPassword("invalid", "newPass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset token");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: new password is encoded and saved")
    void resetPassword_passwordIsEncodedAndSaved() {
        String token = "valid-token";
        String newPassword = "newSecurePass123!";
        String encoded = "$2a$12$encodedPassword";

        User user = User.builder()
                .id(userId)
                .organizationId(orgId)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd_reset:" + token)).thenReturn(userId.toString());
        when(passwordEncoder.encode(newPassword)).thenReturn(encoded);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        passwordResetService.resetPassword(token, newPassword);

        assertThat(user.getPasswordHash()).isEqualTo(encoded);
        verify(userRepository).save(user);
        verify(redisTemplate).delete("pwd_reset:" + token);
    }

    @Test
    @DisplayName("resetPassword: token is single-use — deleted after use")
    void resetPassword_tokenIsSingleUse() {
        String token = "single-use-token";
        String newPassword = "newSecurePass123!";
        String encoded = "$2a$12$encoded";

        User user = User.builder()
                .id(userId)
                .organizationId(orgId)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd_reset:" + token)).thenReturn(userId.toString());
        when(passwordEncoder.encode(newPassword)).thenReturn(encoded);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        passwordResetService.resetPassword(token, newPassword);

        verify(redisTemplate).delete("pwd_reset:" + token);

        // Token has been deleted — a second use will fail
        when(valueOperations.get("pwd_reset:" + token)).thenReturn(null);
        assertThatThrownBy(() -> passwordResetService.resetPassword(token, newPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset token");
    }

    @Test
    @DisplayName("resetPassword: throws when user is soft-deleted")
    void resetPassword_userDeleted_throwsException() {
        String token = "valid-token";
        String newPassword = "newSecurePass123!";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd_reset:" + token)).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(token, newPassword))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
