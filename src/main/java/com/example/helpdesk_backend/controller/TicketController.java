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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Core ticket lifecycle, status management, assignment, search, and analytics")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "List accessible tickets", description = "Retrieves tickets scoped to the authenticated caller (customers view own, agents view assigned, admins view all).")
    public List<TicketResponse> getAllTickets() {
        return ticketService.getAllTickets()
                .stream()
                .map(ticket -> ticketService.toResponse(ticket))
                .toList();
    }

    @GetMapping("/paged")
    @Operation(summary = "Search and filter tickets", description = "Returns a paginated list of tickets filtered by status, priority, and text search.")
    public Page<TicketResponse> getTickets(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String search,
            Pageable pageable) {

        int cappedSize = Math.min(pageable.getPageSize(), 100);
        Pageable safePageable = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                cappedSize,
                pageable.getSort()
        );

        return ticketService
                .getFilteredTickets(status, priority, search, safePageable)
                .map(ticket -> ticketService.toResponse(ticket));
    }

    @PostMapping
    @Operation(summary = "Create ticket", description = "Creates a new ticket, sets dynamic SLA targets based on priority, and fires asynchronous audit events.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
    })
    public TicketResponse createTicket(
            @Valid @RequestBody TicketRequest request) {

        Ticket ticket = ticketService.createTicket(request);

        return ticketService.toResponse(ticket);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID", description = "Retrieves ticket details enforcing customer and agent role boundaries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public TicketResponse getTicketById(@PathVariable Long id) {

        Ticket ticket = ticketService.getTicketById(id);

        return ticketService.toResponse(ticket);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ticket", description = "Updates title, description, or priority. Protected with optimistic concurrency locking (@Version).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket updated"),
            @ApiResponse(responseCode = "409", description = "Optimistic locking conflict - data was modified concurrently")
    })
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody TicketUpdateRequest request) {

        Ticket ticket = ticketService.updateTicket(id, request);

        return ticketService.toResponse(ticket);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete ticket", description = "Deletes a ticket. Restricted to ticket owner or ADMIN.")
    public void deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
    }

    @GetMapping("/summary")
    @Operation(summary = "Ticket metrics summary", description = "Aggregates ticket status counts. Cached in-memory with automatic eviction on state changes.")
    public TicketSummaryResponse getTicketSummary() {
        return ticketService.getTicketSummary();
    }

    @GetMapping("/agent-workload")
    @Operation(summary = "Agent workload analytics", description = "Aggregates active ticket counts per agent. Cached in-memory with automatic eviction on state changes.")
    public List<AgentWorkloadResponse> getAgentWorkload() {
        return ticketService.getAgentWorkload();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update ticket status", description = "Transitions ticket state according to allowed lifecycle rules. Triggers domain events and evicts cached metrics.")
    public TicketResponse updateTicketStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {

        Ticket ticket = ticketService.updateTicketStatus(id, request);

        return ticketService.toResponse(ticket);
    }

    @PutMapping("/{id}/assign")
    @Operation(summary = "Assign ticket", description = "Assigns a ticket to an agent. Restricted to ADMIN users.")
    public TicketResponse assignTicket(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request) {

        Ticket ticket = ticketService.assignTicket(id, request);

        return ticketService.toResponse(ticket);
    }
}
