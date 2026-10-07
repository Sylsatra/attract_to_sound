package com.example.soundattract.network;

import com.example.soundattract.client.FromTheFogVisualCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Tells a tracking client that this invisible entity uses From The Fog's equipment-based model. */
public record FromTheFogEquipmentMarkerMessage(UUID entityId, boolean keepEquipmentVisible) {
    public static void encode(FromTheFogEquipmentMarkerMessage message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.entityId());
        buffer.writeBoolean(message.keepEquipmentVisible());
    }

    public static FromTheFogEquipmentMarkerMessage decode(FriendlyByteBuf buffer) {
        return new FromTheFogEquipmentMarkerMessage(buffer.readUUID(), buffer.readBoolean());
    }

    public static void handle(FromTheFogEquipmentMarkerMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> FromTheFogVisualCompat.applyMarker(message.entityId(), message.keepEquipmentVisible())
        ));
        context.setPacketHandled(true);
    }
}
