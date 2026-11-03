package com.example.helpdesk_backend.security;

import com.example.helpdesk_backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final String secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secretKey,
            @Value("${app.jwt.access-expiration-ms:3600000}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        if (secretKey == null || secretKey.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be configured and at least 32 characters long");
        }
        this.secretKey = secretKey;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return generateToken(user, accessTokenExpirationMs, "ACCESS");
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, refreshTokenExpirationMs, "REFRESH");
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String extractTokenId(String token) {
        return parseClaims(token).get("jti", String.class);
    }

    public String extractTokenType(String token) {
        return parseClaims(token).get("tokenType", String.class);
    }

    public Instant extractExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private String generateToken(User user, long expirationMs, String tokenType) {
        Date now = new Date();
        Date expiry = new Date(System.currentTimeMillis() + expirationMs);
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(user.getId().toString())
                .id(tokenId)
                .claim("role", user.getRole().name())
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(String token) {
        return "REFRESH".equals(parseClaims(token).get("tokenType", String.class));
    }
}