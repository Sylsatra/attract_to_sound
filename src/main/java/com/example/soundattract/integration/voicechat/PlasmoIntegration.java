package com.example.soundattract.integration.voicechat;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.server.level.ServerPlayer;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;

import java.util.Optional;

@Addon(id = "soundattract", name = "Attract to Sound", version = "PlasmoVoice", authors = {"Paldiu", "Sylsatra"})
public class PlasmoIntegration implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceServer server;

    @Override
    public void onAddonInitialize() {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.VOICE_CHAT)) {
            SoundAttractMod.LOGGER.info("[PlasmoIntegration] onAddonInitialize: registering voice listener");
        }

        try {
            server.getEventBus().register(this, new VoiceListener());
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[PlasmoIntegration] register(this, listener) failed: {}", t.toString());
        }
    }

    @Override
    public void onAddonShutdown() {
        AddonInitializer.super.onAddonShutdown();
    }

    public static final class VoiceListener {
        @EventSubscribe
        public void voiceActive(PlayerSpeakEvent event) {
            if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
                return;
            }

            Object mcPlayerObj = event.getPlayer().getInstance();
            if (!(mcPlayerObj instanceof ServerPlayer mcPlayer)) {
                return;
            }

            int range = SoundAttractConfig.SERVER.voiceChatNormalRange.get();
            double weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
            net.minecraft.core.BlockPos pos = mcPlayer.blockPosition();
            String dimString = mcPlayer.serverLevel().dimension().location().toString();

            mcPlayer.getServer().execute(() -> SoundTracker.addSound(null, pos, dimString,
                    range, weight, lifetime, SoundMessage.VOICE_CHAT_SOUND_ID.toString()));
        }

        @EventSubscribe
        public void onServerSourceAudio(ServerSourceAudioPacketEvent event) {
            if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
                return;
            }

            try {
                short distance = event.getDistance();

                var activationInfo = event.getActivationInfo();
                if (activationInfo == null) {
                    return;
                }
                var voicePlayer = activationInfo.getPlayer();
                if (voicePlayer == null) {
                    return;
                }
                Object playerObj = voicePlayer.getInstance();
                if (playerObj == null) {
                    return;
                }

                ServerPlayer mcPlayer = null;
                if (playerObj instanceof ServerPlayer sp) {
                    mcPlayer = sp;
                } else {
                    String[] candidateMethods = new String[]{"getPlayer", "getMinecraftPlayer", "getHandle", "getInstance"};
                    for (String methodName : candidateMethods) {
                        try {
                            var m = playerObj.getClass().getMethod(methodName);
                            Object inner = m.invoke(playerObj);
                            if (inner instanceof ServerPlayer sp2) {
                                mcPlayer = sp2;
                                break;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }

                if (mcPlayer == null) {
                    return;
                }

                int range = (distance > 0) ? distance : SoundAttractConfig.SERVER.voiceChatNormalRange.get();
                double weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
                int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
                net.minecraft.core.BlockPos pos = mcPlayer.blockPosition();
                String dimString = mcPlayer.serverLevel().dimension().location().toString();

                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.VOICE_CHAT)) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] VOICE_CHAT forwarded via Plasmo: player={} range={} dim={}",
                            mcPlayer.getGameProfile().getName(), range, mcPlayer.serverLevel().dimension().location());
                }

                var server = mcPlayer.getServer();
                if (server == null) {
                    return;
                }
                server.execute(() -> SoundTracker.addSound(null, pos, dimString,
                        range, weight, lifetime, SoundMessage.VOICE_CHAT_SOUND_ID.toString()));
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[PlasmoIntegration] Exception in onServerSourceAudio", t);
            }
        }
    }
}