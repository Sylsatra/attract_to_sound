package com.example.soundattract.integration.voicechat;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import su.plo.voice.api.server.PlasmoVoiceServer;

public final class PlasmoVoiceBootstrap {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!ModList.get().isLoaded("plasmovoice")) {
            return;
        }
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
            return;
        }
        try {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[PlasmoVoiceBootstrap] Loading Plasmo Voice addon...");
            }
            PlasmoVoiceServer.getAddonsLoader().load(new PlasmoIntegration());
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[PlasmoVoiceBootstrap] Plasmo Voice addon loaded.");
            }
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.error("[PlasmoVoiceBootstrap] Failed to load Plasmo Voice addon", t);
        }
    }
}
