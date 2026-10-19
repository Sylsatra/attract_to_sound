package com.example.soundattract.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.soundattract.network.AwarenessSnapshotMessage;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.junit.jupiter.api.Test;

class AwarenessDisconnectTest {
    @Test
    void registeredDisconnectCallbackClearsAwarenessAndFromTheFogState() {
        AwarenessClientState.clear();
        FromTheFogVisualCompat.clear();
        UUID mobId = UUID.randomUUID();
        AwarenessClientState.apply(new AwarenessSnapshotMessage(0.8f, 0.4f, 3, true,
                List.of(new AwarenessSnapshotMessage.Alert(mobId, 17, 2, 0xFFFF3030, 1.0f))));
        FromTheFogVisualCompat.applyMarker(mobId, true);
        assertTrue(FromTheFogVisualCompat.isEquipmentModelEntity(mobId));

        AwarenessClientEvents.registerDisconnectCleanup();
        ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(null, null);

        assertEquals(0.0f, AwarenessClientState.visibility());
        assertEquals(0.0f, AwarenessClientState.noise());
        assertEquals(0, AwarenessClientState.scent());
        assertFalse(AwarenessClientState.windshielded());
        assertNull(AwarenessClientState.alert(mobId));
        assertFalse(FromTheFogVisualCompat.isEquipmentModelEntity(mobId));
    }
}
