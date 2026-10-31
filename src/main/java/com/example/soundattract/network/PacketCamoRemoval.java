package com.example.soundattract.network;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PacketCamoRemoval implements CustomPacketPayload {
    public static final Type<PacketCamoRemoval> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "camo_removal"));
    public static final StreamCodec<FriendlyByteBuf, PacketCamoRemoval> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PacketCamoRemoval decode(FriendlyByteBuf buf) {
            return new PacketCamoRemoval(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf, PacketCamoRemoval msg) {
            msg.toBytes(buf);
        }
    };

    private final EquipmentSlot slot;

    public PacketCamoRemoval(EquipmentSlot slot) {
        this.slot = slot;
    }

    public PacketCamoRemoval(FriendlyByteBuf buf) {
        this.slot = buf.readEnum(EquipmentSlot.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(slot);
    }

    public static void handle(PacketCamoRemoval msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer sp ? sp : null;
            if (player != null) {
                ItemStack stack = player.getItemBySlot(msg.slot);
                if (!stack.isEmpty() && (stack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE))) {
                    if (com.example.soundattract.util.CamoUtil.hasCustomTag(stack)) {
                        net.minecraft.nbt.CompoundTag tag = com.example.soundattract.util.CamoUtil.getCustomTag(stack);
                        if (tag.contains("soundattract:CamoLayers") || tag.contains("soundattract:CamoStrength")) {
                            tag.remove("soundattract:CamoLayers");
                            tag.remove("soundattract:CamoStrength");
                            tag.remove("soundattract:CamoColor");
                            tag.remove("soundattract:CamoSeed");
                            player.inventoryMenu.broadcastChanges();
                        }
                    }
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}