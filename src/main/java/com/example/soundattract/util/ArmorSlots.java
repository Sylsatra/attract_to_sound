package com.example.soundattract.util;

import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ArmorSlots {
    private ArmorSlots() {}

    public static List<ItemStack> get(LivingEntity entity) {
        return List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
                .stream().map(entity::getItemBySlot).toList();
    }
}
