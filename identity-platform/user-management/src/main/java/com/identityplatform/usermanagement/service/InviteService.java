package com.identityplatform.usermanagement.service;

import com.identityplatform.usermanagement.domain.User;
import com.identityplatform.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Transactional
    public void sendInvite(User user) {
        String token = UUID.randomUUID().toString();

        user.setInviteToken(token);
        user.setInviteTokenExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
        userRepository.save(user);

        sendInviteEmail(user.getEmail(), token);
    }

    @Transactional
    public User acceptInvite(String token, String password, String encodedPassword) {
        User user = userRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

        if (Instant.now().isAfter(user.getInviteTokenExpiresAt())) {
            throw new IllegalArgumentException("Invite token has expired");
        }

        user.setPasswordHash(encodedPassword);
        user.setInviteToken(null);
        user.setInviteTokenExpiresAt(null);
        user.setEmailVerifiedAt(Instant.now());
        user.setStatus(com.identityplatform.usermanagement.domain.UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    private void sendInviteEmail(String email, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("You've been invited to join");
            message.setText("Click the link to accept your invitation: "
                    + "https://yourplatform.com/invite/accept?token=" + token
                    + "\n\nThis link expires in 72 hours.");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send invite email to {}: {}", email, e.getMessage());
        }
    }
}
