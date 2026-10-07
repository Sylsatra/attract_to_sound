package com.example.soundattract.network;

import com.example.soundattract.util.CamoUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketCamoRemoval {
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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty() && (stack.getItem() instanceof net.minecraft.world.item.ArmorItem)) {
                    if (stack.hasTag()) {
                        net.minecraft.nbt.CompoundTag tag = stack.getTag();
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
        return true;
    }
}