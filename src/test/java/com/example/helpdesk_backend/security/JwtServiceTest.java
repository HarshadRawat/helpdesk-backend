package com.example.helpdesk_backend.security;

import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void shouldCreateAndParseAccessToken() {
        JwtService jwtService = new JwtService("0123456789abcdef0123456789abcdef", 3600000L, 604800000L);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPassword("secret");
        user.setRole(Role.ADMIN);

        String token = jwtService.generateAccessToken(user);

        assertNotNull(token);
        assertEquals("ADMIN", jwtService.extractRole(token));
        assertEquals("ACCESS", jwtService.extractTokenType(token));
        assertEquals("42", jwtService.extractUserId(token));
    }

    @Test
    void shouldRejectShortJwtSecret() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService("short-secret", 3600000L, 604800000L)
        );

        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }
}
