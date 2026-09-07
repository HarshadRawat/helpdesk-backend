package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.CommentRequest;
import com.example.helpdesk_backend.dto.CommentResponse;
import com.example.helpdesk_backend.entity.Comment;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.TicketNotFoundException;
import com.example.helpdesk_backend.exception.UserNotFoundException;
import com.example.helpdesk_backend.repository.CommentRepository;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public CommentService(
            CommentRepository commentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository) {

        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    public CommentResponse addComment(
            Long ticketId,
            CommentRequest request) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() ->
                        new TicketNotFoundException(ticketId));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        Long userId = Long.valueOf(authentication.getName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Comment comment = new Comment();

        comment.setMessage(request.getMessage());
        comment.setTicket(ticket);
        comment.setUser(user);

        Comment savedComment = commentRepository.save(comment);

        return toResponse(savedComment);
    }

    public List<CommentResponse> getComments(Long ticketId) {

        if (!ticketRepository.existsById(ticketId)) {
            throw new TicketNotFoundException(ticketId);
        }

        return commentRepository.findByTicketId(ticketId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CommentResponse toResponse(Comment comment) {

        CommentResponse response = new CommentResponse();

        response.setId(comment.getId());
        response.setMessage(comment.getMessage());

        response.setUserId(comment.getUser().getId());
        response.setUserName(comment.getUser().getName());

        return response;
    }
}