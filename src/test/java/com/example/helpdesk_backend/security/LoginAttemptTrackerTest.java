package com.example.helpdesk_backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptTrackerTest {

    @Test
    void shouldBlockAfterThresholdFailures() {
        LoginAttemptTracker tracker = new LoginAttemptTracker();

        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("user@example.com");
        }

        assertTrue(tracker.isBlocked("user@example.com"));
    }

    @Test
    void shouldResetAfterSuccess() {
        LoginAttemptTracker tracker = new LoginAttemptTracker();

        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("user@example.com");
        }

        tracker.recordSuccess("user@example.com");

        assertFalse(tracker.isBlocked("user@example.com"));
    }
}
