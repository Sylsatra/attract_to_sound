package com.example.soundattract.gametest;

import com.example.soundattract.network.FromTheFogEquipmentMarkerMessage;
import com.example.soundattract.network.SoundAttractNetwork;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

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
                || !(customPayload.payload() instanceof FromTheFogEquipmentMarkerMessage message)
                || !SoundAttractNetwork.FROM_THE_FOG_MARKER_ID.equals(message.type().id())) {
            return;
        }
        CAPTURED.add(new MarkerPacket(message.entityId(), message.keepEquipmentVisible()));
    }
}




