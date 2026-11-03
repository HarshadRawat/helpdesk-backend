package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.entity.RefreshToken;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.InvalidLoginException;
import com.example.helpdesk_backend.repository.RefreshTokenRepository;
import com.example.helpdesk_backend.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public String issueToken(User user) {
        String refreshToken = jwtService.generateRefreshToken(user);
        saveRefreshToken(user, refreshToken);
        return refreshToken;
    }

    @Transactional
    public RotationResult rotate(String refreshTokenValue) {
        RefreshToken storedToken = findAndValidateStoredToken(refreshTokenValue);

        if (storedToken.isRevoked()) {
            revokeAllActiveTokensForUser(storedToken.getUser().getId());
            throw new InvalidLoginException("Refresh token reuse detected. Please login again.");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            revokeToken(storedToken, null);
            throw new InvalidLoginException("Refresh token expired or revoked");
        }

        String newRefreshToken = issueToken(storedToken.getUser());
        String newTokenId = jwtService.extractTokenId(newRefreshToken);
        revokeToken(storedToken, newTokenId);

        return new RotationResult(storedToken.getUser(), newRefreshToken);
    }

    @Transactional
    public void revoke(String refreshTokenValue) {
        RefreshToken storedToken = findAndValidateStoredToken(refreshTokenValue);
        if (!storedToken.isRevoked()) {
            revokeToken(storedToken, null);
        }
    }

    @Transactional
    public void revokeAllActiveTokensForUser(User user) {
        revokeAllActiveTokensForUser(user.getId());
    }

    private RefreshToken findAndValidateStoredToken(String refreshTokenValue) {
        TokenClaims claims = parseRefreshToken(refreshTokenValue);
        String tokenHash = hashToken(refreshTokenValue);
        RefreshToken storedToken = refreshTokenRepository.findByTokenId(claims.tokenId())
                .or(() -> refreshTokenRepository.findByTokenHash(tokenHash))
                .orElseThrow(() -> new InvalidLoginException("Refresh token expired or revoked"));

        if (!storedToken.getUser().getId().equals(claims.userId())) {
            revokeAllActiveTokensForUser(claims.userId());
            throw new InvalidLoginException("Refresh token reuse detected. Please login again.");
        }

        if (!secureEquals(storedToken.getTokenHash(), tokenHash)) {
            revokeAllActiveTokensForUser(claims.userId());
            throw new InvalidLoginException("Refresh token reuse detected. Please login again.");
        }

        return storedToken;
    }

    private TokenClaims parseRefreshToken(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new InvalidLoginException("Refresh token missing");
        }

        try {
            if (!jwtService.isRefreshToken(refreshTokenValue)) {
                throw new InvalidLoginException("Invalid refresh token");
            }

            Long userId = Long.valueOf(jwtService.extractUserId(refreshTokenValue));
            String tokenId = jwtService.extractTokenId(refreshTokenValue);
            if (tokenId == null || tokenId.isBlank()) {
                throw new InvalidLoginException("Invalid refresh token");
            }
            return new TokenClaims(userId, tokenId);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidLoginException("Invalid refresh token");
        }
    }

    private void saveRefreshToken(User user, String refreshTokenValue) {
        RefreshToken refreshToken = new RefreshToken(
                jwtService.extractTokenId(refreshTokenValue),
                hashToken(refreshTokenValue),
                user,
                jwtService.extractExpiration(refreshTokenValue)
        );
        refreshTokenRepository.save(refreshToken);
    }

    private void revokeToken(RefreshToken token, String replacedByTokenId) {
        token.setRevokedAt(Instant.now());
        token.setReplacedByTokenId(replacedByTokenId);
        refreshTokenRepository.save(token);
    }

    private void revokeAllActiveTokensForUser(Long userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        if (activeTokens.isEmpty()) {
            return;
        }

        Instant revokedAt = Instant.now();
        activeTokens.forEach(token -> {
            token.setRevokedAt(revokedAt);
            if (token.getReplacedByTokenId() == null) {
                token.setReplacedByTokenId("REUSE_DETECTED");
            }
        });
        refreshTokenRepository.saveAll(activeTokens);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private record TokenClaims(Long userId, String tokenId) {
    }

    public record RotationResult(User user, String refreshToken) {
    }
}