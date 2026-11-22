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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);
        Instant expiry = Instant.now().plus(tokenValidityDuration);

        PasswordResetToken resetToken = new PasswordResetToken(hashedToken, user, expiry);
        tokenRepository.save(resetToken);

        notificationService.sendPasswordResetNotification(user, rawToken);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new InvalidLoginException("Reset token is required");
        }

        String hashedToken = hashToken(token.trim());
        PasswordResetToken resetToken = tokenRepository.findByTokenAndUsedFalse(hashedToken)
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

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
