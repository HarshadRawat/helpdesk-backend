package com.example.helpdesk_backend.dto;

public class AgentWorkloadResponse {

    private Long agentId;
    private String agentName;
    private long assignedTickets;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public long getAssignedTickets() {
        return assignedTickets;
    }

    public void setAssignedTickets(long assignedTickets) {
        this.assignedTickets = assignedTickets;
    }

    public void incrementAssignedTickets() {
        this.assignedTickets++;
    }
}
