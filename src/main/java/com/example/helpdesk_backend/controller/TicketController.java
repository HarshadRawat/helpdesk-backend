package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.*;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.helpdesk_backend.entity.Ticket;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<TicketResponse> getAllTickets() {
        return ticketService.getAllTickets()
                .stream()
                .map(ticket -> ticketService.toResponse(ticket))
                .toList();
    }

    @GetMapping("/paged")
    public Page<TicketResponse> getTickets(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            Pageable pageable) {

        return ticketService
                .getFilteredTickets(status, priority, pageable)
                .map(ticket -> ticketService.toResponse(ticket));
    }

    @PostMapping
    public TicketResponse createTicket(
            @Valid @RequestBody TicketRequest request) {

        Ticket ticket = ticketService.createTicket(request);

        return ticketService.toResponse(ticket);
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id) {

        Ticket ticket = ticketService.getTicketById(id);

        return ticketService.toResponse(ticket);
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody TicketUpdateRequest request) {

        Ticket ticket = ticketService.updateTicket(id, request);

        return ticketService.toResponse(ticket);
    }

    @DeleteMapping("/{id}")
    public void deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
    }

    @PutMapping("/{id}/status")
    public TicketResponse updateTicketStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {

        Ticket ticket = ticketService.updateTicketStatus(id, request);

        return ticketService.toResponse(ticket);
    }

    @PutMapping("/{id}/assign")
    public TicketResponse assignTicket(
            @PathVariable Long id,
            @RequestBody AssignTicketRequest request) {

        Ticket ticket = ticketService.assignTicket(id, request);

        return ticketService.toResponse(ticket);
    }
}