package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.SoundAttractMod;
import net.neoforged.neoforge.common.NeoForge;

public class CsGrenadesIntegration {

    public static void register() {
        if (!CsGrenadesCompat.isLoaded()) return;
        SoundAttractMod.LOGGER.info("CS Grenades mod detected; registering integration event handlers.");
        NeoForge.EVENT_BUS.register(CsGrenadesEventHandler.class);
    }
}
