package com.example.soundattract.camo;

import net.minecraft.core.registries.BuiltInRegistries;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.separate.StealthConfig;
import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.util.CamoUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.LivingEntity;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class CamoApplyHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!StealthConfig.ENABLE_LAYERED_CAMOUFLAGE.get()) return;

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;

        CamoMaterialRegistry.CamoMaterialEntry entry = CamoMaterialRegistry.getMaterial(id);
        if (entry != null) {
            if (!player.level().isClientSide()) {
                CamouflageCapability camo = player.getData(CamoAttachments.CAMOUFLAGE);
                camo.applyMaterial(player, entry, player.isCreative());
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }
            event.setCanceled(true);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!StealthConfig.ENABLE_LAYERED_CAMOUFLAGE.get()) return;

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;

        if (!(event.getTarget() instanceof LivingEntity target)) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;

        CamoMaterialRegistry.CamoMaterialEntry entry = CamoMaterialRegistry.getMaterial(id);
        if (entry != null) {
            if (!player.level().isClientSide()) {
                EquipmentSlot slot = null;
                var equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable != null) {
                    slot = equippable.slot();
                }

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
                    CamouflageCapability camo = target.getData(CamoAttachments.CAMOUFLAGE);
                    camo.applyMaterial(target, entry, player.isCreative());
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                }
            }
            event.setCanceled(true);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }
}
