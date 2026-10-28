package com.example.soundattract.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FromTheFogEquipmentMarkerMessage(UUID entityId, boolean keepEquipmentVisible) implements CustomPacketPayload {
    public static final Type<FromTheFogEquipmentMarkerMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("soundattract", "from_the_fog_marker"));
    public static final StreamCodec<FriendlyByteBuf, FromTheFogEquipmentMarkerMessage> STREAM_CODEC = new StreamCodec<>() {
        @Override public FromTheFogEquipmentMarkerMessage decode(FriendlyByteBuf buffer) { return FromTheFogEquipmentMarkerMessage.decode(buffer); }
        @Override public void encode(FriendlyByteBuf buffer, FromTheFogEquipmentMarkerMessage message) { FromTheFogEquipmentMarkerMessage.encode(message, buffer); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void encode(FromTheFogEquipmentMarkerMessage message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.entityId());
        buffer.writeBoolean(message.keepEquipmentVisible());
    }

    public static FromTheFogEquipmentMarkerMessage decode(FriendlyByteBuf buffer) {
        return new FromTheFogEquipmentMarkerMessage(buffer.readUUID(), buffer.readBoolean());
    }
}
