package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.separate.StealthConfig;
import com.example.soundattract.util.CamoUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.LivingEntity;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class CamoApplyHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!StealthConfig.ENABLE_LAYERED_CAMOUFLAGE.get()) return;

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;

        CamoMaterialRegistry.CamoMaterialEntry entry = CamoMaterialRegistry.getMaterial(id);
        if (entry != null) {
            if (!player.level().isClientSide) {
                player.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
                    camo.applyMaterial(player, entry, player.isCreative());
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                });
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

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;

        CamoMaterialRegistry.CamoMaterialEntry entry = CamoMaterialRegistry.getMaterial(id);
        if (entry != null) {
            if (!player.level().isClientSide) {
                EquipmentSlot slot = null;
                if (stack.getItem() instanceof ArmorItem armorItem) {
                    slot = armorItem.getEquipmentSlot();
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
                    target.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
                        camo.applyMaterial(target, entry, player.isCreative());
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    });
                }
            }
            event.setCanceled(true);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }
}