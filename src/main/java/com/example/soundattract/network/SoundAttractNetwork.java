package com.example.soundattract.network;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SoundAttractNetwork {

    private static final String PROTOCOL_VERSION = "3";
    public static SimpleChannel INSTANCE;

    public static void register() {
        INSTANCE = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "network"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

        int id = 0;

        INSTANCE.messageBuilder(SoundMessage.class, id++)
                .encoder(SoundMessage::encode)
                .decoder(SoundMessage::decode)
                .consumerMainThread(SoundMessage::handle)
                .add();

        INSTANCE.messageBuilder(CamoSyncMessage.class, id++)
                .encoder(CamoSyncMessage::encode)
                .decoder(CamoSyncMessage::decode)
                .consumerMainThread(CamoSyncMessage::handle)
                .add();

        INSTANCE.messageBuilder(PacketCamoRemoval.class, id++)
                .encoder(PacketCamoRemoval::toBytes)
                .decoder(PacketCamoRemoval::new)
                .consumerMainThread(PacketCamoRemoval::handle)
                .add();

        INSTANCE.messageBuilder(AwarenessSnapshotMessage.class, id++)
                .encoder(AwarenessSnapshotMessage::encode)
                .decoder(AwarenessSnapshotMessage::decode)
                .consumerMainThread(AwarenessSnapshotMessage::handle)
                .add();

        INSTANCE.messageBuilder(FromTheFogEquipmentMarkerMessage.class, id++)
                .encoder(FromTheFogEquipmentMarkerMessage::encode)
                .decoder(FromTheFogEquipmentMarkerMessage::decode)
                .consumerMainThread(FromTheFogEquipmentMarkerMessage::handle)
                .add();
    }

}
