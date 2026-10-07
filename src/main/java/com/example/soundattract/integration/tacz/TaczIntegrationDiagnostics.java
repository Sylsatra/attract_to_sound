package com.example.soundattract.integration.tacz;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.integration.tacznpcs.TaczNpcDebug;
import com.example.soundattract.integration.taczhostiles.TaczHostilesDebug;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczIntegrationDiagnostics {
    private TaczIntegrationDiagnostics() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TaczNpcDebug.reportStartupStatus();
        TaczHostilesDebug.reportStartupStatus();
    }
}
