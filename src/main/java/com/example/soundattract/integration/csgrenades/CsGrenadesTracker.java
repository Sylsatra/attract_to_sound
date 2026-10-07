package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CsGrenadesTracker {

    private record GrenadeKey(ResourceLocation dimension, UUID entityId) {}

    private static final Map<GrenadeKey, SmokeEntry> ACTIVE_SMOKE = new ConcurrentHashMap<>();
    private static final Map<GrenadeKey, FireEntry> ACTIVE_FIRE = new ConcurrentHashMap<>();


    private static final Map<ResourceLocation, List<SmokeEntry>> SMOKE_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<FireEntry>> FIRE_SNAPSHOTS = new ConcurrentHashMap<>();

    private static Method GET_GRENADE_TYPE_METHOD;
    private static Class<?> GRENADE_ENTITY_CLASS;
    private static boolean REFLECTION_INITIALIZED = false;

    private record SmokeEntry(Vec3 center, double radius, long tickCreated) {}
    private record FireEntry(Vec3 center, double dangerRadius, long tickCreated) {}

    private static void ensureReflection() {
        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        if (REFLECTION_INITIALIZED) {
            return;
        }
        try {
            GRENADE_ENTITY_CLASS = Class.forName("club.pisquad.minecraft.csgrenades.entity.CounterStrikeGrenadeEntity");
            GET_GRENADE_TYPE_METHOD = GRENADE_ENTITY_CLASS.getMethod("getGrenadeType");
            REFLECTION_INITIALIZED = true;
            SoundAttractMod.LOGGER.info("[CSGrenades] Successfully initialized grenade entity reflection");
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("[CSGrenades] Failed to initialize grenade entity reflection: {} (type: {})", e.getMessage(), e.getClass().getSimpleName());
        }
    }

    private static Class<?> THROWABLE_ITEM_PROJECTILE_CLASS;

    @SuppressWarnings("unchecked")
    public static void scanAndRebuild(Level level, long currentTick, int smokeMaxAgeTicks) {
        if (level == null || !CsGrenadesCompat.isLoaded()) {
            return;
        }
        ensureReflection();
        if (!REFLECTION_INITIALIZED || GRENADE_ENTITY_CLASS == null) {
            SoundAttractMod.LOGGER.warn("[CSGrenades] Cannot scan: reflection not initialized");
            return;
        }

        ACTIVE_FIRE.keySet().removeIf(key -> key != null && level.dimension().location().equals(key.dimension()));
        ACTIVE_SMOKE.keySet().removeIf(key -> key != null && level.dimension().location().equals(key.dimension()));

        try {
            if (THROWABLE_ITEM_PROJECTILE_CLASS == null) {
                THROWABLE_ITEM_PROJECTILE_CLASS = Class.forName("net.minecraft.world.entity.projectile.ThrowableItemProjectile");
            }
        } catch (Exception ignored) {}

        try {
            List<Entity> grenades = new ArrayList<>();
            java.util.Set<String> foundClasses = new java.util.HashSet<>();
            int throwableCount = 0;
            for (Entity entity : CsGrenadesEventHandler.trackedGrenadesSnapshot(level)) {
                String className = entity.getClass().getName();
                foundClasses.add(className);
                if (className.contains("grenade") || className.contains("Grenade") ||
                    className.contains("csgrenade") || className.contains("pisquad")) {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                        SoundAttractMod.LOGGER.debug("[CSGrenades] Found potential grenade: {} alive={}", className, entity.isAlive());
                    }
                }
                if (THROWABLE_ITEM_PROJECTILE_CLASS != null && THROWABLE_ITEM_PROJECTILE_CLASS.isInstance(entity)) {
                    throwableCount++;
                }
                if (GRENADE_ENTITY_CLASS.isInstance(entity) && entity.isAlive()) {
                    grenades.add(entity);
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                        SoundAttractMod.LOGGER.debug("[CSGrenades] Found grenade entity: {} type={}", className, entity.getType().getDescriptionId());
                    }
                }
            }
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] All entity classes in level: {}", foundClasses);
            }
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] ThrowableItemProjectile count: {}", throwableCount);
            }
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] GRENADE_ENTITY_CLASS = {}", GRENADE_ENTITY_CLASS != null ? GRENADE_ENTITY_CLASS.getName() : "null");
            }
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker scan found {} grenades in level", grenades.size());
            }

            for (Entity entity : grenades) {
                UUID id = entity.getUUID();
                Vec3 center = entity.position();
                String simpleName = entity.getClass().getSimpleName();

                switch (simpleName) {
                    case "SmokeGrenadeEntity" -> {
                        double smokeRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.smokeCloudRadius.get();
                        ACTIVE_SMOKE.put(new GrenadeKey(level.dimension().location(), id),
                                new SmokeEntry(center, smokeRadius, currentTick));
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: smoke grenade at {} (radius {})", center, String.format("%.2f", smokeRadius));
                        }
                    }
                    case "MolotovEntity", "IncendiaryEntity" -> {
                        double dangerRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.fireFleeDangerRadius.get();
                        ACTIVE_FIRE.put(new GrenadeKey(level.dimension().location(), id),
                                new FireEntry(center, dangerRadius, currentTick));
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: fire grenade at {} (radius {})", center, String.format("%.2f", dangerRadius));
                        }
                    }
                    case "FlashBangEntity", "HEGrenadeEntity", "DecoyGrenadeEntity" -> {
                    }
                    default -> {
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: unhandled grenade subclass '{}'", simpleName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("[CSGrenades] Error scanning CS Grenades: {}", e.getMessage(), e);
        }

        ResourceLocation dimension = level.dimension().location();
        SMOKE_SNAPSHOTS.put(dimension, ACTIVE_SMOKE.entrySet().stream()
                .filter(entry -> entry.getKey() != null && dimension.equals(entry.getKey().dimension()))
                .map(Map.Entry::getValue).toList());
        FIRE_SNAPSHOTS.put(dimension, ACTIVE_FIRE.entrySet().stream()
                .filter(entry -> entry.getKey() != null && dimension.equals(entry.getKey().dimension()))
                .map(Map.Entry::getValue).toList());
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
        SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker scan complete: {} smoke, {} fire entries",
                SMOKE_SNAPSHOTS.get(dimension).size(), FIRE_SNAPSHOTS.get(dimension).size());
        }
    }

    public static boolean smokeBlocksRay(Level level, Vec3 from, Vec3 to, int smokeMaxAgeTicks, long currentTick) {
        List<SmokeEntry> snapshot = level == null ? List.of() : SMOKE_SNAPSHOTS.getOrDefault(level.dimension().location(), List.of());
        if (from == null || to == null || snapshot.isEmpty()) {
            return false;
        }
        double smokeRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.smokeCloudRadius.get();
        for (SmokeEntry smoke : snapshot) {
            if ((currentTick - smoke.tickCreated) > smokeMaxAgeTicks) {
                continue;
            }
            if (SmokeLosSuppression.blocksRay(from, to, smoke.center, smokeRadius)) {
                return true;
            }
        }
        return false;
    }

    public static Vec3 nearestActiveFire(Level level, Vec3 mobPos, int fireMaxAgeTicks, long currentTick) {
        List<FireEntry> snapshot = level == null ? List.of() : FIRE_SNAPSHOTS.getOrDefault(level.dimension().location(), List.of());
        if (mobPos == null || snapshot.isEmpty()) {
            return null;
        }
        Vec3 nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (FireEntry fire : snapshot) {
            if ((currentTick - fire.tickCreated) > fireMaxAgeTicks) {
                continue;
            }
            double distSq = mobPos.distanceToSqr(fire.center);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = fire.center;
            }
        }
        return nearest;
    }

    public static void clear() {
        ACTIVE_SMOKE.clear();
        ACTIVE_FIRE.clear();
        SMOKE_SNAPSHOTS.clear();
        FIRE_SNAPSHOTS.clear();
    }

    public static void clearLevel(Level level) {
        if (level == null) {
            return;
        }
        ResourceLocation dimension = level.dimension().location();
        ACTIVE_SMOKE.keySet().removeIf(key -> key != null && dimension.equals(key.dimension()));
        ACTIVE_FIRE.keySet().removeIf(key -> key != null && dimension.equals(key.dimension()));
        SMOKE_SNAPSHOTS.remove(dimension);
        FIRE_SNAPSHOTS.remove(dimension);
    }
}
