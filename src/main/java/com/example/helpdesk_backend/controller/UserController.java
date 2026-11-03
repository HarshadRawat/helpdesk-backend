package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.UserRequest;
import com.example.helpdesk_backend.dto.UserResponse;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers()
                .stream()
                .map(user -> userService.toResponse(user))
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return userService.toResponse(user);
    }

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        User user = userService.createUser(request);
        return userService.toResponse(user);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role role) {
        User user = userService.updateUserRole(id, role);
        return ResponseEntity.ok(userService.toResponse(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}