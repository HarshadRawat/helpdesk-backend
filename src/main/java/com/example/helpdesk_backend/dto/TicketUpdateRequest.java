package com.example.helpdesk_backend.dto;

import com.example.helpdesk_backend.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TicketUpdateRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private Priority priority;

    @NotNull(message = "Version is required for optimistic concurrency control")
    private Long version;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
