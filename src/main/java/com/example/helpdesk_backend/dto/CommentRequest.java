package com.example.helpdesk_backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CommentRequest {

    @NotBlank
    private String message;

    private Long userId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}