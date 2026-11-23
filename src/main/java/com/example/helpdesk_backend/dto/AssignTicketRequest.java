package com.example.helpdesk_backend.dto;

import jakarta.validation.constraints.NotNull;

public class AssignTicketRequest {

    @NotNull
    private Long agentId;

    @NotNull(message = "Version is required for optimistic concurrency control")
    private Long version;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getAssignedToId() {
        return agentId;
    }

    public void setAssignedToId(Long assignedToId) {
        this.agentId = assignedToId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
