package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Used by GET /api/tickets
    List<Ticket> findByCreatedById(Long userId);

    List<Ticket> findByAssignedToId(Long userId);


    // Used by GET /api/tickets/paged
    Page<Ticket> findByCreatedById(Long userId, Pageable pageable);

    Page<Ticket> findByAssignedToId(Long userId, Pageable pageable);


    // ADMIN filtering
    Page<Ticket> findByStatus(Status status, Pageable pageable);

    Page<Ticket> findByPriority(Priority priority, Pageable pageable);

    Page<Ticket> findByStatusAndPriority(
            Status status,
            Priority priority,
            Pageable pageable
    );


    // CUSTOMER filtering
    Page<Ticket> findByCreatedByIdAndStatus(
            Long userId,
            Status status,
            Pageable pageable
    );

    Page<Ticket> findByCreatedByIdAndPriority(
            Long userId,
            Priority priority,
            Pageable pageable
    );

    Page<Ticket> findByCreatedByIdAndStatusAndPriority(
            Long userId,
            Status status,
            Priority priority,
            Pageable pageable
    );


    // AGENT filtering
    Page<Ticket> findByAssignedToIdAndStatus(
            Long userId,
            Status status,
            Pageable pageable
    );

    Page<Ticket> findByAssignedToIdAndPriority(
            Long userId,
            Priority priority,
            Pageable pageable
    );

    Page<Ticket> findByAssignedToIdAndStatusAndPriority(
            Long userId,
            Status status,
            Priority priority,
            Pageable pageable
    );
}