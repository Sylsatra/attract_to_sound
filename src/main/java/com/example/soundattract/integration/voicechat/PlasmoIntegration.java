package com.example.soundattract.integration.voicechat;

import com.example.soundattract.Soundattract;
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
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[PlasmoIntegration] onAddonInitialize: registering voice listener");
        }

        try {
            server.getEventBus().register(this, new VoiceListener());
        } catch (Throwable t) {
            Soundattract.LOGGER.warn("[PlasmoIntegration] register(this, listener) failed: {}", t.toString());
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

            var voicePlayer = event.getPlayer();
            ServerPlayer mcPlayer = resolveMinecraftPlayer(voicePlayer == null ? null : voicePlayer.getInstance());
            if (mcPlayer == null) {
                return;
            }

            int range = SoundAttractConfig.SERVER.voiceChatNormalRange.get();
            double weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
            net.minecraft.core.BlockPos pos = mcPlayer.blockPosition();
            String dimString = ((net.minecraft.server.level.ServerLevel) mcPlayer.level()).dimension().identifier().toString();

            var minecraftServer = mcPlayer.level().getServer();
            if (minecraftServer != null) {
                minecraftServer.execute(() -> SoundTracker.addSound(null, pos, dimString,
                        range, weight, lifetime, SoundMessage.VOICE_CHAT_SOUND_ID.toString()));
            }
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
                ServerPlayer mcPlayer = resolveMinecraftPlayer(voicePlayer.getInstance());
                if (mcPlayer == null) {
                    return;
                }

                int range = (distance > 0) ? distance : SoundAttractConfig.SERVER.voiceChatNormalRange.get();
                double weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
                int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
                net.minecraft.core.BlockPos pos = mcPlayer.blockPosition();
                String dimString = ((net.minecraft.server.level.ServerLevel) mcPlayer.level()).dimension().identifier().toString();

                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[PlasmoIntegration] VOICE_CHAT forwarded via Plasmo: player={} range={} dim={}",
                            mcPlayer.getGameProfile().name(), range, ((net.minecraft.server.level.ServerLevel) mcPlayer.level()).dimension().identifier());
                }

                var server = mcPlayer.level().getServer();
                if (server == null) {
                    return;
                }
                server.execute(() -> SoundTracker.addSound(null, pos, dimString,
                        range, weight, lifetime, SoundMessage.VOICE_CHAT_SOUND_ID.toString()));
            } catch (Throwable t) {
                Soundattract.LOGGER.error("[PlasmoIntegration] Exception in onServerSourceAudio", t);
            }
        }

        private static ServerPlayer resolveMinecraftPlayer(Object playerObj) {
            if (playerObj instanceof ServerPlayer player) {
                return player;
            }
            if (playerObj == null) {
                return null;
            }

            String[] candidateMethods = {"getPlayer", "getMinecraftPlayer", "getHandle", "getInstance"};
            for (String methodName : candidateMethods) {
                try {
                    Object inner = playerObj.getClass().getMethod(methodName).invoke(playerObj);
                    if (inner instanceof ServerPlayer player) {
                        return player;
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) {

                }
            }
            return null;
        }
    }
}
