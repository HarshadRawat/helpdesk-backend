package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.TicketAttachmentResponse;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.TicketAttachment;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.exception.TicketNotFoundException;
import com.example.helpdesk_backend.exception.UserNotFoundException;
import com.example.helpdesk_backend.repository.TicketAttachmentRepository;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketAttachmentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf",
            "text/plain"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".pdf",
            ".txt"
    );

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final Path uploadStorageLocation;

    public TicketAttachmentService(
            TicketAttachmentRepository attachmentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            @Value("${app.storage.upload-dir:./uploads}") String uploadDir) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.uploadStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadStorageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory", e);
        }
    }

    @Transactional
    public TicketAttachmentResponse uploadAttachment(Long ticketId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Filename contains invalid path sequence: " + originalFilename);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        Authentication auth = getCurrentAuthentication();
        Long userId = getCurrentUserId(auth);
        String role = getCurrentRole(auth);
        assertCanAccessTicket(ticket, userId, role, "upload attachments for");

        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file extension: " + extension);
        }

        String storedFileName = UUID.randomUUID() + extension;
        Path targetLocation = this.uploadStorageLocation.resolve(storedFileName).normalize();

        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store file on disk", ex);
        }

        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicket(ticket);
        attachment.setFileName(originalFilename);
        attachment.setContentType(contentType);
        attachment.setSize(file.getSize());
        attachment.setStoragePath(targetLocation.toString());
        attachment.setUploadedBy(uploader);

        TicketAttachment saved = attachmentRepository.save(attachment);
        return toResponse(saved);
    }

    public List<TicketAttachmentResponse> getAttachments(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        Authentication auth = getCurrentAuthentication();
        assertCanAccessTicket(ticket, getCurrentUserId(auth), getCurrentRole(auth), "view attachments for");

        return attachmentRepository.findByTicketId(ticketId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AttachmentDownload loadAttachment(Long ticketId, Long attachmentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        Authentication auth = getCurrentAuthentication();
        assertCanAccessTicket(ticket, getCurrentUserId(auth), getCurrentRole(auth), "download attachments from");

        TicketAttachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));

        try {
            Path filePath = Paths.get(attachment.getStoragePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("File not found or not readable: " + attachment.getFileName());
            }
            return new AttachmentDownload(resource, attachment.getFileName(), attachment.getContentType());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Could not read file path", ex);
        }
    }

    private void assertCanAccessTicket(Ticket ticket, Long userId, String role, String action) {
        if ("ROLE_ADMIN".equals(role) || "ROLE_AGENT".equals(role)) {
            return;
        }

        if ("ROLE_CUSTOMER".equals(role)) {
            if (ticket.getCreatedBy() != null && ticket.getCreatedBy().getId().equals(userId)) {
                return;
            }
            throw new AccessDeniedException("You cannot " + action + " this ticket");
        }

        throw new AccessDeniedException("Access denied");
    }

    private Authentication getCurrentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("Access denied");
        }
        return auth;
    }

    private Long getCurrentUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private String getCurrentRole(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
    }

    private TicketAttachmentResponse toResponse(TicketAttachment attachment) {
        TicketAttachmentResponse response = new TicketAttachmentResponse();
        response.setId(attachment.getId());
        response.setTicketId(attachment.getTicket().getId());
        response.setFileName(attachment.getFileName());
        response.setContentType(attachment.getContentType());
        response.setSize(attachment.getSize());
        response.setUploadedAt(attachment.getUploadedAt());
        response.setUploadedById(attachment.getUploadedBy().getId());
        response.setUploadedByName(attachment.getUploadedBy().getName());
        return response;
    }

    public record AttachmentDownload(Resource resource, String fileName, String contentType) {
    }
}
