package com.example.soundattract.integration.spore;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class SporeGoalInjector {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!SporeIntegration.isSporeLoaded()) return;
        if (!IntegrationConfig.ENABLE_SPORE_INTEGRATION.get()) return;
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();

        try {
            SporeGoalInjectorProxy.tryInject(entity);
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[SporeGoalInjector] Failed to inject goals for entity {}: {}", entity, t.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!SporeIntegration.isSporeLoaded()) return;
        if (!IntegrationConfig.ENABLE_STEALTH_MARKER_BRIDGE.get()) return;

        try {
            SporeGoalInjectorProxy.handleVisibilityEvent(event);
        } catch (Throwable ignored) {}
    }
}
