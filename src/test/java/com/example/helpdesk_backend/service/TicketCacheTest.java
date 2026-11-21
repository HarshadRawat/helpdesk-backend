package com.example.helpdesk_backend.service;

import com.example.helpdesk_backend.dto.TicketRequest;
import com.example.helpdesk_backend.dto.TicketSummaryResponse;
import com.example.helpdesk_backend.entity.User;
import com.example.helpdesk_backend.enums.Priority;
import com.example.helpdesk_backend.enums.Role;
import com.example.helpdesk_backend.repository.CommentRepository;
import com.example.helpdesk_backend.repository.TicketAttachmentRepository;
import com.example.helpdesk_backend.repository.TicketRepository;
import com.example.helpdesk_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TicketCacheTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        admin = new User();
        admin.setName("Admin User");
        admin.setEmail("admin-cache@example.com");
        admin.setRole(Role.ADMIN);
        admin = userRepository.save(admin);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                admin.getId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        Cache cache = cacheManager.getCache("ticketSummary");
        if (cache != null) {
            cache.clear();
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCacheSummaryAndEvictOnTicketCreation() {
        Cache cache = cacheManager.getCache("ticketSummary");
        assertNotNull(cache);

        TicketSummaryResponse firstSummary = ticketService.getTicketSummary();
        assertNotNull(firstSummary);

        Cache.ValueWrapper cached = cache.get(new org.springframework.cache.interceptor.SimpleKey());
        assertNotNull(cached, "Summary should be cached after first query");

        TicketRequest request = new TicketRequest();
        request.setTitle("New Cache Invalidation Ticket");
        request.setDescription("Testing cache eviction");
        request.setPriority(Priority.HIGH);

        ticketService.createTicket(request);

        Cache.ValueWrapper evicted = cache.get(new org.springframework.cache.interceptor.SimpleKey());
        assertNull(evicted, "Cache should be invalidated after ticket mutation");

        TicketSummaryResponse secondSummary = ticketService.getTicketSummary();
        assertEquals(firstSummary.getTotalTickets() + 1, secondSummary.getTotalTickets());
    }
}
