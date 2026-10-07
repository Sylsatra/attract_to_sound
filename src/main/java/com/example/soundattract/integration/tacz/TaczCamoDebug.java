package com.example.soundattract.integration.tacz;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TaczCamoDebug {
    private static final Cache<String, String> LAYER_STATUS = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private static final Cache<UUID, String> RENDER_STATUS = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();
    private static final ConcurrentHashMap<EntityType<?>, Optional<DebugLoggingConfig.Feature>> FEATURES_BY_TYPE = new ConcurrentHashMap<>();

    private TaczCamoDebug() { }

    public static boolean isTracked(EntityType<?> type) {
        return featureFor(type) != null;
    }

    public static void logLayerStatus(EntityType<?> type, String path, String status, String rendererClass) {
        if (!isEnabledForEitherIntegration()) {
            return;
        }
        DebugLoggingConfig.Feature feature = featureFor(type);
        if (feature == null || !DebugLoggingConfig.isEnabled(feature)) {
            return;
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String entityType = id == null ? "<unknown>" : id.toString();
        String key = entityType + '|' + path;
        String previous = LAYER_STATUS.asMap().put(key, status);
        if (!status.equals(previous)) {
            log(feature, "Camo renderer layer: entityType={} path={} status={} renderer={}",
                    entityType, path, status, rendererClass == null ? "<none>" : rendererClass);
        }
    }

    public static void logRenderStatus(LivingEntity entity, String path, String status) {
        if (entity == null || !isEnabledForEitherIntegration()) {
            return;
        }
        EntityType<?> type = entity.getType();
        DebugLoggingConfig.Feature feature = featureFor(type);
        if (feature == null || !DebugLoggingConfig.isEnabled(feature)) {
            return;
        }

        UUID uuid = entity.getUUID();
        String value = path + '|' + status;
        String previous = RENDER_STATUS.getIfPresent(uuid);
        if (!value.equals(previous)) {
            RENDER_STATUS.put(uuid, value);
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            log(feature, "Camo render status: entityType={} entity={} path={} status={}", id, uuid, path, status);
        }
    }

    private static boolean isEnabledForEitherIntegration() {
        return DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ_NPCS)
                || DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ_HOSTILES);
    }

    private static DebugLoggingConfig.Feature featureFor(EntityType<?> type) {
        if (type == null) {
            return null;
        }
        return FEATURES_BY_TYPE.computeIfAbsent(type, TaczCamoDebug::lookupFeature).orElse(null);
    }

    private static Optional<DebugLoggingConfig.Feature> lookupFeature(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        DebugLoggingConfig.Feature feature = id == null ? null : switch (id.getNamespace()) {
            case "tacz_npc" -> DebugLoggingConfig.Feature.TACZ_NPCS;
            case "tacz_hostiles" -> DebugLoggingConfig.Feature.TACZ_HOSTILES;
            default -> null;
        };
        return Optional.ofNullable(feature);
    }

    private static void log(DebugLoggingConfig.Feature feature, String message, Object... args) {
        String integration = feature == DebugLoggingConfig.Feature.TACZ_NPCS ? "TACZ NPCs" : "TACZ Hostiles";
        SoundAttractMod.LOGGER.info("[{}] " + message, prepend(integration, args));
    }

    private static Object[] prepend(String first, Object[] rest) {
        Object[] args = new Object[rest.length + 1];
        args[0] = first;
        System.arraycopy(rest, 0, args, 1, rest.length);
        return args;
    }
}
