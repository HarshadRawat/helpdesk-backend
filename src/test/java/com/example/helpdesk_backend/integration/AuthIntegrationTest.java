package com.example.helpdesk_backend.integration;

import com.example.helpdesk_backend.dto.LoginRequest;
import com.example.helpdesk_backend.dto.RefreshTokenRequest;
import com.example.helpdesk_backend.dto.UserRequest;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.repository.PasswordResetTokenRepository;
import com.example.helpdesk_backend.repository.RefreshTokenRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.helpdesk_backend.repository.TicketRepository ticketRepository;

    @Autowired
    private com.example.helpdesk_backend.repository.CommentRepository commentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    void cleanUp() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testPublicRegistrationAlwaysAssignsCustomerRoleEvenIfAdminRequested() throws Exception {
        UserRequest registerReq = new UserRequest();
        registerReq.setName("Attacker");
        registerReq.setEmail("attacker@test.com");
        registerReq.setPassword("Password123!");
        registerReq.setRole(Role.ADMIN); // Attempt privilege escalation on public endpoint

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        User saved = userRepository.findByEmail("attacker@test.com").orElseThrow();
        assertEquals(Role.CUSTOMER, saved.getRole());
    }

    @Test
    void testRegisterLoginAndRefreshFlowWithReuseRevocationCommit() throws Exception {
        UserRequest registerReq = new UserRequest();
        registerReq.setName("Dev User");
        registerReq.setEmail("DEV@test.com"); // test email normalization
        registerReq.setPassword("Password123!");
        registerReq.setRole(Role.CUSTOMER);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@test.com"));

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("dev@test.com");
        loginReq.setPassword("Password123!");

        String loginResponseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(loginResponseStr);
        String refreshToken = jsonNode.get("refreshToken").asText();

        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken(refreshToken);

        // First rotation: valid old token -> generates new token
        String rotatedResponseStr = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode rotatedNode = objectMapper.readTree(rotatedResponseStr);
        String newRefreshToken = rotatedNode.get("refreshToken").asText();

        // Thief Trap: Reusing the already rotated token must return 401
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());

        // Verifying reuse revocation commit (noRollbackFor):
        // All active tokens for the user must now be revoked in DB!
        User user = userRepository.findByEmail("dev@test.com").orElseThrow();
        assertTrue(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId()).isEmpty(),
                "All refresh tokens for user must be revoked upon reuse detection without rollback");

        // The newly issued token should now ALSO be rejected
        RefreshTokenRequest newRefreshReq = new RefreshTokenRequest();
        newRefreshReq.setRefreshToken(newRefreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newRefreshReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testInvalidLoginReturnsUnauthorized() throws Exception {
        LoginRequest badLogin = new LoginRequest();
        badLogin.setEmail("nonexistent@test.com");
        badLogin.setPassword("WrongPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized());
    }
}
