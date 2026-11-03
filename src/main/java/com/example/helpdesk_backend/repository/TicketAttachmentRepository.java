package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    List<TicketAttachment> findByTicket(Ticket ticket);
}
