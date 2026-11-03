package com.example.helpdesk_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestIdFilterTest {

    @Test
    void shouldGenerateAndAttachRequestId() throws ServletException, IOException {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletRequest req = (HttpServletRequest) servletRequest;
            assertNotNull(req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
            assertEquals(req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE), response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
        });

        assertNotNull(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }
}
