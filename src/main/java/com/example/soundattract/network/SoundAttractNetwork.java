package com.example.soundattract.network;

import com.example.soundattract.Soundattract;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Network registration for 1.20.1 Fabric.
 * Uses direct PacketByteBuf reading instead of StreamCodec.
 */
public class SoundAttractNetwork {

    public static final ResourceLocation SOUND_MESSAGE_ID = new ResourceLocation(Soundattract.MOD_ID, "sound_message");
    public static final ResourceLocation CAMO_SYNC_ID = new ResourceLocation(Soundattract.MOD_ID, "camo_sync");
    public static final ResourceLocation CAMO_REMOVAL_ID = new ResourceLocation(Soundattract.MOD_ID, "camo_removal");
    public static final ResourceLocation AWARENESS_SNAPSHOT_ID = new ResourceLocation(Soundattract.MOD_ID, "awareness_snapshot");
    public static final ResourceLocation FROM_THE_FOG_MARKER_ID = new ResourceLocation(Soundattract.MOD_ID, "from_the_fog_marker");

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SOUND_MESSAGE_ID, (server, player, handler, buf, responseSender) -> {
            SoundMessage msg = SoundMessage.read(buf);
            server.execute(() -> SoundMessage.handle(msg, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(CAMO_REMOVAL_ID, (server, player, handler, buf, responseSender) -> {
            PacketCamoRemoval msg = PacketCamoRemoval.read(buf);
            server.execute(() -> PacketCamoRemoval.handle(msg, player));
        });


        Soundattract.LOGGER.info("Network packets registered for 1.20.1");
    }

    public static void sendToTrackingAndSelf(LivingEntity entity, CamoSyncMessage msg) {
        if (entity.level().isClientSide) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        CamoSyncMessage.write(buf, msg);
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, CAMO_SYNC_ID, buf);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, CAMO_SYNC_ID, buf);
        }
    }

    public static void sendAwarenessSnapshot(ServerPlayer player, AwarenessSnapshotMessage message) {
        if (player == null || !ServerPlayNetworking.canSend(player, AWARENESS_SNAPSHOT_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        AwarenessSnapshotMessage.encode(message, buf);
        ServerPlayNetworking.send(player, AWARENESS_SNAPSHOT_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendCamoRemovalToServer(PacketCamoRemoval msg) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        PacketCamoRemoval.write(buf, msg);
        ClientPlayNetworking.send(CAMO_REMOVAL_ID, buf);
    }
}
