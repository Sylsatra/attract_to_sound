package com.example.soundattract.event;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.data.DataDrivenTags;
import com.example.soundattract.integration.enhancedai.EnhancedAICompat;
import com.example.soundattract.los.OptimizedLOS;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class FovEvents {

    public static final Logger LOGGER = LogManager.getLogger();

    private static final double BACKSTAB_DAMAGE_MULTIPLIER = 1.2;

    private record FovData(double horizontal, double vertical) {

    }

    private static volatile FovData CONFIG_DEFAULT_FOV = null;

    private static final Set<EntityType<?>> DEVELOPER_EXCLUSIONS = Set.of(
            EntityType.WARDEN,
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    private static volatile Map<ResourceLocation, FovData> CONFIG_FOV_CACHE = null;
    private static volatile Set<ResourceLocation> USER_EXCLUSION_CACHE = null;

    private record LosCacheKey(String dim, int sx, int sy, int sz, int ex, int ey, int ez) {
    }

    private record LosCacheEntry(boolean result, long gameTime) {
    }

    private record LosPairKey(String dim, int lookerId, int targetId) {
    }

    private record LosPairEntry(boolean result, long gameTime) {
    }

    private static final Cache<LosCacheKey, LosCacheEntry> LOS_CACHE = Caffeine.newBuilder()
            .maximumSize(65536)
            .expireAfterWrite(Duration.ofSeconds(10))
            .build();
    private static final Cache<LosPairKey, LosPairEntry> LOS_PAIR_CACHE = Caffeine.newBuilder()
            .maximumSize(65536)
            .expireAfterWrite(Duration.ofSeconds(10))
            .build();

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOS_CACHE.invalidateAll();
        LOS_PAIR_CACHE.invalidateAll();
    }

    private static void buildCaches() {
        double defaultH = SoundAttractConfig.COMMON.defaultHorizontalFov.get();
        double defaultV = SoundAttractConfig.COMMON.defaultVerticalFov.get();
        FovData defaultFov = new FovData(defaultH, defaultV);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
            LOGGER.info("[FOV Config] Loaded default FOV: {} horizontal, {} vertical.", defaultH, defaultV);
        }

        Set<ResourceLocation> exclusions = new HashSet<>();
        List<? extends String> exclusionList = SoundAttractConfig.COMMON.fovExclusionList.get();
        for (String entry : exclusionList) {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(entry.trim());
                if (loc != null) {
                    exclusions.add(loc);
                } else {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                        LOGGER.warn("[FOV Config] Malformed exclusion entry, skipping: " + entry);
                    }
                }
            } catch (Exception e) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                    LOGGER.error("[FOV Config] Failed to parse exclusion entry: " + entry, e);
                }
            }
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
            LOGGER.info("[FOV Config] Loaded {} user-defined exclusions.", exclusions.size());
        }

        Map<ResourceLocation, FovData> overrides = new HashMap<>();
        List<? extends String> overrideList = SoundAttractConfig.COMMON.fovOverrides.get();
        for (String entry : overrideList) {
            try {
                String[] parts = entry.split(",");
                if (parts.length != 3) {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                        LOGGER.warn("[FOV Config] Malformed FOV override, skipping: " + entry);
                    }
                    continue;
                }
                ResourceLocation mobId = ResourceLocation.tryParse(parts[0].trim());
                if (mobId == null) {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                        LOGGER.warn("[FOV Config] Malformed mob identifier in override, skipping: " + entry);
                    }
                    continue;
                }
                double h = Double.parseDouble(parts[1].trim());
                double v = Double.parseDouble(parts[2].trim());
                overrides.put(mobId, new FovData(h, v));
            } catch (Exception e) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                    LOGGER.error("[FOV Config] Failed to parse FOV override entry: " + entry, e);
                }
            }
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
            LOGGER.info("[FOV Config] Loaded {} custom FOV overrides.", overrides.size());
        }
        CONFIG_DEFAULT_FOV = defaultFov;
        USER_EXCLUSION_CACHE = Set.copyOf(exclusions);
        CONFIG_FOV_CACHE = Map.copyOf(overrides);
    }

    @SubscribeEvent
    public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getVisibilityModifier() <= 0) {
            return;
        }
        if (!(event.getEntity() instanceof Mob looker)) {
            return;
        }

        if (SoundAttractConfig.isStealthBypassed(looker)) {
            return;
        }
        Entity target = event.getLookingEntity();
        if (target == null) {
            return;
        }

        if (SoundAttractConfig.COMMON.enableXrayTargeting.get()) {
            double xrayRange = StealthDetectionEvents.getEffectiveXrayRange(looker);
            if (xrayRange > 0d) {
                double distSq = looker.distanceToSqr(target);
                if (distSq <= xrayRange * xrayRange) {
                    return;
                }
            }
        }

        if (!isTargetInFov(looker, target, false)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onMobHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == mob) {
            return;
        }

        if (!isTargetInFov(mob, attacker, true)) {
            float originalDamage = event.getAmount();
            float newDamage = (float) (originalDamage * BACKSTAB_DAMAGE_MULTIPLIER);
            event.setAmount(newDamage);

            if (attacker instanceof Player) {
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, mob.getSoundSource(), 1.0F, 1.2F);
            }
        }
    }

    public static boolean isTargetInFov(Mob looker, Entity target, boolean checkObstructions) {
        if (CONFIG_FOV_CACHE == null) {
            buildCaches();
        }

        if (SoundAttractConfig.isStealthBypassed(looker)) {
            return true;
        }

        ResourceLocation lookerId = EntityType.getKey(looker.getType());

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
        if (isSmokeBlockingLineOfSight(looker, target)) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                SoundAttractMod.LOGGER.debug("[Smoke] Blocking LOS for mob {} to target {}", looker.getName().getString(), target.getName().getString());
            }
            return false;
        }
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

    public static boolean isSmokeBlockingLineOfSight(Mob looker, Entity target) {
        if (looker == null || target == null
                || !com.example.soundattract.integration.csgrenades.CsGrenadesCompat.isLoaded()
                || !com.example.soundattract.config.SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
                || !com.example.soundattract.config.SoundAttractConfig.COMMON.enableSmokeLosBlocking.get()) {
            return false;
        }
        Vec3 eye = looker.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        int maxAge = com.example.soundattract.config.SoundAttractConfig.COMMON.smokeLifetimeMaxTicks.get();
        long currentTick = looker.level().getGameTime();
        return com.example.soundattract.integration.csgrenades.CsGrenadesTracker.smokeBlocksRay(
                looker.level(), eye, targetCenter, maxAge, currentTick);
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
        final String dim = level.dimension().location().toString();
        if (useCache) {
            LosPairKey pairKey = new LosPairKey(dim, looker.getId(), target.getId());
            LosPairEntry existing = LOS_PAIR_CACHE.getIfPresent(pairKey);
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
            if (maxEntriesFinal > 0 && LOS_PAIR_CACHE.asMap().size() > maxEntriesFinal) {
                java.util.Iterator<java.util.Map.Entry<LosPairKey, LosPairEntry>> it = LOS_PAIR_CACHE.asMap().entrySet().iterator();
                while (it.hasNext()) {
                    java.util.Map.Entry<LosPairKey, LosPairEntry> e = it.next();
                    if ((now - e.getValue().gameTime()) > 1L) {
                        it.remove();
                    }
                }
                if (LOS_PAIR_CACHE.asMap().size() > maxEntriesFinal) {
                    int toRemove = LOS_PAIR_CACHE.asMap().size() - maxEntriesFinal;
                    java.util.Iterator<java.util.Map.Entry<LosPairKey, LosPairEntry>> it2 = LOS_PAIR_CACHE.asMap().entrySet().iterator();
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

        String dim = level.dimension().location().toString();
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
        LosCacheEntry existing = LOS_CACHE.getIfPresent(cacheKey);
        if (existing != null && (now - existing.gameTime()) <= ttlTicksFinal) {
            return existing.result();
        }

        boolean computed = raycastIgnoringNonBlockingUncached(level, start, end, looker);
        LOS_CACHE.put(cacheKey, new LosCacheEntry(computed, now));
        if (maxEntriesFinal > 0 && LOS_CACHE.asMap().size() > maxEntriesFinal) {
            java.util.Iterator<java.util.Map.Entry<LosCacheKey, LosCacheEntry>> it = LOS_CACHE.asMap().entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<LosCacheKey, LosCacheEntry> e = it.next();
                if ((now - e.getValue().gameTime()) > ttlTicksFinal) {
                    it.remove();
                }
            }
            if (LOS_CACHE.asMap().size() > maxEntriesFinal) {
                int toRemove = LOS_CACHE.asMap().size() - maxEntriesFinal;
                java.util.Iterator<java.util.Map.Entry<LosCacheKey, LosCacheEntry>> it2 = LOS_CACHE.asMap().entrySet().iterator();
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
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
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
            if (state.is(BlockTags.WALLS) || state.getBlock() instanceof IronBarsBlock) {
                return false;
            }
        } catch (Throwable ignored) {}

        try {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
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
