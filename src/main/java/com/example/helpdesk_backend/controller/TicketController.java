package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.AgentWorkloadResponse;
import com.example.helpdesk_backend.dto.AssignTicketRequest;
import com.example.helpdesk_backend.dto.StatusUpdateRequest;
import com.example.helpdesk_backend.dto.TicketRequest;
import com.example.helpdesk_backend.dto.TicketResponse;
import com.example.helpdesk_backend.dto.TicketSummaryResponse;
import com.example.helpdesk_backend.dto.TicketUpdateRequest;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(required = false) String search,
            Pageable pageable) {

        return ticketService
                .getFilteredTickets(status, priority, search, pageable)
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

    @GetMapping("/summary")
    public TicketSummaryResponse getTicketSummary() {
        return ticketService.getTicketSummary();
    }

    @GetMapping("/agent-workload")
    public List<AgentWorkloadResponse> getAgentWorkload() {
        return ticketService.getAgentWorkload();
    }

    @PutMapping("/{id}/status")
    public TicketResponse updateTicketStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {

        Ticket ticket = ticketService.updateTicketStatus(id, request);

        return ticketService.toResponse(ticket);
    }

    @PutMapping("/{id}/assign")
    public TicketResponse assignTicket(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request) {

        Ticket ticket = ticketService.assignTicket(id, request);

        return ticketService.toResponse(ticket);
    }
}