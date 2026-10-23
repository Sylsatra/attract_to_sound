package com.example.soundattract.camo;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Map;

/**
 * Tracks which armor slots have been successfully rendered by the vanilla mixin.
 * Used to detect incompatible modded armor (which bypasses the mixin) and strip its camo NBT.
 */
public class ArmorValidationTracker {
    
    private static final Map<LivingEntity, Set<EquipmentSlot>> VALIDATED_SLOTS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void markSlotValidated(LivingEntity entity, EquipmentSlot slot) {
        if (entity == null || slot == null) return;
        VALIDATED_SLOTS.computeIfAbsent(entity, k -> Collections.synchronizedSet(new HashSet<>())).add(slot);
    }

    public static boolean wasSlotValidated(LivingEntity entity, EquipmentSlot slot) {
        if (entity == null || slot == null) return false;
        Set<EquipmentSlot> slots = VALIDATED_SLOTS.get(entity);
        return slots != null && slots.contains(slot);
    }
    
    public static void clearForEntity(LivingEntity entity) {
        VALIDATED_SLOTS.remove(entity);
    }
}
