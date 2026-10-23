package com.example.soundattract.network;

import com.example.soundattract.camo.CamoLayer;
import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record CamoSyncMessage(int entityId, List<CamoLayer> layers) implements CustomPacketPayload {
    public static final Type<CamoSyncMessage> ID = new Type<>(Identifier.fromNamespaceAndPath("soundattract", "camo_sync"));

    public static final StreamCodec<FriendlyByteBuf, CamoSyncMessage> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CamoSyncMessage decode(FriendlyByteBuf buf) {
            return CamoSyncMessage.read(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CamoSyncMessage msg) {
            CamoSyncMessage.write(buf, msg);
        }
    };

    public static void write(FriendlyByteBuf buf, CamoSyncMessage msg) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.layers.size());
        for (CamoLayer layer : msg.layers) {
            buf.writeNbt(layer.save());
        }
    }

    public static CamoSyncMessage read(FriendlyByteBuf buf) {
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
