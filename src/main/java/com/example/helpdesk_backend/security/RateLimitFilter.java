package com.example.helpdesk_backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final Duration window;
    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(
            @Value("${app.rate-limit.max-requests:10}") int maxRequests,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }

        RateBucket bucket = buckets.computeIfAbsent(clientIp, key -> new RateBucket());
        Instant now = Instant.now();

        synchronized (bucket) {
            if (bucket.isExpired(now, window)) {
                bucket.reset(now);
            }

            if (bucket.count >= maxRequests) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("timestamp", Instant.now().toString());
                payload.put("status", 429);
                payload.put("error", "Too many requests");
                payload.put("message", "Too many requests. Please try again later.");
                payload.put("path", request.getRequestURI());

                response.getWriter().write(objectMapper.writeValueAsString(payload));
                return;
            }

            bucket.count++;
            bucket.windowStart = now;
        }

        filterChain.doFilter(request, response);
    }

    private static class RateBucket {
        private Instant windowStart = Instant.now();
        private int count = 0;

        private boolean isExpired(Instant now, Duration window) {
            return Duration.between(windowStart, now).compareTo(window) > 0;
        }

        private void reset(Instant now) {
            this.windowStart = now;
            this.count = 0;
        }
    }
}
