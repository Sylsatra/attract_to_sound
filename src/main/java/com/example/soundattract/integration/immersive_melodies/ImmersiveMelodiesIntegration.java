package com.example.soundattract.integration.immersive_melodies;

import com.example.soundattract.SoundAttractMod;
import net.neoforged.fml.ModList;

public class ImmersiveMelodiesIntegration {
    public static void init() {
        if (ModList.get().isLoaded("immersive_melodies")) {
            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Prodding lazy registration...");
            try {
                Class.forName("immersive_melodies.item.InstrumentItem");
                SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Lazy registration prodded successfully.");
            } catch (ClassNotFoundException e) {
                SoundAttractMod.LOGGER.warn("[ImmersiveMelodies] Failed to find InstrumentItem class for prodding: {}", e.getMessage());
            }

            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Initializing event integration...");
            ImmersiveMelodiesEvents.register();
        } else {
            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Mod not found, skipping integration.");
        }
    }
}
