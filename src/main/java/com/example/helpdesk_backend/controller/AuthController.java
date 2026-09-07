package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.LoginRequest;
import com.example.helpdesk_backend.dto.LoginResponse;
import com.example.helpdesk_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        String token = authService.login(request);

        return new LoginResponse(token);
    }
}