package com.example.soundattract.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class SoundAttractNetwork {

    private static final String PROTOCOL_VERSION = "2";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(SoundMessage.TYPE, SoundMessage.STREAM_CODEC, SoundMessage::handle);
        registrar.playToClient(CamoSyncMessage.TYPE, CamoSyncMessage.STREAM_CODEC, CamoSyncMessage::handle);
        registrar.playToServer(PacketCamoRemoval.TYPE, PacketCamoRemoval.STREAM_CODEC, PacketCamoRemoval::handle);
        registrar.playToClient(AwarenessSnapshotMessage.TYPE, AwarenessSnapshotMessage.STREAM_CODEC, AwarenessSnapshotMessage::handle);
    }
}
