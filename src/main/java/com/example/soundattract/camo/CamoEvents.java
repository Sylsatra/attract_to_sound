package com.example.soundattract.camo;

import com.example.soundattract.event.StealthDetectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;


import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CamoEvents {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                CamouflageCapability.getCapability(player).ifPresent(camo -> {
                    StealthDetectionEvents.StealthPerfTier tier = StealthDetectionEvents.getPerfTier(player.level());
                    camo.tickDegradation(player, player.level(), tier);
                });
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                CamouflageCapability.getCapability(living).ifPresent(camo -> {
                    camo.onDamage(living, amount);
                });
            }
            return true;
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayer player) {
                CamouflageCapability.getCapability(player).ifPresent(camo -> {
                    camo.sync(player);
                });
            } else if (entity instanceof LivingEntity living) {
                CamouflageCapability.getCapability(living).ifPresent(camo -> {
                    camo.sync(living);
                });
            }
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (CamoApplyHandler.onRightClickItem(player, stack, hand)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (entity instanceof LivingEntity living && CamoApplyHandler.onEntityInteract(player, living, stack)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
