package com.example.soundattract.network;

import com.example.soundattract.client.AwarenessClientState;
import com.example.soundattract.client.FromTheFogVisualCompat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AwarenessClientNetworking {
    private static boolean registered;
    private AwarenessClientNetworking() {}
    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(AwarenessSnapshotMessage.TYPE, (message, context) ->
                context.client().execute(() -> AwarenessClientState.apply(message)));
        ClientPlayNetworking.registerGlobalReceiver(FromTheFogEquipmentMarkerMessage.TYPE, (message, context) ->
                context.client().execute(() -> FromTheFogVisualCompat.applyMarker(message.entityId(), message.keepEquipmentVisible())));
    }
}
