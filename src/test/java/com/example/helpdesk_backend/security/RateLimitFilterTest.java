package com.example.helpdesk_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(2, 60);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldNotFilterOperationalRoutes() {
        MockHttpServletRequest ticketReq = new MockHttpServletRequest("GET", "/api/tickets");
        assertTrue(rateLimitFilter.shouldNotFilter(ticketReq));

        MockHttpServletRequest summaryReq = new MockHttpServletRequest("GET", "/api/tickets/summary");
        assertTrue(rateLimitFilter.shouldNotFilter(summaryReq));

        MockHttpServletRequest getMeReq = new MockHttpServletRequest("GET", "/api/users");
        assertTrue(rateLimitFilter.shouldNotFilter(getMeReq));

        MockHttpServletRequest swaggerReq = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        assertTrue(rateLimitFilter.shouldNotFilter(swaggerReq));
    }

    @Test
    void shouldFilterAuthAndSignupRoutes() {
        MockHttpServletRequest loginReq = new MockHttpServletRequest("POST", "/api/auth/login");
        assertFalse(rateLimitFilter.shouldNotFilter(loginReq));

        MockHttpServletRequest refreshReq = new MockHttpServletRequest("POST", "/api/auth/refresh");
        assertFalse(rateLimitFilter.shouldNotFilter(refreshReq));

        MockHttpServletRequest forgotReq = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
        assertFalse(rateLimitFilter.shouldNotFilter(forgotReq));

        MockHttpServletRequest signupReq = new MockHttpServletRequest("POST", "/api/users");
        assertFalse(rateLimitFilter.shouldNotFilter(signupReq));
    }

    @Test
    void shouldRateLimitAfterExceedingThresholdForSameIp() throws ServletException, IOException {
        MockHttpServletRequest request1 = new MockHttpServletRequest("POST", "/api/auth/login");
        request1.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request1, response1, filterChain);
        assertEquals(200, response1.getStatus());

        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/auth/login");
        request2.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request2, response2, filterChain);
        assertEquals(200, response2.getStatus());

        // 3rd attempt exceeds maxRequests = 2
        MockHttpServletRequest request3 = new MockHttpServletRequest("POST", "/api/auth/login");
        request3.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response3 = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request3, response3, filterChain);
        assertEquals(429, response3.getStatus());
        assertTrue(response3.getContentAsString().contains("Too Many Requests"));
    }

    @Test
    void shouldIsolateUsersByRemoteAddress() throws ServletException, IOException {
        MockHttpServletRequest aliceReq1 = new MockHttpServletRequest("POST", "/api/auth/login");
        aliceReq1.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse aliceRes1 = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(aliceReq1, aliceRes1, filterChain);
        assertEquals(200, aliceRes1.getStatus());

        MockHttpServletRequest aliceReq2 = new MockHttpServletRequest("POST", "/api/auth/login");
        aliceReq2.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse aliceRes2 = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(aliceReq2, aliceRes2, filterChain);
        assertEquals(200, aliceRes2.getStatus());

        // Alice is now at limit (2). Bob sends a request from different IP:
        MockHttpServletRequest bobReq = new MockHttpServletRequest("POST", "/api/auth/login");
        bobReq.setRemoteAddr("198.51.100.25");
        MockHttpServletResponse bobRes = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(bobReq, bobRes, filterChain);
        assertEquals(200, bobRes.getStatus()); // Bob is NOT blocked by Alice's quota!
    }
}
