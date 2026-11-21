package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.CommentRequest;
import com.example.helpdesk_backend.dto.CommentResponse;
import com.example.helpdesk_backend.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
@Tag(name = "Comments", description = "Ticket discussions and threaded commentary")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @Operation(summary = "Add comment", description = "Appends a new comment to a ticket. Caller must have view access to the ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment added successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public CommentResponse addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CommentRequest request) {

        return commentService.addComment(ticketId, request);
    }

    @GetMapping
    @Operation(summary = "List comments", description = "Fetches all comments posted on the ticket in chronological order.")
    public List<CommentResponse> getComments(
            @PathVariable Long ticketId) {

        return commentService.getComments(ticketId);
    }
}
