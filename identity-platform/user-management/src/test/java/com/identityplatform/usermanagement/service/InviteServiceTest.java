package com.identityplatform.usermanagement.service;

import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.domain.UserStatus;
import com.identityplatform.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteService tests")
class InviteServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private InviteService inviteService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("sendInvite: creates token, saves to DB, and sends email")
    void sendInvite_savesTokenAndSendsEmail() {
        User user = User.builder()
                .id(userId)
                .email("invitee@acme.com")
                .firstName("Nguyen")
                .lastName("Van A")
                .build();

        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inviteService.sendInvite(user);

        // sendInvite() calls save() exactly once (sets token then saves)
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getInviteToken()).isNotNull().isNotBlank();
        assertThat(savedUser.getInviteTokenExpiresAt())
                .isAfter(Instant.now().minusSeconds(1))
                .isBefore(Instant.now().plus(73, ChronoUnit.HOURS));

        // Verify email was sent — captor must be used INSIDE verify, not created after
        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());

        SimpleMailMessage sentMessage = mailCaptor.getValue();
        assertThat(sentMessage.getTo()[0]).isEqualTo("invitee@acme.com");
        assertThat(sentMessage.getSubject()).contains("invited");
        assertThat(sentMessage.getText()).contains("accept");
    }

    @Test
    @DisplayName("acceptInvite: success with a valid token")
    void acceptInvite_success() {
        User user = User.builder()
                .id(userId)
                .email("invitee@acme.com")
                .inviteToken("valid-token")
                .inviteTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        when(userRepository.findByInviteToken("valid-token")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = inviteService.acceptInvite("valid-token", "encodedPassword", "encodedPassword");

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.getPasswordHash()).isEqualTo("encodedPassword");
        assertThat(result.getInviteToken()).isNull();
        assertThat(result.getInviteTokenExpiresAt()).isNull();
        assertThat(result.getEmailVerifiedAt()).isNotNull();

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("acceptInvite: throws when token does not exist")
    void acceptInvite_invalidToken_throwsException() {
        when(userRepository.findByInviteToken("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.acceptInvite("invalid-token", "pass", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid invite token");
    }

    @Test
    @DisplayName("acceptInvite: throws when token has expired")
    void acceptInvite_expiredToken_throwsException() {
        User user = User.builder()
                .id(userId)
                .email("invitee@acme.com")
                .inviteToken("expired-token")
                .inviteTokenExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        when(userRepository.findByInviteToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> inviteService.acceptInvite("expired-token", "pass", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invite token has expired");
    }

    @Test
    @DisplayName("acceptInvite: token is single-use — token = null after accept")
    void acceptInvite_tokenIsSingleUse() {
        User user = User.builder()
                .id(userId)
                .email("invitee@acme.com")
                .inviteToken("once-token")
                .inviteTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        when(userRepository.findByInviteToken("once-token")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inviteService.acceptInvite("once-token", "pass", "pass");

        assertThat(user.getInviteToken()).isNull();
    }

    @Test
    @DisplayName("sendInvite: mail send failure does not crash the app")
    void sendInvite_mailFailure_doesNotCrash() {
        User user = User.builder()
                .id(userId)
                .email("test@acme.com")
                .build();

        when(userRepository.save(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(userId);
            return saved;
        });

        doThrow(new RuntimeException("Mail server down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Does not throw — mail error is caught inside the service
        inviteService.sendInvite(user);

        // Token is still saved
        verify(userRepository).save(any());
    }
}
