package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.ForgotPasswordRequest;
import com.example.helpdesk_backend.dto.LoginRequest;
import com.example.helpdesk_backend.dto.LoginResponse;
import com.example.helpdesk_backend.dto.RefreshTokenRequest;
import com.example.helpdesk_backend.dto.ResetPasswordRequest;
import com.example.helpdesk_backend.service.AuthService;
import com.example.helpdesk_backend.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication, token refresh rotation, and self-service password recovery")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Validates credentials and returns a short-lived JWT access token and a refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request format"),
            @ApiResponse(responseCode = "401", description = "Bad credentials")
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token", description = "Validates an existing refresh token, revokes it, and issues a new access/refresh token pair. Detects token reuse.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token rotation successful"),
            @ApiResponse(responseCode = "401", description = "Invalid, expired, or compromised refresh token")
    })
    public LoginResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out user", description = "Revokes the supplied refresh token to prevent further session renewals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged out successfully")
    })
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends a single-use 15-minute password reset link if the email exists, protecting against user enumeration.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset request accepted and processed")
    })
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If an account with that email exists, a password reset email has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Confirm password reset", description = "Validates the reset token, updates the password, and revokes all existing refresh tokens for the user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successful"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired reset token")
    })
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset. Please log in with your new password."));
    }
}
