package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.common.MinecraftForge;

public class CsGrenadesIntegration {

    public static void register() {
        if (!CsGrenadesCompat.isLoaded()) {
            SoundAttractMod.LOGGER.info("CS Grenades mod not detected; skipping integration registration.");
            return;
        }
        SoundAttractMod.LOGGER.info("CS Grenades mod detected; registering integration event handlers.");
        CsGrenadesEventHandler.registerGrenadeEvent(MinecraftForge.EVENT_BUS);
    }
}