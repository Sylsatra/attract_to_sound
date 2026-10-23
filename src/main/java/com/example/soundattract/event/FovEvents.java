package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.data.DataDrivenTags;
import com.example.soundattract.integration.enhancedai.EnhancedAICompat;
import com.example.soundattract.los.OptimizedLOS;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.registries.BuiltInRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FovEvents {

    public static final Logger LOGGER = LogManager.getLogger();

    public static void register() {
    }

    private static final double BACKSTAB_DAMAGE_MULTIPLIER = 1.2;

    private record FovData(double horizontal, double vertical) {

    }

    private static FovData CONFIG_DEFAULT_FOV = null;

    private static final Set<EntityType<?>> DEVELOPER_EXCLUSIONS = Set.of(
            EntityType.WARDEN,
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    private static Map<Identifier, FovData> CONFIG_FOV_CACHE = null;
    private static Set<Identifier> USER_EXCLUSION_CACHE = null;

    private record LosCacheKey(String dim, int sx, int sy, int sz, int ex, int ey, int ez) {
    }

    private record LosCacheEntry(boolean result, long gameTime) {
    }

    private record LosPairKey(String dim, int lookerId, int targetId) {
    }

    private record LosPairEntry(boolean result, long gameTime) {
    }

    private static final ConcurrentHashMap<LosCacheKey, LosCacheEntry> LOS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<LosPairKey, LosPairEntry> LOS_PAIR_CACHE = new ConcurrentHashMap<>();

    private static void buildCaches() {
        double defaultH = SoundAttractConfig.COMMON.defaultHorizontalFov.get();
        double defaultV = SoundAttractConfig.COMMON.defaultVerticalFov.get();
        CONFIG_DEFAULT_FOV = new FovData(defaultH, defaultV);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            LOGGER.info("[FOV Config] Loaded default FOV: {} horizontal, {} vertical.", defaultH, defaultV);
        }

        USER_EXCLUSION_CACHE = new HashSet<>();
        List<? extends String> exclusionList = SoundAttractConfig.COMMON.fovExclusionList.get();
        for (String entry : exclusionList) {
            try {
                Identifier loc = Identifier.tryParse(entry.trim());
                if (loc != null) {
                    USER_EXCLUSION_CACHE.add(loc);
                } else {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        LOGGER.warn("[FOV Config] Malformed exclusion entry, skipping: " + entry);
                    }
                }
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    LOGGER.error("[FOV Config] Failed to parse exclusion entry: " + entry, e);
                }
            }
        }
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            LOGGER.info("[FOV Config] Loaded {} user-defined exclusions.", USER_EXCLUSION_CACHE.size());
        }

        CONFIG_FOV_CACHE = new HashMap<>();
        List<? extends String> overrideList = SoundAttractConfig.COMMON.fovOverrides.get();
        for (String entry : overrideList) {
            try {
                String[] parts = entry.split(",");
                if (parts.length != 3) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        LOGGER.warn("[FOV Config] Malformed FOV override, skipping: " + entry);
                    }
                    continue;
                }
                Identifier mobId = Identifier.tryParse(parts[0].trim());
                if (mobId == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        LOGGER.warn("[FOV Config] Malformed mob identifier in override, skipping: " + entry);
                    }
                    continue;
                }
                double h = Double.parseDouble(parts[1].trim());
                double v = Double.parseDouble(parts[2].trim());
                CONFIG_FOV_CACHE.put(mobId, new FovData(h, v));
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    LOGGER.error("[FOV Config] Failed to parse FOV override entry: " + entry, e);
                }
            }
        }
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            LOGGER.info("[FOV Config] Loaded {} custom FOV overrides.", CONFIG_FOV_CACHE.size());
        }
    }

    public static double modifyVisibility(double currentModifier, Mob looker, LivingEntity target) {
        if (currentModifier <= 0) {
            return currentModifier;
        }
        if (!isTargetInFov(looker, target, true)) {
            return 1.0;
        }
        return currentModifier;
    }

    public static float modifyHurtDamage(float amount, Mob mob, Player attacker) {
        if (!isTargetInFov(mob, attacker, true)) {
            return (float) (amount * BACKSTAB_DAMAGE_MULTIPLIER);
        }
        return amount;
    }

    public static boolean isTargetInFov(Mob looker, Entity target, boolean checkObstructions) {
        if (CONFIG_FOV_CACHE == null) {
            buildCaches();
        }

        if (SoundAttractConfig.isStealthBypassed(looker)) {
            return true;
        }

        Identifier lookerId = EntityType.getKey(looker.getType());

        if (DEVELOPER_EXCLUSIONS.contains(looker.getType())) {
            return true;
        }
        if (USER_EXCLUSION_CACHE.contains(lookerId)) {
            return true;
        }

        FovData fov = CONFIG_FOV_CACHE.getOrDefault(lookerId, CONFIG_DEFAULT_FOV);
        if (fov.horizontal() >= 360) {
            return true;
        }

        if (checkObstructions && !hasSmartLineOfSight(looker, target)) {
            return false;
        }

        return isWithinFieldOfView(looker, target, fov.horizontal(), fov.vertical());
    }

    public static boolean hasSmartLineOfSight(Mob looker, Entity target) {
        boolean enableOptimized = true;
        try {
            enableOptimized = SoundAttractConfig.COMMON.enableOptimizedLos.get();
        } catch (Throwable ignored) {
        }
        if (enableOptimized) {
            return OptimizedLOS.canSee(looker, target);
        }
        return hasSmartLineOfSightLegacy(looker, target);
    }

    private static boolean hasSmartLineOfSightLegacy(Mob looker, Entity target) {
        Level level = looker.level();

        boolean useCache = false;
        int maxEntries = 0;
        try {
            useCache = SoundAttractConfig.COMMON.enableRaycastCache.get();
            maxEntries = SoundAttractConfig.COMMON.raycastCacheMaxEntries.get();
        } catch (Throwable ignored) {
        }

        final int maxEntriesFinal = maxEntries;
        final long now = level.getGameTime();
        final String dim = level.dimension().identifier().toString();
        if (useCache) {
            LosPairKey pairKey = new LosPairKey(dim, looker.getId(), target.getId());
            LosPairEntry existing = LOS_PAIR_CACHE.get(pairKey);
            if (existing != null && (now - existing.gameTime()) <= 1L) {
                return existing.result();
            }
        }

        Vec3 eyeToEye = target.getEyePosition();
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 feet = target.position().add(0, Math.max(0.1, target.getBbHeight() * 0.15), 0);

        Vec3 start = looker.getEyePosition();
        boolean result = raycastIgnoringNonBlockingCached(level, start, eyeToEye, looker)
                || raycastIgnoringNonBlockingCached(level, start, center, looker)
                || raycastIgnoringNonBlockingCached(level, start, feet, looker);

        if (useCache) {
            LosPairKey pairKey = new LosPairKey(dim, looker.getId(), target.getId());
            LOS_PAIR_CACHE.put(pairKey, new LosPairEntry(result, now));
            if (maxEntriesFinal > 0 && LOS_PAIR_CACHE.size() > maxEntriesFinal) {
                java.util.Iterator<java.util.Map.Entry<LosPairKey, LosPairEntry>> it = LOS_PAIR_CACHE.entrySet().iterator();
                while (it.hasNext()) {
                    java.util.Map.Entry<LosPairKey, LosPairEntry> e = it.next();
                    if ((now - e.getValue().gameTime()) > 1L) {
                        it.remove();
                    }
                }
                if (LOS_PAIR_CACHE.size() > maxEntriesFinal) {
                    int toRemove = LOS_PAIR_CACHE.size() - maxEntriesFinal;
                    java.util.Iterator<java.util.Map.Entry<LosPairKey, LosPairEntry>> it2 = LOS_PAIR_CACHE.entrySet().iterator();
                    for (int i = 0; i < toRemove && it2.hasNext(); i++) {
                        it2.next();
                        it2.remove();
                    }
                }
            }
        }

        return result;
    }

    private static boolean raycastIgnoringNonBlockingCached(Level level, Vec3 start, Vec3 end, Mob looker) {
        boolean useCache = false;
        long ttlTicks = 2L;
        int maxEntries = 0;
        try {
            useCache = SoundAttractConfig.COMMON.enableRaycastCache.get();
            long cfgTtl = SoundAttractConfig.COMMON.raycastCacheTtlTicks.get();
            ttlTicks = Math.max(1L, Math.min(ttlTicks, cfgTtl));
            maxEntries = SoundAttractConfig.COMMON.raycastCacheMaxEntries.get();
        } catch (Throwable ignored) {
        }

        final long ttlTicksFinal = ttlTicks;
        final int maxEntriesFinal = maxEntries;

        if (!useCache || level == null || start == null || end == null) {
            return raycastIgnoringNonBlockingUncached(level, start, end, looker);
        }

        String dim = level.dimension().identifier().toString();
        if (QuantifiedCacheCompat.isUsable()) {
            String key = new StringBuilder(96)
                    .append(dim).append('|')
                    .append(q(start.x)).append(',').append(q(start.y)).append(',').append(q(start.z)).append('|')
                    .append(q(end.x)).append(',').append(q(end.y)).append(',').append(q(end.z))
                    .toString();

            Boolean cached = QuantifiedCacheCompat.getCached(
                "soundattract_los_raycast",
                key,
                () -> Boolean.valueOf(raycastIgnoringNonBlockingUncached(level, start, end, looker)),
                ttlTicksFinal,
                maxEntriesFinal
            );
            return cached != null && cached.booleanValue();
        }

        long now = level.getGameTime();
        LosCacheKey cacheKey = new LosCacheKey(dim, q(start.x), q(start.y), q(start.z), q(end.x), q(end.y), q(end.z));
        LosCacheEntry existing = LOS_CACHE.get(cacheKey);
        if (existing != null && (now - existing.gameTime()) <= ttlTicksFinal) {
            return existing.result();
        }

        boolean computed = raycastIgnoringNonBlockingUncached(level, start, end, looker);
        LOS_CACHE.put(cacheKey, new LosCacheEntry(computed, now));
        if (maxEntriesFinal > 0 && LOS_CACHE.size() > maxEntriesFinal) {
            java.util.Iterator<java.util.Map.Entry<LosCacheKey, LosCacheEntry>> it = LOS_CACHE.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<LosCacheKey, LosCacheEntry> e = it.next();
                if ((now - e.getValue().gameTime()) > ttlTicksFinal) {
                    it.remove();
                }
            }
            if (LOS_CACHE.size() > maxEntriesFinal) {
                int toRemove = LOS_CACHE.size() - maxEntriesFinal;
                java.util.Iterator<java.util.Map.Entry<LosCacheKey, LosCacheEntry>> it2 = LOS_CACHE.entrySet().iterator();
                for (int i = 0; i < toRemove && it2.hasNext(); i++) {
                    it2.next();
                    it2.remove();
                }
            }
        }
        return computed;
    }

    private static int q(double v) {
        return (int) Math.round(v * 4.0);
    }

    public static boolean isNonBlockingVisionForLos(BlockState state, Level level, BlockPos pos) {
        return isNonBlockingVision(state, level, pos);
    }

    private static boolean raycastIgnoringNonBlockingUncached(Level level, Vec3 start, Vec3 end, Mob looker) {
        if (level == null || start == null || end == null) {
            return false;
        }
        return OptimizedLOS.hasLineOfSight(level, start, end, looker);
    }

    private static boolean isNonBlockingVision(BlockState state, Level level, BlockPos pos) {
        if (state == null || level == null || pos == null) {
            return false;
        }

        if (state.isAir()) return true;

        if (state.getBlock() instanceof DoorBlock) {
            try {
                Boolean open = state.getValue(DoorBlock.OPEN);
                if (open != null && open) return true;
            } catch (Throwable ignored) {}
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            try {
                Boolean open = state.getValue(TrapDoorBlock.OPEN);
                if (open != null && open) return true;
            } catch (Throwable ignored) {}
        }

        try {
            if (state.is(BlockTags.WALLS) || state.getBlock() instanceof IronBarsBlock) {
                return false;
            }
        } catch (Throwable ignored) {}

        try {
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            boolean inConfig = false;
            if (id != null) {
                if (SoundAttractConfig.NON_BLOCKING_VISION_ALLOW_CACHE.isEmpty()) {
                    List<? extends String> list = SoundAttractConfig.COMMON != null
                            ? SoundAttractConfig.COMMON.nonBlockingVisionAllowList.get()
                            : java.util.Collections.emptyList();
                    if (list != null && !list.isEmpty()) {
                        SoundAttractConfig.parseAndCacheNonBlockingVisionAllowList();
                    }
                }
                inConfig = SoundAttractConfig.NON_BLOCKING_VISION_ALLOW_CACHE.contains(id);
            }

            boolean enableDataDriven = SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.enableDataDriven.get();
            boolean inTag = false;
            if (enableDataDriven) {
                try {
                    inTag = state.is(DataDrivenTags.NON_BLOCKING_VISION);
                } catch (Throwable ignoredInner) {}
            }

            if (!enableDataDriven) {
                if (inConfig) return true;
            } else {
                String priority = SoundAttractConfig.COMMON.datapackPriority.get();
                boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
                if (datapackOverConfig) {
                    if (inTag) return true;
                    if (inConfig) return true;
                } else {
                    if (inConfig) return true;
                    if (inTag) return true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String path = id != null ? id.getPath() : "";
            if (path.contains("glass") && !path.contains("tinted")) {
                return true;
            }
        } catch (Throwable ignored) {}

        if (state.getBlock() instanceof IceBlock || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return true;
        }

        if (state.getBlock() instanceof FenceBlock || state.is(BlockTags.FENCES)) {
            return true;
        }

        try {
            VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
            if (shape.isEmpty()) return true;
        } catch (Throwable ignored) {}

        try {
            if (!state.isViewBlocking(level, pos)) return true;
        } catch (Throwable ignored) {}

        return false;
    }

    private static boolean isWithinFieldOfView(Mob looker, Entity target, double horizontalFovDegrees, double verticalFovDegrees) {
        Vec3 lookVector = looker.getLookAngle();
        Vec3 toTargetVector = target.position()
                .add(0, target.getEyeHeight() / 2.0, 0)
                .subtract(looker.getEyePosition())
                .normalize();

        Vec3 lookHorizontal = new Vec3(lookVector.x, 0, lookVector.z).normalize();
        Vec3 targetHorizontal = new Vec3(toTargetVector.x, 0, toTargetVector.z).normalize();
        double dotHorizontal = lookHorizontal.dot(targetHorizontal);
        double angleHorizontal = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dotHorizontal))));
        if (angleHorizontal > horizontalFovDegrees / 2.0) {
            return false;
        }

        double pitchLook = Math.toDegrees(Math.asin(lookVector.y));
        double pitchTarget = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toTargetVector.y))));
        double angleVertical = Math.abs(pitchTarget - pitchLook);
        return angleVertical <= verticalFovDegrees / 2.0;
    }
}
