package com.example.helpdesk_backend.exception;

public class InvalidTicketAssignmentException extends RuntimeException {

    public InvalidTicketAssignmentException(String message) {
        super(message);
    }
}