package com.example.soundattract.network;

import com.example.soundattract.Soundattract;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class SoundAttractNetwork {

    public static final ResourceLocation AWARENESS_SNAPSHOT_ID = ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "awareness_snapshot");
    public static final ResourceLocation FROM_THE_FOG_MARKER_ID = ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "from_the_fog_marker");
    public static final ResourceLocation SOUND_MESSAGE_ID = ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "sound_message");

    public static void register() {
        PayloadTypeRegistry.playC2S().register(SoundMessage.TYPE, SoundMessage.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CamoSyncMessage.ID, CamoSyncMessage.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketCamoRemoval.TYPE, PacketCamoRemoval.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AwarenessSnapshotMessage.TYPE, AwarenessSnapshotMessage.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FromTheFogEquipmentMarkerMessage.TYPE, FromTheFogEquipmentMarkerMessage.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SoundMessage.TYPE, (msg, context) -> {
            context.player().getServer().execute(() -> SoundMessage.handle(msg, context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(PacketCamoRemoval.TYPE, (msg, context) -> {
            context.player().getServer().execute(() -> PacketCamoRemoval.handle(msg, context.player()));
        });

        Soundattract.LOGGER.info("Network packets registered for 1.21.1");
    }

    public static void sendToTrackingAndSelf(LivingEntity entity, CamoSyncMessage msg) {
        if (entity.level().isClientSide) return;
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, msg);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, msg);
        }
    }

    public static void sendAwarenessSnapshot(ServerPlayer player, AwarenessSnapshotMessage message) {
        if (player != null && ServerPlayNetworking.canSend(player, AwarenessSnapshotMessage.TYPE)) {
            ServerPlayNetworking.send(player, message);
        }
    }

    public static void sendFromTheFogMarker(ServerPlayer player, FromTheFogEquipmentMarkerMessage message) {
        if (player != null && ServerPlayNetworking.canSend(player, FromTheFogEquipmentMarkerMessage.TYPE)) {
            ServerPlayNetworking.send(player, message);
        }
    }

    @Environment(EnvType.CLIENT)
    public static void sendCamoRemovalToServer(PacketCamoRemoval msg) {
        ClientPlayNetworking.send(msg);
    }

    @Environment(EnvType.CLIENT)
    public static void sendSoundToServer(SoundMessage message) {
        ClientPlayNetworking.send(message);
    }
}
