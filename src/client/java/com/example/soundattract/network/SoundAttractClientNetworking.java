package com.example.soundattract.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SoundAttractClientNetworking {
    private SoundAttractClientNetworking() {
    }

    public static void sendCamoRemovalToServer(PacketCamoRemoval message) {
        ClientPlayNetworking.send(message);
    }

    public static void sendSoundToServer(SoundMessage message) {
        ClientPlayNetworking.send(message);
    }
}
