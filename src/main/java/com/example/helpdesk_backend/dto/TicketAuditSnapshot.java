package com.example.helpdesk_backend.dto;

import java.time.Instant;

public record TicketAuditSnapshot(
        String title,
        String description,
        String priority,
        String status,
        Long createdById,
        Long assignedToId,
        Instant slaDueAt,
        Long version
) {}
