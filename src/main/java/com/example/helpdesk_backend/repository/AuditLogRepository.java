package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    long deleteByCreatedAtBefore(Instant cutoff);
}
