package com.example.soundattract.network;

import com.example.soundattract.camo.CamoLayer;
import com.example.soundattract.camo.CamouflageCapability;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import io.netty.handler.codec.DecoderException;

public class CamoSyncMessage {
    private static final int MAX_LAYERS = 64;
    private final int entityId;
    private final List<CamoLayer> layers;

    public CamoSyncMessage(int entityId, List<CamoLayer> layers) {
        this.entityId = entityId;
        this.layers = layers;
    }

    public static void encode(CamoSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        List<CamoLayer> safeLayers = new ArrayList<>(MAX_LAYERS);
        if (msg.layers != null) {
            for (CamoLayer layer : msg.layers) {
                if (layer != null && safeLayers.size() < MAX_LAYERS) {
                    safeLayers.add(layer);
                }
            }
        }
        buf.writeInt(safeLayers.size());
        for (CamoLayer layer : safeLayers) {
            CompoundTag tag = layer.save();
            buf.writeNbt(tag);
        }
    }

    public static CamoSyncMessage decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int size = buf.readInt();
        if (size < 0 || size > MAX_LAYERS) {
            throw new DecoderException("Invalid camouflage layer count: " + size);
        }
        List<CamoLayer> layers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                layers.add(CamoLayer.load(tag));
            }
        }
        return new CamoSyncMessage(entityId, layers);
    }

    public static void handle(CamoSyncMessage msg, Supplier<NetworkEvent.Context> ctxGetter) {
        NetworkEvent.Context ctx = ctxGetter.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> ClientPayloadHandler.handleSync(msg));
        });
        ctx.setPacketHandled(true);
    }

    private static class ClientPayloadHandler {
        private static void handleSync(CamoSyncMessage msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.level() != null) {
                Entity entity = player.level().getEntity(msg.entityId);
                if (entity instanceof net.minecraft.world.entity.LivingEntity targetEntity) {
                    targetEntity.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
                        camo.setLayers(msg.layers);
                    });
                }
            }
        }
    }
}