package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.entity.PasswordResetToken;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.InvalidLoginException;
import com.example.helpdesk_backend.repository.PasswordResetTokenRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationService notificationService;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                tokenRepository,
                userRepository,
                refreshTokenService,
                passwordEncoder,
                notificationService,
                15L
        );
    }

    @Test
    void requestPasswordResetShouldGenerateTokenForExistingUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setName("Alice");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset("user@example.com");

        verify(tokenRepository).deleteByUser(user);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(notificationService).sendPasswordResetNotification(eq(user), anyString());
    }

    @Test
    void requestPasswordResetShouldSilentlyIgnoreMissingEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("missing@example.com");

        verify(tokenRepository, never()).save(any());
        verify(notificationService, never()).sendPasswordResetNotification(any(), any());
    }

    @Test
    void resetPasswordShouldUpdatePasswordAndRevokeActiveTokens() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("old-encoded-password");

        PasswordResetToken resetToken = new PasswordResetToken(
                "valid-token",
                user,
                Instant.now().plus(Duration.ofMinutes(10))
        );

        when(tokenRepository.findByTokenAndUsedFalse("valid-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPassword123!")).thenReturn("new-encoded-password");

        passwordResetService.resetPassword("valid-token", "newPassword123!");

        verify(passwordEncoder).encode("newPassword123!");
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllActiveTokensForUser(user);
        assertTrue(resetToken.isUsed());
        verify(tokenRepository).save(resetToken);
    }

    @Test
    void resetPasswordShouldRejectExpiredToken() {
        User user = new User();
        PasswordResetToken expiredToken = new PasswordResetToken(
                "expired-token",
                user,
                Instant.now().minus(Duration.ofMinutes(5))
        );

        when(tokenRepository.findByTokenAndUsedFalse("expired-token")).thenReturn(Optional.of(expiredToken));

        assertThrows(InvalidLoginException.class, () -> passwordResetService.resetPassword("expired-token", "newPassword123!"));
        assertTrue(expiredToken.isUsed());
        verify(tokenRepository).save(expiredToken);
        verify(userRepository, never()).save(any());
    }
}
