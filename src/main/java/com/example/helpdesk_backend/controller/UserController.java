package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.UserRequest;
import com.example.helpdesk_backend.dto.UserResponse;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User registration, retrieval, and administrative role management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all users", description = "Returns all users in the system. Restricted to ADMIN.")
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers()
                .stream()
                .map(user -> userService.toResponse(user))
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Returns user profile details. Restricted to ADMIN.")
    public UserResponse getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return userService.toResponse(user);
    }

    @PostMapping
    @Operation(summary = "Register user", description = "Creates a new user account (publicly accessible).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed or email already registered")
    })
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        User user = userService.createUser(request);
        return userService.toResponse(user);
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Update user role", description = "Changes user authority to CUSTOMER, AGENT, or ADMIN. Restricted to ADMIN.")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role role) {
        User user = userService.updateUserRole(id, role);
        return ResponseEntity.ok(userService.toResponse(user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Removes a user account. Restricted to ADMIN.")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
