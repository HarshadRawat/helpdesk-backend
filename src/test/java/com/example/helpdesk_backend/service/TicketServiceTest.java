package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.AssignTicketRequest;
import com.example.helpdesk_backend.dto.StatusUpdateRequest;
import com.example.helpdesk_backend.dto.TicketRequest;
import com.example.helpdesk_backend.dto.TicketResponse;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.event.TicketAssignedEvent;
import com.example.helpdesk_backend.event.TicketStatusChangedEvent;
import com.example.helpdesk_backend.exception.InvalidTicketAssignmentException;
import com.example.helpdesk_backend.exception.InvalidTicketStatusException;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private TicketService ticketService;

    @BeforeEach
    void setupSecurity() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "1",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTicketShouldCalculateSlaAndSave() {
        User creator = new User();
        ReflectionTestUtils.setField(creator, "id", 1L);
        creator.setName("Admin");
        creator.setEmail("admin@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 55L);
            return t;
        });

        TicketRequest request = new TicketRequest();
        request.setTitle("VPN not connecting");
        request.setDescription("Cannot connect to corporate VPN");
        request.setPriority(Priority.HIGH);

        Ticket created = ticketService.createTicket(request);

        assertNotNull(created);
        assertEquals("VPN not connecting", created.getTitle());
        assertEquals(Status.OPEN, created.getStatus());
        assertNotNull(created.getSlaDueAt());
        assertFalse(created.isSlaBreached());
        verify(ticketRepository).save(any(Ticket.class));
        verify(auditLogService).log(eq("CREATE"), eq("ticket"), eq(55L), eq(1L), any(), any());
    }

    @Test
    void updateTicketStatusShouldPublishEvent() {
        User creator = new User();
        ReflectionTestUtils.setField(creator, "id", 1L);

        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 10L);
        ticket.setTitle("Sample");
        ticket.setStatus(Status.OPEN);
        ticket.setCreatedBy(creator);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus(Status.IN_PROGRESS);

        Ticket updated = ticketService.updateTicketStatus(10L, request);

        assertEquals(Status.IN_PROGRESS, updated.getStatus());
        verify(eventPublisher).publishEvent(any(TicketStatusChangedEvent.class));
        verify(auditLogService).log(eq("STATUS_CHANGE"), eq("ticket"), eq(10L), eq(1L), eq(Status.OPEN), eq(Status.IN_PROGRESS));
    }

    @Test
    void updateTicketStatusShouldRejectInvalidTransition() {
        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 12L);
        ticket.setStatus(Status.OPEN);

        when(ticketRepository.findById(12L)).thenReturn(Optional.of(ticket));

        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus(Status.RESOLVED);

        assertThrows(InvalidTicketStatusException.class, () -> ticketService.updateTicketStatus(12L, request));
        verify(ticketRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void assignTicketShouldValidateAgentAndPublishEvent() {
        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 33L);

        User agent = new User();
        ReflectionTestUtils.setField(agent, "id", 2L);
        agent.setRole(Role.AGENT);

        when(ticketRepository.findById(33L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignTicketRequest request = new AssignTicketRequest();
        request.setAgentId(2L);

        Ticket assigned = ticketService.assignTicket(33L, request);

        assertEquals(agent, assigned.getAssignedTo());
        verify(eventPublisher).publishEvent(any(TicketAssignedEvent.class));
    }

    @Test
    void assignTicketShouldRejectNonAgent() {
        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 33L);

        User customer = new User();
        ReflectionTestUtils.setField(customer, "id", 3L);
        customer.setRole(Role.CUSTOMER);

        when(ticketRepository.findById(33L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(3L)).thenReturn(Optional.of(customer));

        AssignTicketRequest request = new AssignTicketRequest();
        request.setAgentId(3L);

        assertThrows(InvalidTicketAssignmentException.class, () -> ticketService.assignTicket(33L, request));
        verify(ticketRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void toResponseShouldMapVersionAndSla() {
        User creator = new User();
        ReflectionTestUtils.setField(creator, "id", 1L);
        creator.setName("Alice");

        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 99L);
        ticket.setTitle("Sample Issue");
        ticket.setDescription("Sample Description");
        ticket.setPriority(Priority.MEDIUM);
        ticket.setStatus(Status.OPEN);
        ticket.setCreatedBy(creator);
        ticket.setVersion(3L);
        ticket.setSlaBreached(false);

        TicketResponse response = ticketService.toResponse(ticket);

        assertEquals(99L, response.getId());
        assertEquals(3L, response.getVersion());
        assertFalse(response.isSlaBreached());
        assertEquals("Alice", response.getCreatedByName());
    }
}
