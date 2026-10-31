package com.example.soundattract.integration.voicechat;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

@Addon(id = "soundattract", name = "Attract to Sound", version = "PlasmoVoice", authors = {"Paldiu", "Sylsatra"})
public final class PlasmoIntegration implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceServer server;

    @Override
    public void onAddonInitialize() {
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PlasmoIntegration] onAddonInitialize: registering voice listener");
        }
        try {
            if (server == null) {
                SoundAttractMod.LOGGER.warn("[PlasmoIntegration] PlasmoVoiceServer is null; skipping listener registration.");
                return;
            }
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
        public void onPlayerSpeak(PlayerSpeakEvent event) {
            try {
                if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
                    return;
                }
                if (event == null || event.getPlayer() == null) {
                    return;
                }

                ServerPlayer player = resolveMcPlayer(event.getPlayer().getInstance());
                if (player == null) {
                    UUID uuid = tryResolveUuid(event.getPlayer());
                    if (uuid == null) {
                        uuid = tryResolveUuid(event.getPlayer().getInstance());
                    }
                    player = resolveViaServer(uuid);
                }
                if (player == null) {
                    return;
                }

                addVoiceSound(player, SoundAttractConfig.SERVER.voiceChatNormalRange.get());
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[PlasmoIntegration] Exception in onPlayerSpeak", t);
            }
        }

        @EventSubscribe
        public void onServerSourceAudio(ServerSourceAudioPacketEvent event) {
            try {
                if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
                    return;
                }
                if (event == null || event.getActivationInfo() == null || event.getActivationInfo().getPlayer() == null) {
                    return;
                }

                var voicePlayer = event.getActivationInfo().getPlayer();
                ServerPlayer player = resolveMcPlayer(voicePlayer.getInstance());
                if (player == null) {
                    UUID uuid = tryResolveUuid(voicePlayer);
                    if (uuid == null) {
                        uuid = tryResolveUuid(voicePlayer.getInstance());
                    }
                    player = resolveViaServer(uuid);
                }
                if (player == null) {
                    return;
                }

                int range = event.getDistance() > 0 ? event.getDistance() : SoundAttractConfig.SERVER.voiceChatNormalRange.get();
                addVoiceSound(player, range);
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[PlasmoIntegration] Exception in onServerSourceAudio", t);
            }
        }

        private static void addVoiceSound(ServerPlayer player, int range) {
            double weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
            net.minecraft.core.BlockPos pos = player.blockPosition();
            String dimString = ((ServerLevel) player.level()).dimension().identifier().toString();

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[PlasmoIntegration] VOICE_CHAT forwarded via Plasmo: player={} range={} dim={}",
                        player.getGameProfile().name(), range, ((ServerLevel) player.level()).dimension().identifier());
            }

            MinecraftServer server = ((ServerLevel) player.level()).getServer();
            if (server != null) {
                server.execute(() -> SoundTracker.addSound(null, pos, dimString,
                        range, weight, lifetime, SoundMessage.VOICE_CHAT_SOUND_ID.toString()));
            }
        }

        private static ServerPlayer resolveMcPlayer(Object instance) {
            if (instance instanceof ServerPlayer player) {
                return player;
            }
            if (instance == null) {
                return null;
            }

            String[] methodNames = new String[]{"getPlayer", "getMinecraftPlayer", "getHandle", "getNms", "player", "getInstance"};
            for (String methodName : methodNames) {
                try {
                    Method method = instance.getClass().getMethod(methodName);
                    method.setAccessible(true);
                    Object value = method.invoke(instance);
                    if (value instanceof ServerPlayer player) {
                        return player;
                    }
                } catch (Throwable ignored) {
                }
            }

            String[] fieldNames = new String[]{"player", "minecraftPlayer", "handle", "nms"};
            for (String fieldName : fieldNames) {
                try {
                    Field field = instance.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    if (value instanceof ServerPlayer player) {
                        return player;
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        private static UUID tryResolveUuid(Object object) {
            if (object == null) {
                return null;
            }

            String[] methodNames = new String[]{"getUUID", "getUuid", "getUniqueId", "getId"};
            for (String methodName : methodNames) {
                try {
                    Method method = object.getClass().getMethod(methodName);
                    method.setAccessible(true);
                    Object value = method.invoke(object);
                    if (value instanceof UUID uuid) {
                        return uuid;
                    }
                    if (value instanceof String text) {
                        try {
                            return UUID.fromString(text);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            String[] fieldNames = new String[]{"uuid", "UUID", "id", "uniqueId"};
            for (String fieldName : fieldNames) {
                try {
                    Field field = object.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value instanceof UUID uuid) {
                        return uuid;
                    }
                    if (value instanceof String text) {
                        try {
                            return UUID.fromString(text);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        private static ServerPlayer resolveViaServer(UUID uuid) {
            if (uuid == null) {
                return null;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return null;
            }
            return server.getPlayerList().getPlayer(uuid);
        }
    }
}
