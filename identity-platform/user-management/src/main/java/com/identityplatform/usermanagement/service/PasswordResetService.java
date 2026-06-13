package com.identityplatform.usermanagement.service;

import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Password reset flow (Forgot Password).
 *
 * Uses Redis to store the reset token (TTL 1 hour),
 * avoiding DB storage to prevent table bloat.
 *
 * Flow:
 * 1. User enters email → requestPasswordReset() → sends email with token
 * 2. User clicks link in email → resetPassword() with the token
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String RESET_TOKEN_PREFIX = "pwd_reset:";
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public void requestPasswordReset(String email, UUID organizationId) {
        // Always return success regardless of whether the email exists
        // to prevent user enumeration attacks
        userRepository.findByEmailAndOrganizationIdAndDeletedFalse(email, organizationId)
                .ifPresent(user -> {
                    String token = UUID.randomUUID().toString();
                    String redisKey = RESET_TOKEN_PREFIX + token;

                    // Store userId in Redis with a 1-hour TTL
                    redisTemplate.opsForValue().set(
                            redisKey,
                            user.getId().toString(),
                            TOKEN_TTL
                    );

                    sendResetEmail(email, token);
                    log.info("Password reset requested for user: {}", user.getId());
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String redisKey = RESET_TOKEN_PREFIX + token;
        String userIdStr = redisTemplate.opsForValue().get(redisKey);

        if (userIdStr == null) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Token is single-use: delete immediately after use
        redisTemplate.delete(redisKey);

        log.info("Password reset successful for user: {}", userId);
    }

    private void sendResetEmail(String email, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Reset your password");
            message.setText(
                    "Click the link to reset your password:\n"
                    + "https://yourplatform.com/reset-password?token=" + token
                    + "\n\nThis link expires in 1 hour."
                    + "\n\nIf you didn't request this, please ignore this email."
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", email, e.getMessage());
        }
    }
}
