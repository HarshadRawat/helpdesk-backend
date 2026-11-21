package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.AuditLogResponse;
import com.example.helpdesk_backend.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Audit Logging", description = "Administrative audit trail exploration and retention purging")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Get ticket audit trail", description = "Retrieves chronological lifecycle and status audit events for a ticket.")
    public List<AuditLogResponse> getTicketHistory(@PathVariable Long ticketId) {
        return auditLogService.getTicketHistory(ticketId);
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "Purge expired audit logs", description = "Deletes audit records older than the configured retention period (default: 90 days). Restricted to ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expired audit logs cleaned up")
    })
    public ResponseEntity<Map<String, Object>> cleanupOldAuditLogs() {
        long deleted = auditLogService.purgeExpiredLogs();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deletedRecords", deleted);
        payload.put("retentionDays", auditLogService.getRetentionDays());
        return ResponseEntity.ok(payload);
    }
}
