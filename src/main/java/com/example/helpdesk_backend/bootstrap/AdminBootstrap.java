package com.example.helpdesk_backend.bootstrap;

import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.admin.name:Admin}") String adminName,
            @Value("${app.bootstrap.admin.email:${APP_BOOTSTRAP_ADMIN_EMAIL:admin@localhost}}") String adminEmail,
            @Value("${app.bootstrap.admin.password:${APP_BOOTSTRAP_ADMIN_PASSWORD:ChangeMe123!}}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createDefaultAdminIfMissing() {
        if (!enabled) {
            return;
        }

        if (userRepository.existsByEmail(adminEmail.toLowerCase())) {
            return;
        }

        User admin = new User();
        admin.setName(adminName);
        admin.setEmail(adminEmail.toLowerCase());
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);

        userRepository.save(admin);
    }
}
