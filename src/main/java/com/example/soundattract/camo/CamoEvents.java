package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.event.StealthDetectionEvents;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class CamoEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        player.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {

            StealthDetectionEvents.StealthPerfTier tier = StealthDetectionEvents.getPerfTier(player.level());
            camo.tickDegradation(player, player.level(), tier);
        });
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!event.getEntity().level().isClientSide) {
            event.getEntity().getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
                camo.onDamage(event.getEntity(), event.getAmount());
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            event.getEntity().getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
                camo.sync(event.getEntity());
            });
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!event.getEntity().level().isClientSide) {
            event.getTarget().getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
                camo.sync(event.getTarget() instanceof LivingEntity le ? le : null);
            });
        }
    }
}