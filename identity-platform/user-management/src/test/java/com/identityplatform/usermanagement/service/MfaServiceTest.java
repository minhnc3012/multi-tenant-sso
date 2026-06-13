package com.identityplatform.usermanagement.service;

import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MfaService tests")
class MfaServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MfaService mfaService;

    @Test
    @DisplayName("generateMfaSecret: returns a valid secret and QR URI")
    void generateMfaSecret_returnsValidResult() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("user@company.com")
                .firstName("Nguyen")
                .lastName("Van A")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        MfaService.MfaSetupResult result = mfaService.generateMfaSecret(userId, "TestApp");

        assertThat(result.rawSecret()).isNotBlank();
        assertThat(result.base32Secret()).isNotBlank();
        assertThat(result.otpAuthUri()).startsWith("otpauth://totp/");
        assertThat(result.otpAuthUri()).contains("user@company.com");
        assertThat(result.otpAuthUri()).contains("TestApp");
    }

    @Test
    @DisplayName("enableMfa: returns false when TOTP code is wrong")
    void enableMfa_withWrongCode_returnsFalse() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("user@company.com")
                .firstName("Test")
                .lastName("User")
                .mfaSecret("dGVzdHNlY3JldGtleWZvcnRlc3Rpbmc=")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        boolean result = mfaService.enableMfa(userId, "000000");

        assertThat(result).isFalse();
        assertThat(user.isMfaEnabled()).isFalse();
    }

    @Test
    @DisplayName("enableMfa: throws exception when secret has not been generated yet")
    void enableMfa_withoutSecret_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("user@company.com")
                .firstName("Test")
                .lastName("User")
                .build(); // mfaSecret = null

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> mfaService.enableMfa(userId, "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generateMfaSecret");
    }
}
