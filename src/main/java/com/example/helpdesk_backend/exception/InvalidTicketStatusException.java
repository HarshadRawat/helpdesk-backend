package com.example.helpdesk_backend.exception;

public class InvalidTicketStatusException extends RuntimeException {

    public InvalidTicketStatusException(String message) {
        super(message);
    }
}