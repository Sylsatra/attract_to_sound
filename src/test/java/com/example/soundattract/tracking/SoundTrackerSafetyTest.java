package com.example.soundattract.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundTrackerSafetyTest {
    @Test
    void nearbySoundQueryRejectsMalformedIntegrationInput() {
        assertTrue(SoundTracker.getNearbySounds(null, null, null, null).isEmpty());
    }
}
