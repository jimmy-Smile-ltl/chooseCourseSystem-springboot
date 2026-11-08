package com.example.choosecourse.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService();

    @Test
    void blocksAfterFifteenAttempts() {
        for (int i = 0; i < 15; i++) {
            service.record("127.0.0.1");
        }
        assertTrue(service.isBlocked("127.0.0.1"));
        assertFalse(service.isBlocked("127.0.0.2"));
    }

    @Test
    void resetUnblocks() {
        for (int i = 0; i < 15; i++) {
            service.record("127.0.0.1");
        }
        service.reset("127.0.0.1");
        assertFalse(service.isBlocked("127.0.0.1"));
    }
}
