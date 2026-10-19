package com.example.soundattract.network;

import com.example.soundattract.client.AwarenessClientState;
import com.example.soundattract.client.FromTheFogVisualCompat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;

public final class AwarenessClientNetworking {
    private static boolean registered;

    private AwarenessClientNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SoundAttractNetwork.AWARENESS_SNAPSHOT_ID,
                (client, handler, buffer, responseSender) -> {
                    AwarenessSnapshotMessage message = AwarenessSnapshotMessage.decode(buffer);
                    client.execute(() -> {
                        AwarenessClientState.apply(message);
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(SoundAttractNetwork.FROM_THE_FOG_MARKER_ID,
                (client, handler, buffer, responseSender) -> {
                    FromTheFogEquipmentMarkerMessage message = FromTheFogEquipmentMarkerMessage.decode(buffer);
                    client.execute(() -> FromTheFogVisualCompat.applyMarker(message.entityId(), message.keepEquipmentVisible()));
                });
    }
}
