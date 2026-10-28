package com.example.soundattract.integration.tacz;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.integration.tacznpcs.TaczNpcDebug;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TaczIntegrationDiagnostics {
    private TaczIntegrationDiagnostics() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TaczNpcDebug.reportStartupStatus();
    }
}
