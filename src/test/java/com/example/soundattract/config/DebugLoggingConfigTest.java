package com.example.soundattract.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DebugLoggingConfigTest {
    @Test
    void unloadedDebugConfigDefaultsToDisabled() {
        assertDoesNotThrow(() -> DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE));
        assertFalse(DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE));
    }

    @Test
    void nullFeatureDefaultsToDisabled() {
        assertFalse(DebugLoggingConfig.isEnabled(null));
    }
}
