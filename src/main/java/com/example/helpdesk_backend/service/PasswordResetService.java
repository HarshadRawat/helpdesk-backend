package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.entity.PasswordResetToken;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.InvalidLoginException;
import com.example.helpdesk_backend.repository.PasswordResetTokenRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final Duration tokenValidityDuration;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            @Value("${app.auth.password-reset-validity-minutes:15}") long validityMinutes) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.tokenValidityDuration = Duration.ofMinutes(validityMinutes);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        Optional<User> userOptional = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOptional.isEmpty()) {
            // Silent return prevents email enumeration
            return;
        }

        User user = userOptional.get();
        tokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(tokenValidityDuration);

        PasswordResetToken resetToken = new PasswordResetToken(token, user, expiry);
        tokenRepository.save(resetToken);

        notificationService.sendPasswordResetNotification(user, token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new InvalidLoginException("Reset token is required");
        }

        PasswordResetToken resetToken = tokenRepository.findByTokenAndUsedFalse(token.trim())
                .orElseThrow(() -> new InvalidLoginException("Invalid or expired password reset token"));

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            resetToken.setUsed(true);
            tokenRepository.save(resetToken);
            throw new InvalidLoginException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke active refresh tokens to force re-login across all devices
        refreshTokenService.revokeAllActiveTokensForUser(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }
}
