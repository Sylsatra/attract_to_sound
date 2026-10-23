package com.example.soundattract.integration.immersive_melodies;

import com.example.soundattract.Soundattract;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public class ImmersiveMelodiesIntegration {
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("immersive_melodies")) {
            Soundattract.LOGGER.info("[ImmersiveMelodies] Prodding lazy registration...");
            try {
                Class.forName("immersive_melodies.Items");
                Soundattract.LOGGER.info("[ImmersiveMelodies] Lazy registration prodded successfully.");
            } catch (ClassNotFoundException e) {
                Soundattract.LOGGER.warn("[ImmersiveMelodies] Failed to find Items interface for prodding: {}", e.getMessage());
            }

            Soundattract.LOGGER.info("[ImmersiveMelodies] Registering server tick event...");
            ServerTickEvents.END_SERVER_TICK.register(ImmersiveMelodiesEvents::onServerTick);
        } else {
            Soundattract.LOGGER.info("[ImmersiveMelodies] Mod not found, skipping integration.");
        }
    }
}
