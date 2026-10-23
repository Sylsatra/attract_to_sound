package com.example.soundattract.camo;

import net.minecraft.core.component.DataComponents;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.separate.StealthConfig;
import com.example.soundattract.util.CamoUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

public class CamoApplyHandler {

    public static boolean onRightClickItem(Player player, ItemStack stack, InteractionHand hand) {
        if (!StealthConfig.ENABLE_LAYERED_CAMOUFLAGE.get()) return false;

        if (!player.isShiftKeyDown()) return false;

        if (stack.isEmpty()) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        CamoMaterialRegistry.CamoMaterialEntry entry = CamoMaterialRegistry.getMaterial(id);
        if (entry != null) {
            if (!player.level().isClientSide()) {
                CamouflageCapability.getCapability(player).ifPresent(camo -> {
                    camo.applyMaterial(player, entry, player.isCreative());
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                });
            }
            player.swing(hand, true);
            return true;
        }
        return false;
    }

    public static boolean onEntityInteract(Player player, LivingEntity target, ItemStack stack) {
        if (!StealthConfig.ENABLE_LAYERED_CAMOUFLAGE.get()) return false;

        if (!player.isShiftKeyDown()) return false;

        if (target == null) return false;

        if (stack.isEmpty()) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        CamoMaterialRegistry.CamoMaterialEntry entry = CamoMaterialRegistry.getMaterial(id);
        if (entry != null) {
            if (!player.level().isClientSide()) {
                EquipmentSlot slot = null;
                var equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable != null) slot = equippable.slot();

                if (slot != null) {
                    ItemStack armorStack = target.getItemBySlot(slot);
                    if (!armorStack.isEmpty()) {
                        CamoUtil.ClimateData climate = CamoUtil.sampleClimate(target);
                        CamoUtil.addLayerToStack(armorStack, entry.getLayer(target.level().getGameTime(), target.getRandom().nextLong(), climate), slot);

                        target.setItemSlot(slot, armorStack);
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    }
                } else {
                    CamouflageCapability.getCapability(target).ifPresent(camo -> {
                        camo.applyMaterial(target, entry, player.isCreative());
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    });
                }
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            return true;
        }
        return false;
    }
}
