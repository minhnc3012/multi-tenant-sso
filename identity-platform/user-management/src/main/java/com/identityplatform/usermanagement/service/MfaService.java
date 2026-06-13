package com.identityplatform.usermanagement.service;

import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * TOTP (Time-based One-Time Password) MFA Service.
 * Compatible with Google Authenticator, Authy, and Microsoft Authenticator.
 *
 * No external library required — implements RFC 6238 directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final int SECRET_BYTES = 20;
    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_WINDOW = 1; // ±1 time step (30s) to compensate for clock skew
    private static final long TIME_STEP_SECONDS = 30;

    private final UserRepository userRepository;

    /**
     * Step 1: Generate a secret key for the user.
     * Returns the secret + QR code URI for the user to scan with an authenticator app.
     */
    @Transactional
    public MfaSetupResult generateMfaSecret(UUID userId, String issuer) {
        User user = findUser(userId);

        byte[] secretBytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secretBytes);
        String secret = Base64.getEncoder().encodeToString(secretBytes);

        // Temporarily save the secret (not yet enabled; waiting for user verification)
        user.setMfaSecret(secret);
        userRepository.save(user);

        // URI format for QR code: otpauth://totp/{issuer}:{email}?secret={secret}&issuer={issuer}
        String otpAuthUri = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                issuer, user.getEmail(),
                toBase32(secretBytes),
                issuer, TOTP_DIGITS, TIME_STEP_SECONDS
        );

        return new MfaSetupResult(secret, otpAuthUri, toBase32(secretBytes));
    }

    /**
     * Step 2: User enters the code from the app to confirm setup.
     * If correct, enables MFA for the account.
     */
    @Transactional
    public boolean enableMfa(UUID userId, String totpCode) {
        User user = findUser(userId);

        if (user.getMfaSecret() == null) {
            throw new IllegalStateException("MFA secret not generated yet. Call generateMfaSecret first.");
        }

        if (verifyTotp(user.getMfaSecret(), totpCode)) {
            user.setMfaEnabled(true);
            userRepository.save(user);
            log.info("MFA enabled for user: {}", userId);
            return true;
        }

        return false;
    }

    /**
     * Verifies the TOTP code when a user logs in.
     */
    @Transactional(readOnly = true)
    public boolean verifyMfaCode(UUID userId, String totpCode) {
        User user = findUser(userId);

        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            throw new IllegalStateException("MFA not enabled for this user");
        }

        return verifyTotp(user.getMfaSecret(), totpCode);
    }

    @Transactional
    public void disableMfa(UUID userId, String totpCode) {
        User user = findUser(userId);

        if (!verifyTotp(user.getMfaSecret(), totpCode)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        log.info("MFA disabled for user: {}", userId);
    }

    // ── TOTP implementation (RFC 6238) ─────────────────────────────────────

    private boolean verifyTotp(String secret, String code) {
        if (code == null || code.length() != TOTP_DIGITS) return false;

        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;

        // Check current step ± window to compensate for clock skew
        for (int i = -TOTP_WINDOW; i <= TOTP_WINDOW; i++) {
            String expected = generateTotp(secret, currentStep + i);
            if (expected.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String generateTotp(String secret, long timeStep) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secret);
            byte[] data = longToBytes(timeStep);

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            // Dynamic truncation
            int offset = hash[hash.length - 1] & 0x0F;
            int truncated = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = truncated % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP", e);
        }
    }

    private byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }

    /**
     * Base32 encoding for QR code URI (RFC 4648).
     * Google Authenticator uses Base32, not Base64.
     */
    private String toBase32(byte[] bytes) {
        final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0, bitsLeft = 0;

        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public record MfaSetupResult(
            String rawSecret,
            String otpAuthUri,
            String base32Secret  // Displayed to the user for manual entry if they cannot scan the QR code
    ) {}
}
