package com.example.soundattract.integration.voicechat;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
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
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.VOICE_CHAT)) {
                SoundAttractMod.LOGGER.info("[PlasmoVoiceBootstrap] Loading Plasmo Voice addon...");
            }

            PlasmoVoiceServer.getAddonsLoader().load(new PlasmoIntegration());

            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.VOICE_CHAT)) {
                SoundAttractMod.LOGGER.info("[PlasmoVoiceBootstrap] Plasmo Voice addon loaded.");
            }
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.error("[PlasmoVoiceBootstrap] Failed to load Plasmo Voice addon", t);
        }
    }
}