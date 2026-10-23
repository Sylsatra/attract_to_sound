package com.example.soundattract.event;

import com.example.soundattract.network.CamoSyncClientHandler;
import com.example.soundattract.network.CamoSyncMessage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SoundAttractFabricClientEvents {
    private SoundAttractFabricClientEvents() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CamoSyncMessage.ID, (message, context) ->
                context.client().execute(() -> CamoSyncClientHandler.apply(message, context.client())));
    }
}
