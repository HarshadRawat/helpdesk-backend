package com.example.helpdesk_backend.event;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;

public record TicketAssignedEvent(
        Ticket ticket,
        User agent
) {
}
