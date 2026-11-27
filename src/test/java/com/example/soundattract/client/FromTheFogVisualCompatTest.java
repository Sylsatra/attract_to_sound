package com.example.soundattract.client;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FromTheFogVisualCompatTest {
    @Test
    void equipmentMarkerTracksAndClearsEntityVisibility() {
        UUID entityId = UUID.randomUUID();
        try {
            assertFalse(FromTheFogVisualCompat.hasMarker(entityId));
            FromTheFogVisualCompat.applyMarker(entityId, true);
            assertTrue(FromTheFogVisualCompat.hasMarker(entityId));
            FromTheFogVisualCompat.applyMarker(entityId, false);
            assertFalse(FromTheFogVisualCompat.hasMarker(entityId));
        } finally {
            FromTheFogVisualCompat.applyMarker(entityId, false);
        }
    }
}
