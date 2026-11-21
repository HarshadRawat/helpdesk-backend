package com.example.helpdesk_backend.controller;

import com.example.helpdesk_backend.dto.TicketAttachmentResponse;
import com.example.helpdesk_backend.service.TicketAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
@Tag(name = "Ticket Attachments", description = "Endpoints for uploading, listing, and securely downloading ticket file attachments")
public class TicketAttachmentController {

    private final TicketAttachmentService attachmentService;

    public TicketAttachmentController(TicketAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload attachment", description = "Uploads a file attachment to a ticket (max 10MB; validated against whitelisted MIME types).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attachment uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or prohibited MIME type"),
            @ApiResponse(responseCode = "403", description = "Access denied: ticket not owned or assigned to user"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public TicketAttachmentResponse uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) {
        return attachmentService.uploadAttachment(ticketId, file);
    }

    @GetMapping
    @Operation(summary = "List attachments", description = "Returns metadata for all file attachments on the specified ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of attachments retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    public List<TicketAttachmentResponse> getAttachments(@PathVariable Long ticketId) {
        return attachmentService.getAttachments(ticketId);
    }

    @GetMapping("/{attachmentId}")
    @Operation(summary = "Download attachment", description = "Streams the attachment resource with content disposition headers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File binary stream"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Attachment or ticket not found")
    })
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId) {
        TicketAttachmentService.AttachmentDownload download = attachmentService.loadAttachment(ticketId, attachmentId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .body(download.resource());
    }
}
