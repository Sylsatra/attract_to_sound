package com.example.soundattract.integration.spore;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.separate.IntegrationConfig;
import com.example.soundattract.util.CamoUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SporeScentSuppressor {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!SporeIntegration.isSporeLoaded()) return;
        if (!IntegrationConfig.ENABLE_SPORE_INTEGRATION.get()) return;
        if (!IntegrationConfig.ENABLE_SCENT_BLOCK_SUPPRESSION.get()) return;

        Player player = event.player;

        if (player.tickCount % 10 != 0) return;

        float camoStrength = CamoUtil.getCombinedCamoStrength(player);
        double threshold = 0.7;

        if (camoStrength >= threshold) {
            int accel = IntegrationConfig.SCENT_ENTITY_DISSIPATION_ACCEL.get();
            SporeGoalInjectorProxy.accelerateScentDissipation(player, 16.0, accel);
        }
    }
}