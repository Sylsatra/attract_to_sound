package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;

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

    private record SmokeEntry(Vec3 center, double radius, long tickCreated) {}
    private record FireEntry(Vec3 center, double dangerRadius, long tickCreated) {}

    public static void scanAndRebuild(Level level, long currentTick, int smokeMaxAgeTicks) {
        if (level == null || !CsGrenadesCompat.isLoaded()) {
            return;
        }

        ACTIVE_FIRE.keySet().removeIf(key -> key != null && level.dimension().location().equals(key.dimension()));
        ACTIVE_SMOKE.keySet().removeIf(key -> key != null && level.dimension().location().equals(key.dimension()));

        try {
            for (Entity entity : CsGrenadesEventHandler.trackedGrenadesSnapshot(level)) {
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
