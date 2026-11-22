package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c WHERE c.ticket.id = :ticketId AND c.ticket.deleted = false")
    List<Comment> findByTicketId(@Param("ticketId") Long ticketId);

    void deleteByTicketId(Long ticketId);
}
