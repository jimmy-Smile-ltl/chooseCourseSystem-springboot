package com.example.choosecourse.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoModeGuardTest {

    @Test
    void failsWhenDemoModeIsEnabledWithoutPublicConfirmation() {
        assertThrows(IllegalStateException.class,
                () -> new DemoModeGuard(true, false, false).run(null));
        assertThrows(IllegalStateException.class,
                () -> new DemoModeGuard(false, true, false).run(null));
    }

    @Test
    void passesWhenPublicDemoIsExplicitlyEnabled() {
        assertDoesNotThrow(() -> new DemoModeGuard(true, true, true).run(null));
    }

    @Test
    void passesWhenDemoModeIsDisabled() {
        assertDoesNotThrow(() -> new DemoModeGuard(false, false, false).run(null));
    }
}
