package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.LoginRequest;
import com.example.helpdesk_backend.dto.LoginResponse;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.InvalidLoginException;
import com.example.helpdesk_backend.repository.UserRepository;
import com.example.helpdesk_backend.security.JwtService;
import com.example.helpdesk_backend.security.LoginAttemptTracker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptTracker loginAttemptTracker;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptTracker loginAttemptTracker) {

        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptTracker = loginAttemptTracker;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        if (loginAttemptTracker.isBlocked(email)) {
            throw new InvalidLoginException("Too many failed login attempts. Please try again later.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    loginAttemptTracker.recordFailure(email);
                    return new InvalidLoginException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptTracker.recordFailure(email);
            throw new InvalidLoginException("Invalid email or password");
        }

        loginAttemptTracker.recordSuccess(email);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = refreshTokenService.issueToken(user);

        return new LoginResponse(accessToken, refreshTokenValue);
    }

    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        RefreshTokenService.RotationResult rotationResult = refreshTokenService.rotate(refreshToken);
        User user = rotationResult.user();
        String accessToken = jwtService.generateAccessToken(user);

        return new LoginResponse(accessToken, rotationResult.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
}