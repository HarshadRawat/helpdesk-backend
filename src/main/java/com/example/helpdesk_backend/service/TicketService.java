package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.*;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.exception.InvalidTicketAssignmentException;
import com.example.helpdesk_backend.exception.InvalidTicketStatusException;
import com.example.helpdesk_backend.exception.TicketNotFoundException;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.UserNotFoundException;
import com.example.helpdesk_backend.enums.Role;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.Arrays;
import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository) {

        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    public List<Ticket> getAllTickets() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        Long userId = Long.valueOf(authentication.getName());

        String role = authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        // ADMIN → can see all tickets
        if (role.equals("ROLE_ADMIN")) {
            return ticketRepository.findAll();
        }

        // CUSTOMER → can see only tickets created by them
        if (role.equals("ROLE_CUSTOMER")) {
            return ticketRepository.findByCreatedById(userId);
        }

        // AGENT → can see only tickets assigned to them
        if (role.equals("ROLE_AGENT")) {
            return ticketRepository.findByAssignedToId(userId);
        }

        return List.of();
    }

    public Page<Ticket> getTickets(Pageable pageable) {
        return ticketRepository.findAll(pageable);
    }

    public Ticket createTicket(TicketRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        Long userId = Long.valueOf(authentication.getName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Ticket ticket = new Ticket();

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority());
        ticket.setStatus(Status.OPEN);

        ticket.setCreatedBy(user);

        return ticketRepository.save(ticket);
    }

    public Ticket getTicketById(Long id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        Long userId = Long.valueOf(authentication.getName());

        String role = authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        // Admin can view any ticket
        if (role.equals("ROLE_ADMIN")) {
            return ticket;
        }

        // Customer can view only their own ticket
        if (role.equals("ROLE_CUSTOMER")) {

            if (!ticket.getCreatedBy().getId().equals(userId)) {
                throw new AccessDeniedException("You cannot access this ticket");
            }

            return ticket;
        }

        // Agent can view tickets assigned to them
        if (role.equals("ROLE_AGENT")) {

            if (ticket.getAssignedTo() == null ||
                    !ticket.getAssignedTo().getId().equals(userId)) {
                throw new AccessDeniedException("You cannot access this ticket");
            }

            return ticket;
        }

        throw new AccessDeniedException("Access denied");
    }

    public Ticket updateTicket(Long id, TicketUpdateRequest request) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        Long userId = Long.valueOf(authentication.getName());

        String role = authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        // ADMIN → can update any ticket
        if (role.equals("ROLE_ADMIN")) {
            // allowed
        }

        // CUSTOMER → can update only their own ticket
        else if (role.equals("ROLE_CUSTOMER")) {

            if (!ticket.getCreatedBy().getId().equals(userId)) {
                throw new AccessDeniedException("You cannot update this ticket");
            }
        }

        // AGENT → can update only assigned tickets
        else if (role.equals("ROLE_AGENT")) {

            if (ticket.getAssignedTo() == null ||
                    !ticket.getAssignedTo().getId().equals(userId)) {

                throw new AccessDeniedException("You cannot update this ticket");
            }
        }

        else {
            throw new AccessDeniedException("Access denied");
        }

        // Authorization passed → perform the update
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority());

        return ticketRepository.save(ticket);
    }

    public void deleteTicket(Long id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        Long userId = Long.valueOf(authentication.getName());

        String role = authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        // ADMIN → can delete any ticket
        if (role.equals("ROLE_ADMIN")) {
            // allowed
        }

        // CUSTOMER → can delete only their own ticket
        else if (role.equals("ROLE_CUSTOMER")) {

            if (!ticket.getCreatedBy().getId().equals(userId)) {
                throw new AccessDeniedException("You cannot delete this ticket");
            }
        }

        // AGENT → can delete only assigned tickets
        else if (role.equals("ROLE_AGENT")) {

            if (ticket.getAssignedTo() == null ||
                    !ticket.getAssignedTo().getId().equals(userId)) {

                throw new AccessDeniedException("You cannot delete this ticket");
            }
        }

        else {
            throw new AccessDeniedException("Access denied");
        }

        ticketRepository.delete(ticket);
    }

    public Ticket updateTicketStatus(Long id, StatusUpdateRequest request) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        Status currentStatus = ticket.getStatus();
        Status newStatus = request.getStatus();

        if (currentStatus == Status.OPEN && newStatus != Status.IN_PROGRESS) {
            throw new InvalidTicketStatusException(
                    "OPEN ticket can only move to IN_PROGRESS"
            );
        }

        if (currentStatus == Status.IN_PROGRESS && newStatus != Status.RESOLVED) {
            throw new InvalidTicketStatusException(
                    "OPEN ticket can only move to IN_PROGRESS"
            );
        }

        if (currentStatus == Status.RESOLVED && newStatus != Status.CLOSED) {
            throw new InvalidTicketStatusException(
                    "OPEN ticket can only move to IN_PROGRESS"
            );
        }

        if (currentStatus == Status.CLOSED) {
            throw new InvalidTicketStatusException(
                    "OPEN ticket can only move to IN_PROGRESS"
            );
        }

        ticket.setStatus(newStatus);

        return ticketRepository.save(ticket);
    }

    public TicketResponse toResponse(Ticket ticket) {

        TicketResponse response = new TicketResponse();

        response.setId(ticket.getId());
        response.setTitle(ticket.getTitle());
        response.setDescription(ticket.getDescription());
        response.setPriority(ticket.getPriority());
        response.setStatus(ticket.getStatus());

        response.setCreatedById(ticket.getCreatedBy().getId());
        response.setCreatedByName(ticket.getCreatedBy().getName());

        if (ticket.getAssignedTo() != null) {
            response.setAssignedToId(ticket.getAssignedTo().getId());
            response.setAssignedToName(ticket.getAssignedTo().getName());
        }

        return response;
    }

    public Ticket assignTicket(Long ticketId, AssignTicketRequest request) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        User agent = userRepository.findById(request.getAgentId())
                .orElseThrow(() ->
                        new UserNotFoundException(request.getAgentId()));

        if (agent.getRole() != Role.AGENT) {
            throw new InvalidTicketAssignmentException(
                    "User is not an AGENT"
            );
        }

        ticket.setAssignedTo(agent);

        return ticketRepository.save(ticket);
    }

    public Page<Ticket> getFilteredTickets(
            Status status,
            Priority priority,
            Pageable pageable) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        Long userId = Long.valueOf(authentication.getName());

        String role = authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        // ADMIN → can see all tickets
        if (role.equals("ROLE_ADMIN")) {

            if (status != null && priority != null) {
                return ticketRepository.findByStatusAndPriority(
                        status, priority, pageable);
            }

            if (status != null) {
                return ticketRepository.findByStatus(
                        status, pageable);
            }

            if (priority != null) {
                return ticketRepository.findByPriority(
                        priority, pageable);
            }

            return ticketRepository.findAll(pageable);
        }

        // CUSTOMER → only their own tickets
        if (role.equals("ROLE_CUSTOMER")) {

            if (status != null && priority != null) {
                return ticketRepository.findByCreatedByIdAndStatusAndPriority(
                        userId, status, priority, pageable);
            }

            if (status != null) {
                return ticketRepository.findByCreatedByIdAndStatus(
                        userId, status, pageable);
            }

            if (priority != null) {
                return ticketRepository.findByCreatedByIdAndPriority(
                        userId, priority, pageable);
            }

            return ticketRepository.findByCreatedById(
                    userId, pageable);
        }

        // AGENT → only tickets assigned to them
        if (role.equals("ROLE_AGENT")) {

            if (status != null && priority != null) {
                return ticketRepository.findByAssignedToIdAndStatusAndPriority(
                        userId, status, priority, pageable);
            }

            if (status != null) {
                return ticketRepository.findByAssignedToIdAndStatus(
                        userId, status, pageable);
            }

            if (priority != null) {
                return ticketRepository.findByAssignedToIdAndPriority(
                        userId, priority, pageable);
            }

            return ticketRepository.findByAssignedToId(
                    userId, pageable);
        }

        return Page.empty(pageable);
    }
}
