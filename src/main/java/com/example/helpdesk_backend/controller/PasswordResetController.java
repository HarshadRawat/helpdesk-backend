package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.PasswordResetRequest;
import com.example.helpdesk_backend.dto.PasswordUpdateRequest;
import com.example.helpdesk_backend.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestParam String token, @Valid @RequestBody PasswordUpdateRequest request) {
        passwordResetService.resetPassword(token, request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }
}
