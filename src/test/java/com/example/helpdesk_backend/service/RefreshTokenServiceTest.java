package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.entity.RefreshToken;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.exception.InvalidLoginException;
import com.example.helpdesk_backend.repository.RefreshTokenRepository;
import com.example.helpdesk_backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void issueTokenShouldPersistHashAndExpiration() {
        User user = user(1L);
        String refreshToken = "issued-refresh-token";
        String tokenId = "issued-jti";

        when(jwtService.generateRefreshToken(user)).thenReturn(refreshToken);
        when(jwtService.extractTokenId(refreshToken)).thenReturn(tokenId);
        when(jwtService.extractExpiration(refreshToken)).thenReturn(Instant.now().plusSeconds(3600));

        String result = refreshTokenService.issueToken(user);

        assertEquals(refreshToken, result);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotateShouldIssueNewTokenAndRevokeOldToken() {
        User user = user(1L);
        String oldToken = "old-refresh-token";
        String oldTokenId = "old-jti";
        String newToken = "new-refresh-token";
        String newTokenId = "new-jti";

        RefreshToken stored = new RefreshToken(
                oldTokenId,
                hash(oldToken),
                user,
                Instant.now().plusSeconds(3600)
        );

        when(jwtService.isRefreshToken(oldToken)).thenReturn(true);
        when(jwtService.extractUserId(oldToken)).thenReturn("1");
        when(jwtService.extractTokenId(oldToken)).thenReturn(oldTokenId);
        when(refreshTokenRepository.findByTokenId(oldTokenId)).thenReturn(Optional.of(stored));
        when(jwtService.generateRefreshToken(user)).thenReturn(newToken);
        when(jwtService.extractTokenId(newToken)).thenReturn(newTokenId);
        when(jwtService.extractExpiration(newToken)).thenReturn(Instant.now().plusSeconds(7200));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(oldToken);

        assertEquals(newToken, result.refreshToken());
        assertEquals(user, result.user());
        assertNotNull(stored.getRevokedAt());
        assertEquals(newTokenId, stored.getReplacedByTokenId());
        verify(refreshTokenRepository, never()).saveAll(any());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotateShouldRejectExpiredToken() {
        User user = user(2L);
        String expiredToken = "expired-refresh-token";
        String tokenId = "expired-jti";

        RefreshToken stored = new RefreshToken(
                tokenId,
                hash(expiredToken),
                user,
                Instant.now().minusSeconds(10)
        );

        when(jwtService.isRefreshToken(expiredToken)).thenReturn(true);
        when(jwtService.extractUserId(expiredToken)).thenReturn("2");
        when(jwtService.extractTokenId(expiredToken)).thenReturn(tokenId);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));

        assertThrows(InvalidLoginException.class, () -> refreshTokenService.rotate(expiredToken));
        assertNotNull(stored.getRevokedAt());
    }

    @Test
    void rotateShouldRevokeAllActiveTokensWhenReusedTokenIsDetected() {
        User user = user(7L);
        String reusedToken = "reused-token";
        String tokenId = "reused-jti";

        RefreshToken stored = new RefreshToken(
                tokenId,
                hash(reusedToken),
                user,
                Instant.now().plusSeconds(3600)
        );
        stored.setRevokedAt(Instant.now().minusSeconds(30));

        RefreshToken otherActiveToken = new RefreshToken(
                "other-jti",
                hash("other-token"),
                user,
                Instant.now().plusSeconds(3600)
        );

        when(jwtService.isRefreshToken(reusedToken)).thenReturn(true);
        when(jwtService.extractUserId(reusedToken)).thenReturn("7");
        when(jwtService.extractTokenId(reusedToken)).thenReturn(tokenId);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(7L)).thenReturn(List.of(otherActiveToken));

        assertThrows(InvalidLoginException.class, () -> refreshTokenService.rotate(reusedToken));
        verify(refreshTokenRepository).saveAll(any());
    }

    @Test
    void rotateShouldRejectTamperedTokenHash() {
        User user = user(3L);
        String realToken = "real-refresh-token";
        String tokenId = "tampered-jti";

        RefreshToken stored = new RefreshToken(
                tokenId,
                hash(realToken),
                user,
                Instant.now().plusSeconds(3600)
        );

        String tamperedToken = "different-token";

        when(jwtService.isRefreshToken(tamperedToken)).thenReturn(true);
        when(jwtService.extractUserId(tamperedToken)).thenReturn("3");
        when(jwtService.extractTokenId(tamperedToken)).thenReturn(tokenId);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(3L)).thenReturn(List.of());

        assertThrows(InvalidLoginException.class, () -> refreshTokenService.rotate(tamperedToken));
    }

    @Test
    void revokeShouldMarkTokenAsRevoked() {
        User user = user(4L);
        String token = "logout-token";
        String tokenId = "logout-jti";

        RefreshToken stored = new RefreshToken(
                tokenId,
                hash(token),
                user,
                Instant.now().plusSeconds(3600)
        );

        when(jwtService.isRefreshToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn("4");
        when(jwtService.extractTokenId(token)).thenReturn(tokenId);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(stored));

        refreshTokenService.revoke(token);

        assertNotNull(stored.getRevokedAt());
        verify(refreshTokenRepository).save(stored);
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(Role.CUSTOMER);
        user.setEmail("user" + id + "@example.com");
        user.setName("User");
        user.setPassword("hashed");
        return user;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}