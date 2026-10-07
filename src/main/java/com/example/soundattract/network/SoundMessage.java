package com.example.soundattract.network;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.time.Duration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class SoundMessage {
    private final ResourceLocation soundId;
    private final double x, y, z;
    private final ResourceLocation dimension;
    private final Optional<UUID> sourcePlayerUUID;
    private final String action;
    private final float intensity01;
    private final boolean whispering;
    private final String animatorClass;
    private final String taczType;
    private final String pointBlankType;

    public static final ResourceLocation VOICE_CHAT_SOUND_ID = ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "voice_chat");
    public static final ResourceLocation POINT_BLANK_SOUND_ID = ResourceLocation.fromNamespaceAndPath("pointblank", "gun_action");
    private static final Cache<String, Boolean> DROPPED_SOUND_LOG_CACHE = Caffeine.newBuilder()
            .maximumSize(2048)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();

    private static final java.util.Set<String> KNOWN_ACTIONS = java.util.Set.of(
            "CRAWLING", "SNEAKING", "WALKING", "SPRINTING", "SPRINT_JUMPING");

    public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                        ResourceLocation dimension, Optional<UUID> sourcePlayerUUID,
                        String action, float intensity01, boolean whispering,
                        String animatorClass, String taczType, String pointBlankType) {
        this.soundId = soundId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.sourcePlayerUUID = sourcePlayerUUID == null ? Optional.empty() : sourcePlayerUUID;
        this.action = (action != null && KNOWN_ACTIONS.contains(action)) ? action : null;
        this.intensity01 = Float.isFinite(intensity01) ? Math.max(0f, Math.min(1f, intensity01)) : 0f;
        this.whispering = whispering;
        this.animatorClass = animatorClass;
        this.taczType = taczType;
        this.pointBlankType = pointBlankType;
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                        ResourceLocation dimension, Optional<UUID> sourcePlayerUUID) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, null, Float.NaN, false, null, null, null);
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                        ResourceLocation dimension, Optional<UUID> sourcePlayerUUID,
                        String action) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, action, Float.NaN, false, null, null, null);
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                        ResourceLocation dimension, Optional<UUID> sourcePlayerUUID,
                        float intensity01, boolean whispering) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, null, intensity01, whispering, null, null, null);
    }

    public static void encode(SoundMessage msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.soundId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeResourceLocation(msg.dimension);
        buf.writeBoolean(msg.sourcePlayerUUID.isPresent());
        msg.sourcePlayerUUID.ifPresent(buf::writeUUID);
        buf.writeBoolean(msg.action != null);
        if (msg.action != null) buf.writeUtf(msg.action, 32);
        buf.writeFloat(msg.intensity01);
        buf.writeBoolean(msg.whispering);
        buf.writeBoolean(msg.animatorClass != null);
        if (msg.animatorClass != null) buf.writeUtf(msg.animatorClass, 128);
        buf.writeBoolean(msg.taczType != null);
        if (msg.taczType != null) buf.writeUtf(msg.taczType, 128);
        buf.writeBoolean(msg.pointBlankType != null);
        if (msg.pointBlankType != null) buf.writeUtf(msg.pointBlankType, 128);
    }

    public static SoundMessage decode(FriendlyByteBuf buf) {
        ResourceLocation soundId = buf.readResourceLocation();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        ResourceLocation dimension = buf.readResourceLocation();
        Optional<UUID> sourcePlayerUUID = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
        String rawAction = buf.readBoolean() ? buf.readUtf(32) : null;
        float intensity01 = buf.readFloat();
        boolean whispering = buf.readBoolean();
        String animatorClass = buf.readBoolean() ? buf.readUtf(128) : null;
        String taczType = buf.readBoolean() ? buf.readUtf(128) : null;
        String pointBlankType = buf.readBoolean() ? buf.readUtf(128) : null;
        return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID,
                rawAction, intensity01, whispering, animatorClass, taczType, pointBlankType);
    }

    public static void handle(SoundMessage msg, Supplier<NetworkEvent.Context> ctx) {
        try {
            if (msg == null || msg.soundId == null || msg.dimension == null) {
                if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
                return;
            }
            ResourceLocation loc = msg.soundId;
            boolean isIntegration = (msg.taczType != null) || (msg.pointBlankType != null);
            ServerPlayer preSender = (ctx != null && ctx.get() != null) ? ctx.get().getSender() : null;
            if (preSender != null) {
                com.example.soundattract.ai.LivingWallCoordinator.recordCombatThreat(preSender.serverLevel(), preSender);
            }
            if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
                    && (loc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc))
                    && !msg.soundId.equals(VOICE_CHAT_SOUND_ID)
                    && !msg.soundId.equals(POINT_BLANK_SOUND_ID)
                    && !isIntegration) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_TRACKING)
                        && DROPPED_SOUND_LOG_CACHE.asMap().putIfAbsent(msg.dimension + ":" + loc, Boolean.TRUE) == null) {
                    SoundAttractMod.LOGGER.info("[SoundMessage] Dropping sound {} because it is not in whitelist (dim={})", loc, msg.dimension);
                }
                if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
                return;
            }

            Runnable logic = () -> {
                if (!SoundAttractConfig.serverReady()) return;
                ServerPlayer sender = (ctx != null && ctx.get() != null)
                                  ? ctx.get().getSender() : null;
                ServerLevel serverLevel = sender != null
                        ? sender.serverLevel()
                        : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                              .getLevel(net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.DIMENSION,
                                        msg.dimension));

                if (serverLevel == null) {
                    SoundAttractMod.LOGGER.warn("[SoundMessage] serverLevel is null for {}", msg.dimension);
                    return;
                }
                if (!serverLevel.dimension().location().equals(msg.dimension)) {
                    SoundAttractMod.LOGGER.warn("[SoundMessage] dimension mismatch ({} != {})",
                            serverLevel.dimension().location(), msg.dimension);
                    return;
                }

                BlockPos pos = BlockPos.containing(msg.x, msg.y, msg.z);
                if (pos.equals(BlockPos.ZERO) && sender != null) pos = sender.blockPosition();
                String dimString = msg.dimension.toString();
                int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

                if (sender != null) {
                    com.example.soundattract.ai.LivingWallCoordinator.recordCombatThreat(serverLevel, sender);
                }

                double range;
                double weight;
                String resolvedIdForAdd = null;

                if (msg.action != null) {
                    Integer r = SoundAttractConfig.PLAYER_ACTION_RANGES_CACHE.get(msg.action);
                    Double w = SoundAttractConfig.PLAYER_ACTION_WEIGHTS_CACHE.get(msg.action);
                    if (r == null || w == null) {
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_TRACKING)) {
                            SoundAttractMod.LOGGER.info("[SoundMessage] Unknown action {}, dropping", msg.action);
                        }
                        return;
                    }
                    range = r;
                    weight = w;
                    if (sender != null) {
                        double tanMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.soundMultiplier(sender, serverLevel.getGameTime());
                        range *= tanMultiplier;
                        weight *= tanMultiplier;
                    }
                } else if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                    int baseRange = msg.whispering
                            ? SoundAttractConfig.SERVER.voiceChatWhisperRange.get()
                            : SoundAttractConfig.SERVER.voiceChatNormalRange.get();
                    double normDb = Math.max(0f, Math.min(1f, msg.intensity01)) * 127.0;
                    double factor = lookupVoiceChatFactor(normDb);
                    range = Math.round(baseRange * factor);
                    if (range <= 0) return;
                    weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
                    resolvedIdForAdd = VOICE_CHAT_SOUND_ID.toString();
                } else {
                    SoundAttractConfig.SoundDefaultEntry def =
                            SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(msg.soundId);
                    if (def == null) {
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_TRACKING)) {
                            SoundAttractMod.LOGGER.info("[SoundMessage] No default entry for {}, dropping", msg.soundId);
                        }
                        return;
                    }
                    range = def.range();
                    weight = def.weight();
                }

                range = Math.min(range, SoundAttractConfig.SERVER.maxClientSoundRange.get());

                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_TRACKING)) {
                    SoundAttractMod.LOGGER.info(
                            "[SoundMessage] resolved soundId={} action={} whispering={} intensity01={} -> range={} weight={}",
                            msg.soundId, msg.action, msg.whispering, msg.intensity01, range, weight);
                }

                if (resolvedIdForAdd != null) {
                    SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, resolvedIdForAdd);
                } else {
                    SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundId);
                    if (se != null) {
                        SoundTracker.addSound(se, pos, dimString, (int) range, weight, lifetime);
                    } else {
                        SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, msg.soundId.toString());
                    }
                }
                if (sender != null) {
                    com.example.soundattract.ai.LivingWallCoordinator.recordCombatThreat(serverLevel, sender);
                }
            };

            if (ctx != null && ctx.get() != null) {
                ctx.get().enqueueWork(logic);
                ctx.get().setPacketHandled(true);
            } else {
                logic.run();
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("[SoundMessage] Exception for soundId={}", msg.soundId, e);
            if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
        }
    }

    private static double lookupVoiceChatFactor(double normDb) {
        java.util.List<? extends String> raw = SoundAttractConfig.SERVER.voiceChatDbThresholdMap.get();
        if (raw == null || raw.isEmpty()) {
            if (normDb >= 50.0) return 1.0;
            if (normDb >= 30.0) return 0.7;
            if (normDb >= 10.0) return 0.3;
            return 0.0;
        }
        for (String entry : raw) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) continue;
            try {
                double thr = Double.parseDouble(parts[0].trim());
                double mult = Double.parseDouble(parts[1].trim());
                if (normDb >= thr) return mult;
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}