package com.example.helpdesk_backend.dto;

import jakarta.validation.constraints.NotNull;

public class AssignTicketRequest {

    @NotNull
    private Long agentId;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }
}