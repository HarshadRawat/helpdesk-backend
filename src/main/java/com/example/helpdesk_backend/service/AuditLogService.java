package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.AuditLogResponse;
import com.example.helpdesk_backend.entity.AuditLog;
import com.example.helpdesk_backend.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.audit.retention-days:90}")
    private int retentionDays;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String action, String entityType, Long entityId, Long actorId, Object oldValue, Object newValue) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setActorId(actorId);
        auditLog.setOldValue(serialize(oldValue));
        auditLog.setNewValue(serialize(newValue));
        auditLogRepository.save(auditLog);
    }

    public List<AuditLogResponse> getTicketHistory(Long ticketId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("ticket", ticketId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public long purgeExpiredLogs() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        return auditLogRepository.deleteByCreatedAtBefore(cutoff);
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return value.toString();
        }
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(auditLog.getId());
        response.setAction(auditLog.getAction());
        response.setEntityType(auditLog.getEntityType());
        response.setEntityId(auditLog.getEntityId());
        response.setActorId(auditLog.getActorId());
        response.setOldValue(auditLog.getOldValue());
        response.setNewValue(auditLog.getNewValue());
        response.setCreatedAt(auditLog.getCreatedAt());
        return response;
    }
}
