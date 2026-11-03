package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.AuditLogResponse;
import com.example.helpdesk_backend.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/tickets/{ticketId}")
    public List<AuditLogResponse> getTicketHistory(@PathVariable Long ticketId) {
        return auditLogService.getTicketHistory(ticketId);
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldAuditLogs() {
        long deleted = auditLogService.purgeExpiredLogs();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deletedRecords", deleted);
        payload.put("retentionDays", auditLogService.getRetentionDays());
        return ResponseEntity.ok(payload);
    }
}
