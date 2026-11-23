package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    // Used by GET /api/tickets
    List<Ticket> findByCreatedById(Long userId);

    List<Ticket> findByAssignedToId(Long userId);

    @Query("SELECT t FROM Ticket t WHERE t.slaDueAt < :now AND t.slaBreached = false AND t.status NOT IN :excludedStatuses")
    List<Ticket> findBreachedTickets(
            @Param("now") Instant now,
            @Param("excludedStatuses") List<Status> excludedStatuses
    );

    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countTicketsByStatus();

    @Query("SELECT t.priority, COUNT(t) FROM Ticket t GROUP BY t.priority")
    List<Object[]> countTicketsByPriority();

    @Query("SELECT t.assignedTo.id, t.assignedTo.name, COUNT(t) FROM Ticket t WHERE t.assignedTo IS NOT NULL AND t.status IN :activeStatuses GROUP BY t.assignedTo.id, t.assignedTo.name ORDER BY COUNT(t) DESC")
    List<Object[]> countActiveTicketsByAgent(@Param("activeStatuses") List<Status> activeStatuses);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN (com.example.helpdesk_backend.enums.Status.RESOLVED, com.example.helpdesk_backend.enums.Status.CLOSED)")
    long countResolvedOrClosedTickets();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN (com.example.helpdesk_backend.enums.Status.RESOLVED, com.example.helpdesk_backend.enums.Status.CLOSED) AND t.slaBreached = false")
    long countSlaCompliantTickets();

    long countBySlaBreachedTrue();

    @Query("SELECT t.createdAt, t.resolvedAt FROM Ticket t WHERE t.resolvedAt IS NOT NULL")
    List<Object[]> findResolutionTimes();
}