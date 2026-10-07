package com.example.soundattract.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AwarenessSnapshotMessageTest {
    @Test
    void alertEntityIdSurvivesNetworkRoundTrip() {
        UUID mobId = UUID.randomUUID();
        AwarenessSnapshotMessage message = new AwarenessSnapshotMessage(
                0.25f,
                0.75f,
                4,
                true,
                List.of(new AwarenessSnapshotMessage.Alert(mobId, 12345, 5, 0xFFFFC040, 1.0f)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AwarenessSnapshotMessage.encode(message, buffer);
            AwarenessSnapshotMessage decoded = AwarenessSnapshotMessage.decode(buffer);
            AwarenessSnapshotMessage.Alert alert = decoded.alerts().get(0);
            assertEquals(mobId, alert.mobId());
            assertEquals(12345, alert.entityId());
            assertEquals(5, alert.type());
            assertEquals(0xFFFFC040, alert.color());
        } finally {
            buffer.release();
        }
    }
}
