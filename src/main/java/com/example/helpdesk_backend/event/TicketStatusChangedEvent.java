package com.example.helpdesk_backend.event;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;

public record TicketStatusChangedEvent(
        Ticket ticket,
        User recipient,
        String oldStatus,
        String newStatus
) {
}
