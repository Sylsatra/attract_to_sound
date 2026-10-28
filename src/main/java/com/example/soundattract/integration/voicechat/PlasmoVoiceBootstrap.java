package com.example.soundattract.integration.voicechat;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import su.plo.voice.api.server.PlasmoVoiceServer;

public final class PlasmoVoiceBootstrap {

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!FabricLoader.getInstance().isModLoaded("plasmovoice")) {
                return;
            }
            if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
                return;
            }
            try {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[PlasmoVoiceBootstrap] Loading Plasmo Voice addon...");
                }

                PlasmoVoiceServer.getAddonsLoader().load(new PlasmoIntegration());

                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[PlasmoVoiceBootstrap] Plasmo Voice addon loaded.");
                }
            } catch (Throwable t) {
                Soundattract.LOGGER.error("[PlasmoVoiceBootstrap] Failed to load Plasmo Voice addon", t);
            }
        });
    }
}
