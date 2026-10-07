package com.example.soundattract.integration.immersive_melodies;

import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

public class ImmersiveMelodiesIntegration {
    public static void init() {
        if (ModList.get().isLoaded("immersive_melodies")) {
            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Prodding lazy registration...");
            try {
                Class.forName("immersive_melodies.Items");
                SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Lazy registration prodded successfully.");
            } catch (ClassNotFoundException e) {
                SoundAttractMod.LOGGER.warn("[ImmersiveMelodies] Failed to find Items interface for prodding: {}", e.getMessage());
            }

            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Initializing event integration...");
            MinecraftForge.EVENT_BUS.register(ImmersiveMelodiesEvents.class);
        } else {
            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Mod not found, skipping integration.");
        }
    }
}