package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.LoginRequest;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.InvalidLoginException;
import com.example.helpdesk_backend.repository.UserRepository;
import com.example.helpdesk_backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidLoginException(
                                "Invalid email or password"
                        ));
        System.out.println("LOGIN EMAIL: " + request.getEmail());
        System.out.println("FOUND USER ID: " + user.getId());
        System.out.println("FOUND USER ROLE: " + user.getRole());

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new InvalidLoginException(
                    "Invalid email or password"
            );
        }

        return jwtService.generateToken(user);
    }
}