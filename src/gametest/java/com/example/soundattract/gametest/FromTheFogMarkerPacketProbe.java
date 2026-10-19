package com.example.soundattract.gametest;

import com.example.soundattract.network.FromTheFogEquipmentMarkerMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;

/** Captures real outbound From The Fog marker payloads only during its focused GameTest. */
public final class FromTheFogMarkerPacketProbe {
    public record MarkerPacket(UUID entityId, boolean keepEquipmentVisible) {}

    private static final List<MarkerPacket> CAPTURED = new CopyOnWriteArrayList<>();
    private static volatile boolean enabled;

    private FromTheFogMarkerPacketProbe() {}

    public static void begin() {
        CAPTURED.clear();
        enabled = true;
    }

    public static List<MarkerPacket> finish() {
        enabled = false;
        List<MarkerPacket> result = List.copyOf(CAPTURED);
        CAPTURED.clear();
        return result;
    }

    public static void capture(Packet<?> packet) {
        if (!enabled || !(packet instanceof ClientboundCustomPayloadPacket customPayload)
                || !SoundAttractNetwork.FROM_THE_FOG_MARKER_ID.equals(customPayload.getIdentifier())) {
            return;
        }
        FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.copiedBuffer(customPayload.getData()));
        try {
            FromTheFogEquipmentMarkerMessage message = FromTheFogEquipmentMarkerMessage.decode(copy);
            CAPTURED.add(new MarkerPacket(message.entityId(), message.keepEquipmentVisible()));
        } finally {
            copy.release();
        }
    }
}
