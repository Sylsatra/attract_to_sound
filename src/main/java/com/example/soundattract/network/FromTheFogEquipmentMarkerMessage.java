package com.example.soundattract.network;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.client.FromTheFogVisualCompat;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Identifies tracked From The Fog entities whose equipment forms part of their visible model. */
public record FromTheFogEquipmentMarkerMessage(UUID entityId, boolean keepEquipmentVisible)
        implements CustomPacketPayload {
    public static final Type<FromTheFogEquipmentMarkerMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "from_the_fog_equipment_marker"));
    public static final StreamCodec<ByteBuf, FromTheFogEquipmentMarkerMessage> STREAM_CODEC = StreamCodec.of(
            FromTheFogEquipmentMarkerMessage::encode, FromTheFogEquipmentMarkerMessage::decode);

    private static void encode(ByteBuf buffer, FromTheFogEquipmentMarkerMessage message) {
        buffer.writeLong(message.entityId().getMostSignificantBits());
        buffer.writeLong(message.entityId().getLeastSignificantBits());
        buffer.writeBoolean(message.keepEquipmentVisible());
    }

    private static FromTheFogEquipmentMarkerMessage decode(ByteBuf buffer) {
        return new FromTheFogEquipmentMarkerMessage(new UUID(buffer.readLong(), buffer.readLong()), buffer.readBoolean());
    }

    public static void handle(FromTheFogEquipmentMarkerMessage message, IPayloadContext context) {
        context.enqueueWork(() -> FromTheFogVisualCompat.applyMarker(message.entityId(), message.keepEquipmentVisible()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
