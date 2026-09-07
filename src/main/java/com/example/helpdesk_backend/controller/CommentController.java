package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.CommentRequest;
import com.example.helpdesk_backend.dto.CommentResponse;
import com.example.helpdesk_backend.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public CommentResponse addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CommentRequest request) {

        return commentService.addComment(ticketId, request);
    }

    @GetMapping
    public List<CommentResponse> getComments(
            @PathVariable Long ticketId) {

        return commentService.getComments(ticketId);
    }
}