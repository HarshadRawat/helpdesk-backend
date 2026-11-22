package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    List<TicketAttachment> findByTicketId(Long ticketId);

    Optional<TicketAttachment> findByIdAndTicketId(Long id, Long ticketId);

    void deleteByTicketId(Long ticketId);
}
