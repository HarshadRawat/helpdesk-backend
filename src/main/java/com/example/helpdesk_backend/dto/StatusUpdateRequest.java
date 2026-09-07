package com.example.helpdesk_backend.dto;

import com.example.helpdesk_backend.enums.Status;

public class StatusUpdateRequest {

    private Status status;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}