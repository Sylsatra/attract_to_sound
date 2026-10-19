package com.example.soundattract.event;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StealthDetectionNullSafetyTest {
    @Test
    void nullDetectionInputFallsBackWithoutRequiringInitializedConfig() {
        assertTrue(StealthDetectionEvents.canMobDetectLivingEntity(null, null));
    }
}
