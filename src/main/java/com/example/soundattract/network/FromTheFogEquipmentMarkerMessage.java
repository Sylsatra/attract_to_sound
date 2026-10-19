package com.example.soundattract.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

public record FromTheFogEquipmentMarkerMessage(UUID entityId, boolean keepEquipmentVisible) {
    public static void encode(FromTheFogEquipmentMarkerMessage message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.entityId());
        buffer.writeBoolean(message.keepEquipmentVisible());
    }

    public static FromTheFogEquipmentMarkerMessage decode(FriendlyByteBuf buffer) {
        return new FromTheFogEquipmentMarkerMessage(buffer.readUUID(), buffer.readBoolean());
    }
}
