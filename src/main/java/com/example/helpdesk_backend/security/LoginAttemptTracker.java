package com.example.helpdesk_backend.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptTracker {

    private final Duration lockWindow = Duration.ofMinutes(15);
    private final int maxAttempts = 5;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void recordFailure(String email) {
        AttemptState state = attempts.computeIfAbsent(email, key -> new AttemptState());
        synchronized (state) {
            if (Duration.between(state.lastAttempt, Instant.now()).compareTo(lockWindow) > 0) {
                state.reset();
            }
            state.count++;
            state.lastAttempt = Instant.now();
        }
    }

    public boolean isBlocked(String email) {
        AttemptState state = attempts.get(email);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (Duration.between(state.lastAttempt, Instant.now()).compareTo(lockWindow) > 0) {
                state.reset();
                return false;
            }
            return state.count >= maxAttempts;
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }

    private static class AttemptState {
        private int count = 0;
        private Instant lastAttempt = Instant.now();

        private void reset() {
            count = 0;
            lastAttempt = Instant.now();
        }
    }
}
