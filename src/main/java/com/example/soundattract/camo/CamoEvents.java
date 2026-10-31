package com.example.soundattract.camo;

import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.event.StealthDetectionEvents;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class CamoEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;

        Player player = event.getEntity();
        CamouflageCapability camo = player.getData(CamoAttachments.CAMOUFLAGE);

        StealthDetectionEvents.StealthPerfTier tier = StealthDetectionEvents.getPerfTier(player.level());
        camo.tickDegradation(player, player.level(), tier);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!event.getEntity().level().isClientSide()) {
            CamouflageCapability camo = event.getEntity().getData(CamoAttachments.CAMOUFLAGE);
            camo.onDamage(event.getEntity(), event.getNewDamage());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            CamouflageCapability camo = event.getEntity().getData(CamoAttachments.CAMOUFLAGE);
            camo.sync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!event.getEntity().level().isClientSide()) {
            if (event.getTarget() instanceof LivingEntity target) {
                CamouflageCapability camo = target.getData(CamoAttachments.CAMOUFLAGE);
                camo.sync(target);
            }
        }
    }
}
