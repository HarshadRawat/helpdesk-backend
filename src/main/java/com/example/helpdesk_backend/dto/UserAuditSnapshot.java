package com.example.helpdesk_backend.dto;

public record UserAuditSnapshot(
        Long id,
        String name,
        String email
) {}
