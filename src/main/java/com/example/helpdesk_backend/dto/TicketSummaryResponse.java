package com.example.helpdesk_backend.dto;

import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;

public class TicketSummaryResponse {

    private long totalTickets;
    private long openTickets;
    private long inProgressTickets;
    private long resolvedTickets;
    private long closedTickets;
    private long highPriorityTickets;
    private long mediumPriorityTickets;
    private long lowPriorityTickets;

    public long getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(long totalTickets) {
        this.totalTickets = totalTickets;
    }

    public long getOpenTickets() {
        return openTickets;
    }

    public void setOpenTickets(long openTickets) {
        this.openTickets = openTickets;
    }

    public long getInProgressTickets() {
        return inProgressTickets;
    }

    public void setInProgressTickets(long inProgressTickets) {
        this.inProgressTickets = inProgressTickets;
    }

    public long getResolvedTickets() {
        return resolvedTickets;
    }

    public void setResolvedTickets(long resolvedTickets) {
        this.resolvedTickets = resolvedTickets;
    }

    public long getClosedTickets() {
        return closedTickets;
    }

    public void setClosedTickets(long closedTickets) {
        this.closedTickets = closedTickets;
    }

    public long getHighPriorityTickets() {
        return highPriorityTickets;
    }

    public void setHighPriorityTickets(long highPriorityTickets) {
        this.highPriorityTickets = highPriorityTickets;
    }

    public long getMediumPriorityTickets() {
        return mediumPriorityTickets;
    }

    public void setMediumPriorityTickets(long mediumPriorityTickets) {
        this.mediumPriorityTickets = mediumPriorityTickets;
    }

    public long getLowPriorityTickets() {
        return lowPriorityTickets;
    }

    public void setLowPriorityTickets(long lowPriorityTickets) {
        this.lowPriorityTickets = lowPriorityTickets;
    }

    public void addStatusCount(Status status) {
        if (status == null) {
            return;
        }
        switch (status) {
            case OPEN -> openTickets++;
            case IN_PROGRESS -> inProgressTickets++;
            case RESOLVED -> resolvedTickets++;
            case CLOSED -> closedTickets++;
            default -> {
            }
        }
        totalTickets++;
    }

    public void addPriorityCount(Priority priority) {
        if (priority == null) {
            return;
        }
        switch (priority) {
            case HIGH -> highPriorityTickets++;
            case MEDIUM -> mediumPriorityTickets++;
            case LOW -> lowPriorityTickets++;
            default -> {
            }
        }
    }
}
