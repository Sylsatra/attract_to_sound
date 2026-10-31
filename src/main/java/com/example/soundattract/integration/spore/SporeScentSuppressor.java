package com.example.soundattract.integration.spore;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.separate.IntegrationConfig;
import com.example.soundattract.util.CamoUtil;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles the logic for stealthy players suppressing Spore scent entities.
 */
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class SporeScentSuppressor {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!SporeIntegration.isSporeLoaded()) return;
        if (!IntegrationConfig.ENABLE_SPORE_INTEGRATION.get()) return;
        if (!IntegrationConfig.ENABLE_SCENT_BLOCK_SUPPRESSION.get()) return;

        Player player = event.getEntity();
        
        if (player.tickCount % 10 != 0) return;

        float camoStrength = CamoUtil.getCombinedCamoStrength(player);
        double threshold = 0.7;

        if (camoStrength >= threshold) {
            int accel = IntegrationConfig.SCENT_ENTITY_DISSIPATION_ACCEL.get();
            SporeGoalInjectorProxy.accelerateScentDissipation(player, 16.0, accel);
        }
    }
}
