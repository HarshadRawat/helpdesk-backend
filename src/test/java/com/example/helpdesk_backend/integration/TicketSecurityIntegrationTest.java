package com.example.helpdesk_backend.integration;

import com.example.helpdesk_backend.dto.AssignTicketRequest;
import com.example.helpdesk_backend.dto.CommentRequest;
import com.example.helpdesk_backend.dto.StatusUpdateRequest;
import com.example.helpdesk_backend.dto.TicketRequest;
import com.example.helpdesk_backend.dto.TicketResponse;
import com.example.helpdesk_backend.dto.TicketUpdateRequest;
import com.example.helpdesk_backend.dto.UserResponse;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.repository.CommentRepository;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import com.example.helpdesk_backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private com.example.helpdesk_backend.repository.TicketAttachmentRepository attachmentRepository;

    @Autowired
    private com.example.helpdesk_backend.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.example.helpdesk_backend.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User customerA;
    private User customerB;
    private User agent;

    private String adminToken;
    private String customerAToken;
    private String customerBToken;
    private String agentToken;

    @BeforeEach
    void setUpData() {
        attachmentRepository.deleteAll();
        commentRepository.deleteAll();
        ticketRepository.deleteAllHard();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();

        admin = createUser("Admin", "admin@domain.com", Role.ADMIN);
        customerA = createUser("Customer A", "customerA@domain.com", Role.CUSTOMER);
        customerB = createUser("Customer B", "customerB@domain.com", Role.CUSTOMER);
        agent = createUser("Support Agent", "agent@domain.com", Role.AGENT);

        adminToken = "Bearer " + jwtService.generateAccessToken(admin);
        customerAToken = "Bearer " + jwtService.generateAccessToken(customerA);
        customerBToken = "Bearer " + jwtService.generateAccessToken(customerB);
        agentToken = "Bearer " + jwtService.generateAccessToken(agent);
    }

    private User createUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setRole(role);
        return userRepository.save(user);
    }

    @Test
    void testCustomerCannotAccessOtherCustomerTicket() throws Exception {
        TicketRequest ticketReq = new TicketRequest();
        ticketReq.setTitle("Printer issue");
        ticketReq.setDescription("Cannot print documents");
        ticketReq.setPriority(Priority.MEDIUM);

        String createRes = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        TicketResponse createdTicket = objectMapper.readValue(createRes, TicketResponse.class);
        Long ticketId = createdTicket.getId();

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", customerAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", customerBToken))
                .andExpect(status().isForbidden());

        TicketUpdateRequest updateReq = new TicketUpdateRequest();
        updateReq.setTitle("Malicious Title Update");
        updateReq.setDescription("Customer B trying to update Customer A's ticket");
        updateReq.setPriority(Priority.HIGH);

        mockMvc.perform(put("/api/tickets/" + ticketId)
                        .header("Authorization", customerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAssignmentAndStatusWorkflowWithRbac() throws Exception {
        TicketRequest ticketReq = new TicketRequest();
        ticketReq.setTitle("Server latency");
        ticketReq.setDescription("Server latency is spiking");
        ticketReq.setPriority(Priority.HIGH);

        String createRes = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TicketResponse createdTicket = objectMapper.readValue(createRes, TicketResponse.class);
        Long ticketId = createdTicket.getId();

        AssignTicketRequest assignReq = new AssignTicketRequest();
        assignReq.setAgentId(agent.getId());

        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(agent.getId()));

        StatusUpdateRequest invalidJump = new StatusUpdateRequest();
        invalidJump.setStatus(Status.RESOLVED);

        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidJump)))
                .andExpect(status().isBadRequest());

        StatusUpdateRequest validProgress = new StatusUpdateRequest();
        validProgress.setStatus(Status.IN_PROGRESS);

        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProgress)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void testCommentPermissionsAndAdminSummary() throws Exception {
        TicketRequest ticketReq = new TicketRequest();
        ticketReq.setTitle("Email delivery failing");
        ticketReq.setDescription("Outbound SMTP errors");
        ticketReq.setPriority(Priority.LOW);

        String createRes = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TicketResponse createdTicket = objectMapper.readValue(createRes, TicketResponse.class);
        Long ticketId = createdTicket.getId();

        CommentRequest commentReq = new CommentRequest();
        commentReq.setMessage("Adding extra diagnostic log details");

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Adding extra diagnostic log details"));

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", customerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isForbidden());

        // Admin warms the cache first:
        mockMvc.perform(get("/api/tickets/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").isNumber());

        // Cache hit must NOT bypass security for customer:
        mockMvc.perform(get("/api/tickets/summary")
                        .header("Authorization", customerAToken))
                .andExpect(status().isForbidden());

        // Audit trail is restricted to admins:
        mockMvc.perform(get("/api/audit/tickets/" + ticketId)
                        .header("Authorization", customerAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit/tickets/" + ticketId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testOptimisticLockingConflictWhenVersionMismatches() throws Exception {
        TicketRequest ticketReq = new TicketRequest();
        ticketReq.setTitle("Concurrency issue test");
        ticketReq.setDescription("Testing Alice and Bob version conflict");
        ticketReq.setPriority(Priority.HIGH);

        String createRes = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TicketResponse created = objectMapper.readValue(createRes, TicketResponse.class);
        Long ticketId = created.getId();

        // Stale update sending an outdated version (e.g. version 99 when ticket is at version 0)
        TicketUpdateRequest staleUpdate = new TicketUpdateRequest();
        staleUpdate.setTitle("Updated by Alice");
        staleUpdate.setDescription("Alice saved first");
        staleUpdate.setPriority(Priority.HIGH);
        staleUpdate.setVersion(99L);

        mockMvc.perform(put("/api/tickets/" + ticketId)
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleUpdate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void testCascadeDeleteTicketWithComments() throws Exception {
        TicketRequest ticketReq = new TicketRequest();
        ticketReq.setTitle("Ticket to delete");
        ticketReq.setDescription("Should cascade comments on delete");
        ticketReq.setPriority(Priority.LOW);

        String createRes = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TicketResponse created = objectMapper.readValue(createRes, TicketResponse.class);
        Long ticketId = created.getId();

        CommentRequest commentReq = new CommentRequest();
        commentReq.setMessage("Diagnostic comment attached to ticket");

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isOk());

        // Deleting the ticket must cascade and succeed with 200 OK (no 500 foreign key failure)
        mockMvc.perform(delete("/api/tickets/" + ticketId)
                        .header("Authorization", customerAToken))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertFalse(ticketRepository.existsById(ticketId));
        org.junit.jupiter.api.Assertions.assertTrue(commentRepository.findByTicketId(ticketId).isEmpty());
    }

    @Test
    void testUrgentPrioritySlaAndTimestamps() throws Exception {
        TicketRequest ticketReq = new TicketRequest();
        ticketReq.setTitle("Urgent production outage");
        ticketReq.setDescription("Database down in US-East");
        ticketReq.setPriority(Priority.URGENT);

        String createRes = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        TicketResponse created = objectMapper.readValue(createRes, TicketResponse.class);
        Long ticketId = created.getId();

        // Urgent SLA due must be within 4 hours
        java.time.Instant now = java.time.Instant.now();
        org.junit.jupiter.api.Assertions.assertTrue(created.getSlaDueAt().isAfter(now.plus(java.time.Duration.ofHours(3))));
        org.junit.jupiter.api.Assertions.assertTrue(created.getSlaDueAt().isBefore(now.plus(java.time.Duration.ofHours(5))));

        // Admin assigns to agent:
        AssignTicketRequest assignReq = new AssignTicketRequest();
        assignReq.setAgentId(agent.getId());
        assignReq.setVersion(created.getVersion());
        String assignRes = mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TicketResponse assignedTicket = objectMapper.readValue(assignRes, TicketResponse.class);

        // Assigned agent moves to IN_PROGRESS:
        StatusUpdateRequest inProgress = new StatusUpdateRequest();
        inProgress.setStatus(Status.IN_PROGRESS);
        inProgress.setVersion(assignedTicket.getVersion());
        String inProgressRes = mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inProgress)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TicketResponse progressTicket = objectMapper.readValue(inProgressRes, TicketResponse.class);

        // Move to RESOLVED
        StatusUpdateRequest resolveReq = new StatusUpdateRequest();
        resolveReq.setStatus(Status.RESOLVED);
        resolveReq.setVersion(progressTicket.getVersion());
        String resolveRes = mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        TicketResponse resolvedTicket = objectMapper.readValue(resolveRes, TicketResponse.class);

        // Reopen ticket from RESOLVED -> OPEN
        StatusUpdateRequest reopenReq = new StatusUpdateRequest();
        reopenReq.setStatus(Status.OPEN);
        reopenReq.setVersion(resolvedTicket.getVersion());
        mockMvc.perform(put("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reopenReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty());
    }

    @Test
    void testInternalCommentNotesScoping() throws Exception {
        TicketRequest ticketReq = new TicketRequest();
        ticketReq.setTitle("Internal comments scoping test");
        ticketReq.setDescription("Testing agent internal notes vs customer view");
        ticketReq.setPriority(Priority.MEDIUM);

        String createRes = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TicketResponse created = objectMapper.readValue(createRes, TicketResponse.class);
        Long ticketId = created.getId();

        // Assign to agent so agent can act
        AssignTicketRequest assignReq = new AssignTicketRequest();
        assignReq.setAgentId(agent.getId());
        assignReq.setVersion(created.getVersion());
        mockMvc.perform(put("/api/tickets/" + ticketId + "/assign")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk());

        // Customer posts public comment:
        CommentRequest publicComment = new CommentRequest();
        publicComment.setMessage("Customer public comment");
        publicComment.setIsInternal(false);
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publicComment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internal").value(false));

        // Customer attempts to post internal note -> forbidden:
        CommentRequest customerInternalNote = new CommentRequest();
        customerInternalNote.setMessage("Customer sneaky internal note");
        customerInternalNote.setIsInternal(true);
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerInternalNote)))
                .andExpect(status().isForbidden());

        // Agent posts internal note:
        CommentRequest agentInternalNote = new CommentRequest();
        agentInternalNote.setMessage("Agent diagnosis: internal hardware fault");
        agentInternalNote.setIsInternal(true);
        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agentInternalNote)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internal").value(true));

        // Agent fetches comments: sees both public and internal notes (2 total)
        mockMvc.perform(get("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Customer fetches comments: sees only public comment (1 total, internal filtered out)
        mockMvc.perform(get("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("Customer public comment"));
    }

    @Test
    void testUserDeactivationPreventsLogin() throws Exception {
        // Register a new customer
        com.example.helpdesk_backend.dto.UserRequest newCustomer = new com.example.helpdesk_backend.dto.UserRequest();
        newCustomer.setName("Deactivation Target");
        newCustomer.setEmail("target@helpdesk.com");
        newCustomer.setPassword("ValidPassword123!");

        String userRes = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCustomer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UserResponse userResponse = objectMapper.readValue(userRes, UserResponse.class);
        org.junit.jupiter.api.Assertions.assertTrue(userResponse.isActive());

        // Verify login works initially
        com.example.helpdesk_backend.dto.LoginRequest loginReq = new com.example.helpdesk_backend.dto.LoginRequest();
        loginReq.setEmail("target@helpdesk.com");
        loginReq.setPassword("ValidPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk());

        // Admin deactivates user
        mockMvc.perform(delete("/api/users/" + userResponse.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        // Login now fails because account is deactivated
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User account is deactivated"));
    }
}
