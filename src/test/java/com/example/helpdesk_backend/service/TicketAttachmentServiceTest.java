package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.TicketAttachmentResponse;
import com.example.helpdesk_backend.entity.Ticket;
import com.example.helpdesk_backend.entity.TicketAttachment;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.repository.TicketAttachmentRepository;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketAttachmentServiceTest {

    @Mock
    private TicketAttachmentRepository attachmentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @TempDir
    Path tempUploadDir;

    private TicketAttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new TicketAttachmentService(
                attachmentRepository,
                ticketRepository,
                userRepository,
                tempUploadDir.toString()
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "10",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUploadValidAttachmentForTicketOwner() {
        User customer = new User();
        ReflectionTestUtils.setField(customer, "id", 10L);
        customer.setName("Customer");
        customer.setRole(Role.CUSTOMER);

        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 1L);
        ticket.setCreatedBy(customer);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(attachmentRepository.save(any(TicketAttachment.class))).thenAnswer(invocation -> {
            TicketAttachment att = invocation.getArgument(0);
            ReflectionTestUtils.setField(att, "id", 99L);
            return att;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "error_log.txt",
                "text/plain",
                "System Out of Memory log details".getBytes()
        );

        TicketAttachmentResponse response = attachmentService.uploadAttachment(1L, file);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals("error_log.txt", response.getFileName());
        assertEquals("text/plain", response.getContentType());
        verify(attachmentRepository).save(any(TicketAttachment.class));
    }

    @Test
    void shouldRejectPathTraversalInFilename() {
        MockMultipartFile dangerousFile = new MockMultipartFile(
                "file",
                "../../etc/passwd",
                "text/plain",
                "dangerous".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadAttachment(1L, dangerousFile));
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectDisallowedContentType() {
        MockMultipartFile executableFile = new MockMultipartFile(
                "file",
                "malware.exe",
                "application/x-msdownload",
                "malware".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> attachmentService.uploadAttachment(1L, executableFile));
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCustomerAccessingAnotherCustomersTicket() {
        User otherCustomer = new User();
        ReflectionTestUtils.setField(otherCustomer, "id", 20L);

        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 2L);
        ticket.setCreatedBy(otherCustomer);

        when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "screenshot.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        assertThrows(AccessDeniedException.class, () -> attachmentService.uploadAttachment(2L, file));
        verify(attachmentRepository, never()).save(any());
    }
}
