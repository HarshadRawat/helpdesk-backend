package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.*;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.exception.InvalidTicketAssignmentException;
import com.example.helpdesk_backend.exception.InvalidTicketStatusException;
import com.example.helpdesk_backend.exception.TicketNotFoundException;
import com.example.helpdesk_backend.exception.UserNotFoundException;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TicketService {
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String ROLE_AGENT = "ROLE_AGENT";

    private static final String ACTION_VIEW = "access";
    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_DELETE = "delete";

    private static final String MESSAGE_ACCESS_DENIED = "Access denied";


    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService) {

        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    public List<Ticket> getAllTickets() {
        Authentication authentication = getCurrentAuthentication();
        Long userId = getCurrentUserId(authentication);
        String role = getCurrentRole(authentication);

        // ADMIN → can see all tickets
        if (isAdmin(role)) {
            return ticketRepository.findAll();
        }

        // CUSTOMER → can see only tickets created by them
        if (isCustomer(role)) {
            return ticketRepository.findByCreatedById(userId);
        }

        // AGENT → can see only tickets assigned to them
        if (isAgent(role)) {
            return ticketRepository.findByAssignedToId(userId);
        }

        throw new AccessDeniedException(MESSAGE_ACCESS_DENIED);
    }

    public Page<Ticket> getTickets(Pageable pageable) {
        return ticketRepository.findAll(pageable);
    }

    public Ticket createTicket(TicketRequest request) {
        Long userId = getCurrentUserId(getCurrentAuthentication());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Ticket ticket = new Ticket();

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority());
        ticket.setStatus(Status.OPEN);

        ticket.setCreatedBy(user);

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("CREATE", "ticket", savedTicket.getId(), userId, null, savedTicket);
        return savedTicket;
    }

    public Ticket getTicketById(Long id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        Authentication authentication = getCurrentAuthentication();
        assertCanActOnTicket(
                ticket,
                getCurrentUserId(authentication),
                getCurrentRole(authentication),
                ACTION_VIEW
        );
        return ticket;
    }

    public Ticket updateTicket(Long id, TicketUpdateRequest request) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        Authentication authentication = getCurrentAuthentication();
        Long actorId = getCurrentUserId(authentication);
        assertCanActOnTicket(
                ticket,
                actorId,
                getCurrentRole(authentication),
                ACTION_UPDATE
        );

        Ticket snapshot = new Ticket();
        snapshot.setTitle(ticket.getTitle());
        snapshot.setDescription(ticket.getDescription());
        snapshot.setPriority(ticket.getPriority());
        snapshot.setStatus(ticket.getStatus());
        snapshot.setCreatedBy(ticket.getCreatedBy());
        snapshot.setAssignedTo(ticket.getAssignedTo());

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setPriority(request.getPriority());

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("UPDATE", "ticket", savedTicket.getId(), actorId, snapshot, savedTicket);
        return savedTicket;
    }

    public void deleteTicket(Long id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        Authentication authentication = getCurrentAuthentication();
        Long actorId = getCurrentUserId(authentication);
        assertCanActOnTicket(
                ticket,
                actorId,
                getCurrentRole(authentication),
                ACTION_DELETE
        );

        ticketRepository.delete(ticket);
    }

    public Ticket updateTicketStatus(Long id, StatusUpdateRequest request) {
        Authentication authentication = getCurrentAuthentication();
        Long actorId = getCurrentUserId(authentication);
        String role = getCurrentRole(authentication);
        assertCanChangeStatus(role);

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        assertCanActOnTicket(ticket, actorId, role, ACTION_UPDATE);

        Status currentStatus = ticket.getStatus();
        Status newStatus = request.getStatus();

        if (newStatus == null) {
            throw new InvalidTicketStatusException("Status is required");
        }

        if (currentStatus == null) {
            if (newStatus != Status.OPEN && newStatus != Status.IN_PROGRESS) {
                throw new InvalidTicketStatusException("New ticket can only start as OPEN or IN_PROGRESS");
            }
        }

        if (currentStatus == Status.OPEN && newStatus != Status.IN_PROGRESS) {
            throw new InvalidTicketStatusException("OPEN ticket can only move to IN_PROGRESS");
        }

        if (currentStatus == Status.IN_PROGRESS && newStatus != Status.RESOLVED) {
            throw new InvalidTicketStatusException("IN_PROGRESS ticket can only move to RESOLVED");
        }

        if (currentStatus == Status.RESOLVED && newStatus != Status.CLOSED) {
            throw new InvalidTicketStatusException("RESOLVED ticket can only move to CLOSED");
        }

        if (currentStatus == Status.CLOSED) {
            throw new InvalidTicketStatusException("CLOSED ticket cannot be changed");
        }

        Status previousStatus = ticket.getStatus();
        ticket.setStatus(newStatus);

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("STATUS_CHANGE", "ticket", savedTicket.getId(), actorId, previousStatus, newStatus);

        if (savedTicket.getCreatedBy() != null) {
            notificationService.sendTicketStatusNotification(savedTicket, savedTicket.getCreatedBy(), currentStatus != null ? currentStatus.name() : "UNKNOWN", savedTicket.getStatus().name());
        }

        return savedTicket;
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
        Authentication authentication = getCurrentAuthentication();
        Long actorId = getCurrentUserId(authentication);
        assertCanAssign(getCurrentRole(authentication));

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

        User previousAgent = ticket.getAssignedTo();
        ticket.setAssignedTo(agent);

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("ASSIGN", "ticket", savedTicket.getId(), actorId, previousAgent, agent);
        notificationService.sendTicketAssignmentNotification(savedTicket, agent);
        return savedTicket;
    }

    public Page<Ticket> getFilteredTickets(
            Status status,
            Priority priority,
            String search,
            Pageable pageable) {
        Authentication authentication = getCurrentAuthentication();
        Long userId = getCurrentUserId(authentication);
        String role = getCurrentRole(authentication);
        String normalizedSearch = normalizeSearch(search);

        if (isAdmin(role)) {
            if (normalizedSearch != null && status != null && priority != null) {
                return ticketRepository.findByTitleContainingIgnoreCaseAndStatusAndPriority(normalizedSearch, status, priority, pageable);
            }
            if (normalizedSearch != null && status != null) {
                return ticketRepository.findByTitleContainingIgnoreCaseAndStatus(normalizedSearch, status, pageable);
            }
            if (normalizedSearch != null && priority != null) {
                return ticketRepository.findByTitleContainingIgnoreCaseAndPriority(normalizedSearch, priority, pageable);
            }
            if (normalizedSearch != null) {
                return ticketRepository.findByTitleContainingIgnoreCase(normalizedSearch, pageable);
            }
            if (status != null && priority != null) {
                return ticketRepository.findByStatusAndPriority(status, priority, pageable);
            }
            if (status != null) {
                return ticketRepository.findByStatus(status, pageable);
            }
            if (priority != null) {
                return ticketRepository.findByPriority(priority, pageable);
            }
            return ticketRepository.findAll(pageable);
        }

        if (isCustomer(role)) {
            if (normalizedSearch != null && status != null && priority != null) {
                return ticketRepository.findByCreatedByIdAndTitleContainingIgnoreCaseAndStatusAndPriority(userId, normalizedSearch, status, priority, pageable);
            }
            if (normalizedSearch != null && status != null) {
                return ticketRepository.findByCreatedByIdAndTitleContainingIgnoreCaseAndStatus(userId, normalizedSearch, status, pageable);
            }
            if (normalizedSearch != null && priority != null) {
                return ticketRepository.findByCreatedByIdAndTitleContainingIgnoreCaseAndPriority(userId, normalizedSearch, priority, pageable);
            }
            if (normalizedSearch != null) {
                return ticketRepository.findByCreatedByIdAndTitleContainingIgnoreCase(userId, normalizedSearch, pageable);
            }
            if (status != null && priority != null) {
                return ticketRepository.findByCreatedByIdAndStatusAndPriority(userId, status, priority, pageable);
            }
            if (status != null) {
                return ticketRepository.findByCreatedByIdAndStatus(userId, status, pageable);
            }
            if (priority != null) {
                return ticketRepository.findByCreatedByIdAndPriority(userId, priority, pageable);
            }
            return ticketRepository.findByCreatedById(userId, pageable);
        }

        if (isAgent(role)) {
            if (normalizedSearch != null && status != null && priority != null) {
                return ticketRepository.findByAssignedToIdAndTitleContainingIgnoreCaseAndStatusAndPriority(userId, normalizedSearch, status, priority, pageable);
            }
            if (normalizedSearch != null && status != null) {
                return ticketRepository.findByAssignedToIdAndTitleContainingIgnoreCaseAndStatus(userId, normalizedSearch, status, pageable);
            }
            if (normalizedSearch != null && priority != null) {
                return ticketRepository.findByAssignedToIdAndTitleContainingIgnoreCaseAndPriority(userId, normalizedSearch, priority, pageable);
            }
            if (normalizedSearch != null) {
                return ticketRepository.findByAssignedToIdAndTitleContainingIgnoreCase(userId, normalizedSearch, pageable);
            }
            if (status != null && priority != null) {
                return ticketRepository.findByAssignedToIdAndStatusAndPriority(userId, status, priority, pageable);
            }
            if (status != null) {
                return ticketRepository.findByAssignedToIdAndStatus(userId, status, pageable);
            }
            if (priority != null) {
                return ticketRepository.findByAssignedToIdAndPriority(userId, priority, pageable);
            }
            return ticketRepository.findByAssignedToId(userId, pageable);
        }

        throw new AccessDeniedException(MESSAGE_ACCESS_DENIED);
    }

    public TicketSummaryResponse getTicketSummary() {
        String role = getCurrentRole(getCurrentAuthentication());
        if (!isAdmin(role)) {
            throw new AccessDeniedException("Only admins can view ticket summary");
        }

        List<Ticket> tickets = ticketRepository.findAll();
        TicketSummaryResponse summary = new TicketSummaryResponse();

        for (Ticket ticket : tickets) {
            summary.addStatusCount(ticket.getStatus());
            summary.addPriorityCount(ticket.getPriority());
        }

        return summary;
    }

    public List<AgentWorkloadResponse> getAgentWorkload() {
        String role = getCurrentRole(getCurrentAuthentication());
        if (!isAdmin(role)) {
            throw new AccessDeniedException("Only admins can view agent workload");
        }

        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == com.example.helpdesk_backend.enums.Role.AGENT)
                .map(user -> {
                    AgentWorkloadResponse response = new AgentWorkloadResponse();
                    response.setAgentId(user.getId());
                    response.setAgentName(user.getName());
                    response.setAssignedTickets(ticketRepository.findByAssignedToId(user.getId()).size());
                    return response;
                })
                .sorted(Comparator.comparingLong(AgentWorkloadResponse::getAssignedTickets).reversed())
                .toList();
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.length() < 3 ? null : trimmed;
    }

    private Authentication getCurrentAuthentication() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null) {
            throw new AccessDeniedException(MESSAGE_ACCESS_DENIED);
        }

        return authentication;
    }

    private Long getCurrentUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException(MESSAGE_ACCESS_DENIED);
        }
    }

    private String getCurrentRole(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .orElseThrow(() -> new AccessDeniedException(MESSAGE_ACCESS_DENIED));
    }

    private boolean isAdmin(String role) {
        return ROLE_ADMIN.equals(role);
    }

    private boolean isCustomer(String role) {
        return ROLE_CUSTOMER.equals(role);
    }

    private boolean isAgent(String role) {
        return ROLE_AGENT.equals(role);
    }

    private void assertCanAssign(String role) {
        if (!isAdmin(role)) {
            throw new AccessDeniedException("You cannot assign this ticket");
        }
    }

    private void assertCanChangeStatus(String role) {
        if (!isAdmin(role) && !isAgent(role)) {
            throw new AccessDeniedException("You cannot update this ticket status");
        }
    }

    private void assertCanActOnTicket(
            Ticket ticket,
            Long userId,
            String role,
            String action) {

        if (isAdmin(role)) {
            return;
        }

        if (isCustomer(role)) {
            if (ticket.getCreatedBy().getId().equals(userId)) {
                return;
            }
            throw new AccessDeniedException(
                    String.format("You cannot %s this ticket", action));
        }

        if (isAgent(role)) {
            if (ticket.getAssignedTo() != null
                    && ticket.getAssignedTo().getId().equals(userId)) {
                return;
            }
            throw new AccessDeniedException(
                    String.format("You cannot %s this ticket", action));
        }

        throw new AccessDeniedException(MESSAGE_ACCESS_DENIED);
    }
}
