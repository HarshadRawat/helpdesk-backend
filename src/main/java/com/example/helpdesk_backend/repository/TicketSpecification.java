package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Status;
import org.springframework.data.jpa.domain.Specification;

public class TicketSpecification {

    public static Specification<Ticket> createdBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), userId);
    }

    public static Specification<Ticket> assignedTo(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), userId);
    }

    public static Specification<Ticket> hasStatus(Status status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Ticket> hasPriority(Priority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Ticket> searchInTitle(String keyword) {
        return (root, query, cb) -> (keyword == null || keyword.isBlank())
                ? null
                : cb.like(cb.lower(root.get("title")), "%" + keyword.trim().toLowerCase() + "%");
    }
}
