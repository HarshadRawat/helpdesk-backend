package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.*;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.event.TicketAssignedEvent;
import com.example.helpdesk_backend.event.TicketStatusChangedEvent;
import com.example.helpdesk_backend.exception.InvalidTicketAssignmentException;
import com.example.helpdesk_backend.exception.InvalidTicketStatusException;
import com.example.helpdesk_backend.exception.TicketNotFoundException;
import com.example.helpdesk_backend.exception.UserNotFoundException;
import com.example.helpdesk_backend.repository.CommentRepository;
import com.example.helpdesk_backend.repository.TicketAttachmentRepository;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.TicketSpecification;
import com.example.helpdesk_backend.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
    private final CommentRepository commentRepository;
    private final TicketAttachmentRepository ticketAttachmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            CommentRepository commentRepository,
            TicketAttachmentRepository ticketAttachmentRepository,
            ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService) {

        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.ticketAttachmentRepository = ticketAttachmentRepository;
        this.eventPublisher = eventPublisher;
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

    @Transactional
    @CacheEvict(value = {"ticketSummary", "agentWorkload"}, allEntries = true)
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
        ticket.setSlaDueAt(calculateSlaDueAt(request.getPriority()));
        ticket.setSlaBreached(false);

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("CREATE", "ticket", savedTicket.getId(), userId, null, toSnapshot(savedTicket));
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

    @Transactional
    @CacheEvict(value = {"ticketSummary", "agentWorkload"}, allEntries = true)
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

        if (request.getVersion() == null || !request.getVersion().equals(ticket.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Ticket.class, id);
        }

        TicketAuditSnapshot oldSnapshot = toSnapshot(ticket);

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        if (request.getPriority() != null && request.getPriority() != ticket.getPriority()) {
            ticket.setPriority(request.getPriority());
            if (ticket.getStatus() == Status.OPEN || ticket.getStatus() == Status.IN_PROGRESS) {
                ticket.setSlaDueAt(calculateSlaDueAt(request.getPriority()));
            }
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("UPDATE", "ticket", savedTicket.getId(), actorId, oldSnapshot, toSnapshot(savedTicket));
        return savedTicket;
    }

    @Transactional
    @CacheEvict(value = {"ticketSummary", "agentWorkload"}, allEntries = true)
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

        ticket.setDeleted(true);
        ticketRepository.save(ticket);
        auditLogService.log("DELETE", "ticket", ticket.getId(), actorId, toSnapshot(ticket), null);
    }

    @Transactional
    @CacheEvict(value = {"ticketSummary", "agentWorkload"}, allEntries = true)
    public Ticket updateTicketStatus(Long id, StatusUpdateRequest request) {
        Authentication authentication = getCurrentAuthentication();
        Long actorId = getCurrentUserId(authentication);
        String role = getCurrentRole(authentication);
        assertCanChangeStatus(role);

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        assertCanActOnTicket(ticket, actorId, role, ACTION_UPDATE);

        if (request.getVersion() == null || !request.getVersion().equals(ticket.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Ticket.class, id);
        }

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

        if (currentStatus == Status.RESOLVED && newStatus != Status.CLOSED && newStatus != Status.OPEN) {
            throw new InvalidTicketStatusException("RESOLVED ticket can only move to CLOSED or OPEN");
        }

        if (currentStatus == Status.CLOSED) {
            throw new InvalidTicketStatusException("CLOSED ticket cannot be changed");
        }

        Status previousStatus = ticket.getStatus();
        ticket.setStatus(newStatus);
        if (newStatus == Status.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
            if (ticket.getSlaDueAt() != null && Instant.now().isAfter(ticket.getSlaDueAt())) {
                ticket.setSlaBreached(true);
            }
        } else if (newStatus == Status.OPEN) {
            ticket.setResolvedAt(null);
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("STATUS_CHANGE", "ticket", savedTicket.getId(), actorId, previousStatus, newStatus);

        if (savedTicket.getCreatedBy() != null) {
            eventPublisher.publishEvent(new TicketStatusChangedEvent(
                    savedTicket,
                    savedTicket.getCreatedBy(),
                    currentStatus != null ? currentStatus.name() : "UNKNOWN",
                    savedTicket.getStatus().name()
            ));
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

        response.setVersion(ticket.getVersion());
        response.setSlaDueAt(ticket.getSlaDueAt());
        response.setSlaBreached(ticket.isSlaBreached());
        response.setCreatedAt(ticket.getCreatedAt());
        response.setUpdatedAt(ticket.getUpdatedAt());
        response.setResolvedAt(ticket.getResolvedAt());

        return response;
    }

    @Transactional
    @CacheEvict(value = {"ticketSummary", "agentWorkload"}, allEntries = true)
    public Ticket assignTicket(Long ticketId, AssignTicketRequest request) {
        Authentication authentication = getCurrentAuthentication();
        Long actorId = getCurrentUserId(authentication);
        assertCanAssign(getCurrentRole(authentication));

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        if (request.getVersion() == null || !request.getVersion().equals(ticket.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Ticket.class, ticketId);
        }

        User agent = userRepository.findById(request.getAgentId())
                .orElseThrow(() ->
                        new UserNotFoundException(request.getAgentId()));

        if (agent.getRole() != Role.AGENT) {
            throw new InvalidTicketAssignmentException(
                    "User is not an AGENT"
            );
        }

        User previousAgent = ticket.getAssignedTo();
        if (previousAgent != null && previousAgent.getId().equals(agent.getId())) {
            throw new InvalidTicketAssignmentException(
                    "Ticket is already assigned to this agent (" + agent.getName() + ")"
            );
        }
        ticket.setAssignedTo(agent);

        Ticket savedTicket = ticketRepository.save(ticket);
        auditLogService.log("ASSIGN", "ticket", savedTicket.getId(), actorId, toUserSnapshot(previousAgent), toUserSnapshot(agent));
        eventPublisher.publishEvent(new TicketAssignedEvent(savedTicket, agent));
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

        Specification<Ticket> spec = Specification.where(TicketSpecification.hasStatus(status))
                .and(TicketSpecification.hasPriority(priority))
                .and(TicketSpecification.searchInTitle(normalizeSearch(search)));

        if (isCustomer(role)) {
            spec = spec.and(TicketSpecification.createdBy(userId));
        } else if (!isAgent(role) && !isAdmin(role)) {
            throw new AccessDeniedException(MESSAGE_ACCESS_DENIED);
        }

        return ticketRepository.findAll(spec, pageable);
    }

    @Cacheable(value = "ticketSummary", condition = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication() != null and T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getAuthorities().?[authority == 'ROLE_ADMIN'].size() > 0")
    public TicketSummaryResponse getTicketSummary() {
        Authentication authentication = getCurrentAuthentication();
        Long userId = getCurrentUserId(authentication);
        String role = getCurrentRole(authentication);

        TicketSummaryResponse summary = new TicketSummaryResponse();

        if (isAdmin(role)) {
            List<Object[]> statusCounts = ticketRepository.countTicketsByStatus();
            for (Object[] row : statusCounts) {
                Status status = (Status) row[0];
                long count = ((Number) row[1]).longValue();
                summary.setStatusCount(status, count);
            }

            List<Object[]> priorityCounts = ticketRepository.countTicketsByPriority();
            for (Object[] row : priorityCounts) {
                Priority priority = (Priority) row[0];
                long count = ((Number) row[1]).longValue();
                summary.setPriorityCount(priority, count);
            }

            long totalResolved = ticketRepository.countResolvedOrClosedTickets();
            long compliant = ticketRepository.countSlaCompliantTickets();
            double complianceRate = totalResolved > 0 ? ((double) compliant / totalResolved) * 100.0 : 100.0;
            summary.setSlaComplianceRate(Math.round(complianceRate * 10.0) / 10.0);
            summary.setSlaBreachedTickets(ticketRepository.countBySlaBreachedTrue());

            List<Object[]> resolutionTimes = ticketRepository.findResolutionTimes();
            if (!resolutionTimes.isEmpty()) {
                double totalHours = 0;
                for (Object[] row : resolutionTimes) {
                    Instant created = (Instant) row[0];
                    Instant resolved = (Instant) row[1];
                    if (created != null && resolved != null) {
                        totalHours += Duration.between(created, resolved).toSeconds() / 3600.0;
                    }
                }
                double avgHours = totalHours / resolutionTimes.size();
                summary.setAverageResolutionTimeHours(Math.round(avgHours * 10.0) / 10.0);
            }

            return summary;
        }

        // CUSTOMER or AGENT: return scoped metrics
        List<Ticket> accessibleTickets;
        if (isCustomer(role)) {
            accessibleTickets = ticketRepository.findByCreatedById(userId);
        } else if (isAgent(role)) {
            accessibleTickets = ticketRepository.findByAssignedToId(userId);
        } else {
            return summary;
        }

        long openCount = 0;
        long inProgressCount = 0;
        long resolvedCount = 0;
        long closedCount = 0;
        long breachedCount = 0;

        for (Ticket ticket : accessibleTickets) {
            if (ticket.getStatus() == Status.OPEN) {
                openCount++;
            } else if (ticket.getStatus() == Status.IN_PROGRESS) {
                inProgressCount++;
            } else if (ticket.getStatus() == Status.RESOLVED) {
                resolvedCount++;
            } else if (ticket.getStatus() == Status.CLOSED) {
                closedCount++;
            }

            if (ticket.isSlaBreached()) {
                breachedCount++;
            }
        }

        summary.setOpenTickets(openCount);
        summary.setInProgressTickets(inProgressCount);
        summary.setResolvedTickets(resolvedCount);
        summary.setClosedTickets(closedCount);
        summary.setTotalTickets(accessibleTickets.size());
        summary.setSlaBreachedTickets(breachedCount);

        return summary;
    }

    @Cacheable(value = "agentWorkload")
    public List<AgentWorkloadResponse> getAgentWorkload() {
        String role = getCurrentRole(getCurrentAuthentication());
        if (!isAdmin(role) && !isAgent(role)) {
            throw new AccessDeniedException("Only admins and agents can view agent workload");
        }

        List<Object[]> agentCounts = ticketRepository.countActiveTicketsByAgent(List.of(Status.OPEN, Status.IN_PROGRESS));
        java.util.Map<Long, Long> countsMap = new java.util.HashMap<>();
        for (Object[] row : agentCounts) {
            countsMap.put(((Number) row[0]).longValue(), ((Number) row[2]).longValue());
        }

        List<User> agents = userRepository.findByRole(Role.AGENT);
        if (agents.isEmpty()) {
            return agentCounts.stream()
                    .map(row -> {
                        AgentWorkloadResponse response = new AgentWorkloadResponse();
                        response.setAgentId(((Number) row[0]).longValue());
                        response.setAgentName((String) row[1]);
                        response.setAssignedTickets(((Number) row[2]).longValue());
                        return response;
                    })
                    .toList();
        }

        return agents.stream()
                .filter(User::isActive)
                .map(agent -> {
                    AgentWorkloadResponse response = new AgentWorkloadResponse();
                    response.setAgentId(agent.getId());
                    response.setAgentName(agent.getName());
                    response.setEmail(agent.getEmail());
                    response.setAssignedTickets(countsMap.getOrDefault(agent.getId(), 0L));
                    return response;
                })
                .sorted((a, b) -> Long.compare(b.getAssignedTickets(), a.getAssignedTickets()))
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
            if (ACTION_VIEW.equals(action)) {
                return;
            }
            if (ticket.getAssignedTo() != null
                    && ticket.getAssignedTo().getId().equals(userId)) {
                return;
            }
            throw new AccessDeniedException(
                    String.format("You cannot %s this ticket", action));
        }

        throw new AccessDeniedException(MESSAGE_ACCESS_DENIED);
    }

    private Instant calculateSlaDueAt(Priority priority) {
        if (priority == null) {
            return Instant.now().plus(Duration.ofHours(24));
        }
        return switch (priority) {
            case URGENT -> Instant.now().plus(Duration.ofHours(4));
            case HIGH -> Instant.now().plus(Duration.ofHours(24));
            case MEDIUM -> Instant.now().plus(Duration.ofHours(72));
            case LOW -> Instant.now().plus(Duration.ofHours(120));
        };
    }

    private TicketAuditSnapshot toSnapshot(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return new TicketAuditSnapshot(
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority() != null ? ticket.getPriority().name() : null,
                ticket.getStatus() != null ? ticket.getStatus().name() : null,
                ticket.getCreatedBy() != null ? ticket.getCreatedBy().getId() : null,
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null,
                ticket.getSlaDueAt(),
                ticket.getVersion()
        );
    }

    private UserAuditSnapshot toUserSnapshot(User user) {
        if (user == null) {
            return null;
        }
        return new UserAuditSnapshot(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
