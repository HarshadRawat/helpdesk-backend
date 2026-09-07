package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.UserRequest;
import com.example.helpdesk_backend.dto.UserResponse;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.service.UserService;
import jakarta.validation.Valid;
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
}