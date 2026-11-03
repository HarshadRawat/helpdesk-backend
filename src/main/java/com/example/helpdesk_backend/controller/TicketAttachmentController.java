package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.TicketAttachmentResponse;
import com.example.helpdesk_backend.service.TicketAttachmentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketAttachmentController {

    private final TicketAttachmentService ticketAttachmentService;

    public TicketAttachmentController(TicketAttachmentService ticketAttachmentService) {
        this.ticketAttachmentService = ticketAttachmentService;
    }

    @PostMapping(value = "/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketAttachmentResponse> uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) throws IOException {

        TicketAttachmentResponse response = ticketAttachmentService.uploadAttachment(ticketId, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ticketId}/attachments")
    public ResponseEntity<List<TicketAttachmentResponse>> getAttachments(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketAttachmentService.getAttachments(ticketId));
    }
}
