package com.example.choosecourse.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoModeGuardTest {

    @Test
    void failsWhenDemoModeIsEnabled() {
        assertThrows(IllegalStateException.class, () -> new DemoModeGuard(true, false).run(null));
        assertThrows(IllegalStateException.class, () -> new DemoModeGuard(false, true).run(null));
    }

    @Test
    void passesWhenDemoModeIsDisabled() {
        assertDoesNotThrow(() -> new DemoModeGuard(false, false).run(null));
    }
}
