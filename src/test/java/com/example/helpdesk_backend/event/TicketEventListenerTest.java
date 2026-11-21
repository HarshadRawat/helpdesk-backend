package com.example.helpdesk_backend.event;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TicketEventListener listener;

    @Test
    void shouldHandleTicketStatusChangedEvent() {
        Ticket ticket = new Ticket();
        ticket.setId(10L);
        ticket.setTitle("Network issue");

        User customer = new User();
        customer.setEmail("customer@example.com");
        customer.setName("Customer");

        TicketStatusChangedEvent event = new TicketStatusChangedEvent(ticket, customer, "OPEN", "IN_PROGRESS");

        listener.onTicketStatusChanged(event);

        verify(notificationService).sendTicketStatusNotification(ticket, customer, "OPEN", "IN_PROGRESS");
    }

    @Test
    void shouldHandleTicketAssignedEvent() {
        Ticket ticket = new Ticket();
        ticket.setId(20L);
        ticket.setTitle("Server down");

        User agent = new User();
        agent.setEmail("agent@example.com");
        agent.setName("Agent");

        TicketAssignedEvent event = new TicketAssignedEvent(ticket, agent);

        listener.onTicketAssigned(event);

        verify(notificationService).sendTicketAssignmentNotification(ticket, agent);
    }
}
