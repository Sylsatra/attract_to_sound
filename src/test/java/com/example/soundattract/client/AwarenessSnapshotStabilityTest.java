package com.example.soundattract.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.soundattract.network.AwarenessSnapshotMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AwarenessSnapshotStabilityTest {
    @AfterEach
    void clearState() {
        AwarenessClientState.clear();
    }

    @Test
    void oneMissingSnapshotDoesNotBlinkAnOtherwiseActiveMarker() {
        UUID mobId = UUID.randomUUID();
        long now = 10_000L;
        AwarenessClientState.applyAt(snapshot(new AwarenessSnapshotMessage.Alert(
                mobId, 17, 2, 0xFFFF3030, 1.0f)), now);
        long originalStartedAt = AwarenessClientState.alert(mobId).startedAt();

        AwarenessClientState.applyAt(snapshot(), now + 250L);

        AwarenessClientState.MobAlert retained = AwarenessClientState.alert(mobId);
        assertNotNull(retained, "An alert should survive a single full-snapshot scan gap");
        assertEquals(originalStartedAt, retained.startedAt(), "A brief gap must not restart the animation");
        assertTrue(AwarenessClientState.shouldRetainMissingAlert(retained.lastSeenAt(), retained.lastSeenAt() + 750L));
        assertFalse(AwarenessClientState.shouldRetainMissingAlert(retained.lastSeenAt(), retained.lastSeenAt() + 751L));

        AwarenessClientState.applyAt(snapshot(), retained.lastSeenAt() + 751L);
        assertNull(AwarenessClientState.alert(mobId), "A sustained missing alert should eventually be removed");
    }

    @Test
    void everyContinuousAlertKeepsItsAnimationAgeAcrossSnapshotsAndRestartsOnTypeChange() {
        UUID mobId = UUID.randomUUID();
        for (int type = 1; type <= 5; type++) {
            AwarenessClientState.clear();
            AwarenessClientState.applyAt(snapshot(new AwarenessSnapshotMessage.Alert(mobId, 17, type, -1, 0)), 10_000L);
            AwarenessClientState.applyAt(snapshot(new AwarenessSnapshotMessage.Alert(mobId, 17, type, -1, 1)), 10_250L);
            assertEquals(10_000L, AwarenessClientState.alert(mobId).startedAt(), "Snapshot must not restart type " + type);
            int nextType = type % 5 + 1;
            AwarenessClientState.applyAt(snapshot(new AwarenessSnapshotMessage.Alert(mobId, 17, nextType, -1, 0)), 10_500L);
            assertEquals(10_500L, AwarenessClientState.alert(mobId).startedAt(), "New type must start a new animation");
        }
    }

    @Test
    void aNewNearMissRestartsItsFinishedFadeWithoutRestartingEverySnapshot() {
        UUID mobId = UUID.randomUUID();
        var first = new AwarenessSnapshotMessage.Alert(mobId, 17, 3, 0xFFFFB000, 0, 100L);
        AwarenessClientState.applyAt(snapshot(first), 10_000L);
        AwarenessClientState.applyAt(snapshot(first), 11_500L);
        assertEquals(10_000L, AwarenessClientState.alert(mobId).startedAt());
        AwarenessClientState.applyAt(snapshot(), 11_750L); // retained by the missing-snapshot grace
        var second = new AwarenessSnapshotMessage.Alert(mobId, 17, 3, 0xFFFFB000, 0, 135L);
        AwarenessClientState.applyAt(snapshot(second), 12_000L);
        assertEquals(12_000L, AwarenessClientState.alert(mobId).startedAt(), "A new near miss must become visible again");
        AwarenessClientState.applyAt(snapshot(second), 12_250L);
        assertEquals(12_000L, AwarenessClientState.alert(mobId).startedAt(), "Same event must continue animating");
    }

    @Test
    void alertCacheNeverExceedsItsConfiguredCapacity() {
        long now = 10_000L;
        for (int batch = 0; batch < 33; batch++) {
            List<AwarenessSnapshotMessage.Alert> alerts = new ArrayList<>(128);
            for (int index = 0; index < 128; index++) {
                int identity = batch * 128 + index;
                UUID mobId = UUID.nameUUIDFromBytes(("awareness-cache-" + identity).getBytes(StandardCharsets.UTF_8));
                alerts.add(new AwarenessSnapshotMessage.Alert(mobId, identity, 2, 0xFFFF3030, 1.0f));
            }
            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0.0f, 0.0f, 0, false, alerts), now);
        }

        var renderAlerts = AwarenessClientState.alerts();
        int retainedAlerts = renderAlerts.size();
        assertTrue(retainedAlerts > 0, "The alert cache should retain entries after capacity pressure");
        assertTrue(retainedAlerts <= 4096, "The Caffeine alert cache must honor its 4,096-entry maximum");
        for (var entry : renderAlerts) {
            assertEquals(entry.getValue(), AwarenessClientState.alert(entry.getKey()),
                    "The renderer snapshot must not retain an alert evicted from the live cache");
        }
    }

    @Test
    void alertCacheExpiresEntriesAfterWriteWithoutAnotherSnapshot() throws InterruptedException {
        UUID mobId = UUID.randomUUID();
        AwarenessClientState.apply(snapshot(new AwarenessSnapshotMessage.Alert(
                mobId, 17, 2, 0xFFFF3030, 1.0f)));
        assertNotNull(AwarenessClientState.alert(mobId));

        Thread.sleep(2700L);

        assertNull(AwarenessClientState.alert(mobId), "An alert should expire after the 2.5-second cache TTL");
        assertTrue(AwarenessClientState.alerts().isEmpty(), "Expired cache entries must leave the render snapshot");
    }

    private static AwarenessSnapshotMessage snapshot(AwarenessSnapshotMessage.Alert... alerts) {
        return new AwarenessSnapshotMessage(0.0f, 0.0f, 0, false, List.of(alerts));
    }
}
