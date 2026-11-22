package com.example.helpdesk_backend.dto;

import com.example.helpdesk_backend.enums.Status;
import jakarta.validation.constraints.NotNull;

public class StatusUpdateRequest {

    @NotNull
    private Status status;

    private Long version;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
