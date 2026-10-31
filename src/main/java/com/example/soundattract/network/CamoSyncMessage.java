package com.example.soundattract.network;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.camo.CamoLayer;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.camo.CamoAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class CamoSyncMessage implements CustomPacketPayload {
    public static final Type<CamoSyncMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "camo_sync"));
    public static final StreamCodec<FriendlyByteBuf, CamoSyncMessage> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CamoSyncMessage decode(FriendlyByteBuf buf) {
            return CamoSyncMessage.decode(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CamoSyncMessage msg) {
            CamoSyncMessage.encode(msg, buf);
        }
    };

    private final int entityId;
    private final List<CamoLayer> layers;

    public CamoSyncMessage(int entityId, List<CamoLayer> layers) {
        this.entityId = entityId;
        this.layers = layers;
    }

    public static void encode(CamoSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.layers.size());
        for (CamoLayer layer : msg.layers) {
            buf.writeNbt(layer.save());
        }
    }

    public static CamoSyncMessage decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int size = buf.readInt();
        List<CamoLayer> layers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                layers.add(CamoLayer.load(tag));
            }
        }
        return new CamoSyncMessage(entityId, layers);
    }

    public static void handle(CamoSyncMessage msg, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadHandler.handleSync(msg));
    }

    private static class ClientPayloadHandler {
        private static void handleSync(CamoSyncMessage msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.level() != null) {
                Entity entity = player.level().getEntity(msg.entityId);
                if (entity instanceof LivingEntity targetEntity) {
                    CamouflageCapability camo = targetEntity.getData(CamoAttachments.CAMOUFLAGE);
                    camo.setLayers(msg.layers);
                }
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}