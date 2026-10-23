package com.example.soundattract.network;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.item.ItemStack;

public record PacketCamoRemoval(
        EquipmentSlot slot
) implements CustomPacketPayload {
    public static final Type<PacketCamoRemoval> TYPE = new Type<>(Identifier.fromNamespaceAndPath("soundattract", "camo_removal"));

    public static final StreamCodec<FriendlyByteBuf, PacketCamoRemoval> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PacketCamoRemoval decode(FriendlyByteBuf buf) {
            return PacketCamoRemoval.read(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf, PacketCamoRemoval msg) {
            PacketCamoRemoval.write(buf, msg);
        }
    };
    public static void write(FriendlyByteBuf buf, PacketCamoRemoval msg) {
        buf.writeEnum(msg.slot);
    }

    public static PacketCamoRemoval read(FriendlyByteBuf buf) {
        return new PacketCamoRemoval(buf.readEnum(EquipmentSlot.class));
    }

    public static void handle(PacketCamoRemoval msg, net.minecraft.server.level.ServerPlayer player) {
        if (player != null) {
            ItemStack stack = player.getItemBySlot(msg.slot);
            if (!stack.isEmpty() && stack.has(DataComponents.EQUIPPABLE)) {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag != null && !tag.isEmpty() && (tag.contains("soundattract:CamoLayers") || tag.contains("soundattract:CamoStrength"))) {
                    tag.remove("soundattract:CamoLayers");
                    tag.remove("soundattract:CamoStrength");
                    tag.remove("soundattract:CamoColor");
                    tag.remove("soundattract:CamoSeed");
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    player.inventoryMenu.broadcastChanges();
                }
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
