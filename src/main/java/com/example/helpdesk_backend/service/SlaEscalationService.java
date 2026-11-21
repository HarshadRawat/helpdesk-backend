package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.enums.Status;
import com.example.helpdesk_backend.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    public SlaEscalationService(TicketRepository ticketRepository, AuditLogService auditLogService) {
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
    }

    @Scheduled(cron = "${app.sla.check-cron:0 */15 * * * *}")
    @Transactional
    public int checkAndEscalateBreachedTickets() {
        Instant now = Instant.now();
        List<Ticket> breachedTickets = ticketRepository.findBreachedTickets(
                now,
                List.of(Status.RESOLVED, Status.CLOSED)
        );

        if (breachedTickets.isEmpty()) {
            return 0;
        }

        log.info("Detected {} tickets breaching SLA", breachedTickets.size());
        for (Ticket ticket : breachedTickets) {
            ticket.setSlaBreached(true);
            ticketRepository.save(ticket);
            auditLogService.log("SLA_BREACH", "ticket", ticket.getId(), 0L, "SLA_MET", "SLA_BREACHED");
            log.warn("Ticket #{} has breached SLA. Due at: {}", ticket.getId(), ticket.getSlaDueAt());
        }

        return breachedTickets.size();
    }
}
