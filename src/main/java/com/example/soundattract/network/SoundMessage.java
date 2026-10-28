package com.example.soundattract.network;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

public class SoundMessage implements CustomPacketPayload {
    public static final Type<SoundMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "sound_message"));
    public static final Identifier VOICE_CHAT_SOUND_ID = Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "voice_chat");

    public final Identifier soundId;
    public final double x, y, z;
    public final Identifier dimension;
    public final Optional<UUID> sourcePlayerUUID;
    public final String action;
    public final float intensity01;
    public final boolean whispering;
    public final String animatorClass;
    public final String taczType;

    private static final java.util.Set<String> KNOWN_ACTIONS = java.util.Set.of(
            "CRAWLING", "SNEAKING", "WALKING", "SPRINTING", "SPRINT_JUMPING");

    public static final StreamCodec<FriendlyByteBuf, SoundMessage> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SoundMessage decode(FriendlyByteBuf buf) {
            return SoundMessage.read(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SoundMessage msg) {
            SoundMessage.write(buf, msg);
        }
    };

    public SoundMessage(Identifier soundId, double x, double y, double z,
                        Identifier dimension, Optional<UUID> sourcePlayerUUID,
                        String action, float intensity01, boolean whispering,
                        String animatorClass, String taczType) {
        this.soundId = soundId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.sourcePlayerUUID = sourcePlayerUUID;
        this.action = (action != null && KNOWN_ACTIONS.contains(action)) ? action : null;
        this.intensity01 = Float.isFinite(intensity01) ? Math.max(0f, Math.min(1f, intensity01)) : 0f;
        this.whispering = whispering;
        this.animatorClass = animatorClass;
        this.taczType = taczType;
    }

    public SoundMessage(Identifier soundId, double x, double y, double z,
                        Identifier dimension, Optional<UUID> sourcePlayerUUID) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, null, Float.NaN, false, null, null);
    }

    public SoundMessage(Identifier soundId, double x, double y, double z,
                        Identifier dimension, Optional<UUID> sourcePlayerUUID,
                        String action) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, action, Float.NaN, false, null, null);
    }

    public SoundMessage(Identifier soundId, double x, double y, double z,
                        Identifier dimension, Optional<UUID> sourcePlayerUUID,
                        float intensity01, boolean whispering) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, null, intensity01, whispering, null, null);
    }

    public static void write(FriendlyByteBuf buf, SoundMessage msg) {
        buf.writeIdentifier(msg.soundId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeIdentifier(msg.dimension);
        buf.writeBoolean(msg.sourcePlayerUUID.isPresent());
        msg.sourcePlayerUUID.ifPresent(buf::writeUUID);
        buf.writeBoolean(msg.action != null);
        if (msg.action != null) buf.writeUtf(msg.action, 32);
        buf.writeFloat(msg.intensity01);
        buf.writeBoolean(msg.whispering);
        buf.writeBoolean(msg.animatorClass != null);
        if (msg.animatorClass != null) buf.writeUtf(msg.animatorClass);
        buf.writeBoolean(msg.taczType != null);
        if (msg.taczType != null) buf.writeUtf(msg.taczType);
    }

    public static SoundMessage read(FriendlyByteBuf buf) {
        Identifier soundId = buf.readIdentifier();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        Identifier dimension = buf.readIdentifier();
        Optional<UUID> sourcePlayerUUID = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
        String rawAction = buf.readBoolean() ? buf.readUtf(32) : null;
        float intensity01 = buf.readFloat();
        boolean whispering = buf.readBoolean();
        String animatorClass = buf.readBoolean() ? buf.readUtf() : null;
        String taczType = buf.readBoolean() ? buf.readUtf() : null;
        return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID,
                rawAction, intensity01, whispering, animatorClass, taczType);
    }

    public static void handle(SoundMessage msg, ServerPlayer sender) {
        try {
            Identifier loc = msg.soundId;
            boolean isIntegration = msg.taczType != null;
            if (sender != null) {
                com.example.soundattract.ai.LivingWallCoordinator.recordCombatThreat((ServerLevel) sender.level(), sender);
            }
            if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
                    && (loc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc))
                    && !msg.soundId.equals(VOICE_CHAT_SOUND_ID)
                    && !isIntegration) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundMessage] Dropping sound {} because it is not in whitelist (dim={})", loc, msg.dimension);
                }
                return;
            }

            if (!SoundAttractConfig.serverReady()) return;

            MinecraftServer server = sender != null ? sender.level().getServer() : null;
            ServerLevel serverLevel = sender != null
                    ? (ServerLevel) sender.level()
                    : (server != null ? server.getLevel(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            msg.dimension)) : null);

            if (serverLevel == null) {
                Soundattract.LOGGER.warn("[SoundMessage] serverLevel is null for {}", msg.dimension);
                return;
            }
            if (!serverLevel.dimension().identifier().equals(msg.dimension)) {
                Soundattract.LOGGER.warn("[SoundMessage] dimension mismatch ({} != {})",
                        serverLevel.dimension().identifier(), msg.dimension);
                return;
            }

            BlockPos pos = BlockPos.containing(msg.x, msg.y, msg.z);
            if (pos.equals(BlockPos.ZERO) && sender != null) pos = sender.blockPosition();
            String dimString = msg.dimension.toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            double range;
            double weight;
            String resolvedIdForAdd = null;

            if (msg.action != null) {
                Integer r = SoundAttractConfig.PLAYER_ACTION_RANGES_CACHE.get(msg.action);
                Double w = SoundAttractConfig.PLAYER_ACTION_WEIGHTS_CACHE.get(msg.action);
                if (r == null || w == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info("[SoundMessage] Unknown action {}, dropping", msg.action);
                    }
                    return;
                }
                range = r;
                weight = w;
                if (sender != null) {
                    double multiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                            .soundMultiplier(sender, serverLevel.getGameTime());
                    range *= multiplier;
                    weight *= multiplier;
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
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info("[SoundMessage] No default entry for {}, dropping", msg.soundId);
                    }
                    return;
                }
                range = def.range();
                weight = def.weight();
            }

            range = Math.min(range, SoundAttractConfig.SERVER.maxClientSoundRange.get());

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info(
                        "[SoundMessage] resolved soundId={} action={} whispering={} intensity01={} -> range={} weight={}",
                        msg.soundId, msg.action, msg.whispering, msg.intensity01, range, weight);
            }

            if (resolvedIdForAdd != null) {
                SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, resolvedIdForAdd);
            } else {
                SoundEvent se = BuiltInRegistries.SOUND_EVENT.getValue(msg.soundId);
                if (se != null) {
                    SoundTracker.addSound(se, pos, dimString, (int) range, weight, lifetime);
                } else {
                    SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, msg.soundId.toString());
                }
            }
        } catch (Exception e) {
            Soundattract.LOGGER.error("[SoundMessage] Exception for soundId={}", msg.soundId, e);
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
