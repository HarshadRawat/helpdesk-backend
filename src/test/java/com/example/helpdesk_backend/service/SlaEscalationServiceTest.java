package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaEscalationServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SlaEscalationService slaEscalationService;

    @Test
    void shouldEscalateBreachedTickets() {
        Ticket ticket = new Ticket();
        ticket.setId(101L);
        ticket.setTitle("Critical Database Outage");
        ticket.setPriority(Priority.HIGH);
        ticket.setStatus(Status.OPEN);
        ticket.setSlaDueAt(Instant.now().minusSeconds(3600));
        ticket.setSlaBreached(false);

        when(ticketRepository.findBreachedTickets(any(Instant.class), eq(List.of(Status.RESOLVED, Status.CLOSED))))
                .thenReturn(List.of(ticket));

        int breachedCount = slaEscalationService.checkAndEscalateBreachedTickets();

        assertEquals(1, breachedCount);
        assertTrue(ticket.isSlaBreached());
        verify(ticketRepository).save(ticket);
        verify(auditLogService).log(eq("SLA_BREACH"), eq("ticket"), eq(101L), eq(0L), any(), any());
    }

    @Test
    void shouldDoNothingWhenNoTicketsBreached() {
        when(ticketRepository.findBreachedTickets(any(Instant.class), eq(List.of(Status.RESOLVED, Status.CLOSED))))
                .thenReturn(List.of());

        int count = slaEscalationService.checkAndEscalateBreachedTickets();

        assertEquals(0, count);
        verify(ticketRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }
}
