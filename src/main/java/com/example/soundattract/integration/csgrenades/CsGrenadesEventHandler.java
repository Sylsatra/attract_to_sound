package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.los.OptimizedLOS;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class CsGrenadesEventHandler {

    private static final Object LOCK = new Object();
    private static final Map<net.minecraft.resources.ResourceLocation, Long> LAST_SCAN_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Cache<Mob, Long> FLEE_COOLDOWNS = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private static final Cache<UUID, Boolean> PROCESSED_FLASHBANGS = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private static final Set<Entity> TRACKED_GRENADES = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new WeakHashMap<>()));

    static List<Entity> trackedGrenadesSnapshot(net.minecraft.world.level.Level level) {
        if (level == null) return java.util.Collections.emptyList();
        synchronized (TRACKED_GRENADES) {
            List<Entity> snapshot = new ArrayList<>(TRACKED_GRENADES.size());
            for (Entity entity : TRACKED_GRENADES) {
                if (entity != null && entity.level() == level && entity.isAlive()) {
                    snapshot.add(entity);
                }
            }
            return snapshot;
        }
    }

    private static Class<?> GRENADE_ENTITY_CLASS;
    private static Method GET_GRENADE_TYPE_METHOD;
    private static Method GET_IS_ACTIVATED_ACCESSOR_METHOD;
    private static boolean FLASHBANG_REFLECTION_INITIALIZED = false;
    private static boolean FLASHBANG_REFLECTION_FAILED = false;

    private static void ensureFlashbangReflection() {
        if (FLASHBANG_REFLECTION_INITIALIZED || FLASHBANG_REFLECTION_FAILED) {
            return;
        }
        try {
            GRENADE_ENTITY_CLASS = Class.forName("club.pisquad.minecraft.csgrenades.entity.CounterStrikeGrenadeEntity");
            GET_GRENADE_TYPE_METHOD = GRENADE_ENTITY_CLASS.getMethod("getGrenadeType");
            GET_IS_ACTIVATED_ACCESSOR_METHOD = GRENADE_ENTITY_CLASS.getMethod("access$isActivatedAccessor$cp");
            FLASHBANG_REFLECTION_INITIALIZED = true;
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] Flashbang reflection initialized successfully");
            }
        } catch (Exception e) {
            FLASHBANG_REFLECTION_FAILED = true;
            SoundAttractMod.LOGGER.warn("[CSGrenades] Failed to initialize flashbang reflection: {} - will not retry", e.getMessage());
        }
    }

    private static boolean isGrenadeActivated(Entity grenade) {
        if (!FLASHBANG_REFLECTION_INITIALIZED) {
            return false;
        }
        try {
            Object accessor = GET_IS_ACTIVATED_ACCESSOR_METHOD.invoke(null);
            return Boolean.TRUE.equals(grenade.getEntityData().get((EntityDataAccessor<Boolean>) accessor));
        } catch (Exception e) {
            return false;
        }
    }

    public static void registerGrenadeEvent(Object eventBus) {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
            SoundAttractMod.LOGGER.debug("[CSGrenades] Event-based registration skipped - using tick-based scanning instead");
        }
    }

    private static void processExplodedFlashbang(Entity grenade, ServerLevel level) {
        if (!SoundAttractConfig.COMMON.enableFlashbangBlinding.get()) {
            return;
        }

        Vec3 flashPos = grenade.position();
        double radius = SoundAttractConfig.COMMON.flashbangBlindRadius.get();
        double radiusSq = radius * radius;
        int baseDuration = SoundAttractConfig.COMMON.flashbangBlindDurationTicks.get();
        boolean requireLos = SoundAttractConfig.COMMON.flashbangRequireLineOfSight.get();
        boolean requireFov = SoundAttractConfig.COMMON.flashbangRequireFov.get();
        int mobBudget = SoundAttractConfig.COMMON.flashbangMobScanBudget.get();
        long currentTick = level.getGameTime();

        int scanned = 0;
        int blinded = 0;
        var mobsInRange = level.getEntitiesOfClass(Mob.class, grenade.getBoundingBox().inflate(radius));
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
            SoundAttractMod.LOGGER.debug("[CSGrenades] Processing flashbang - found {} mobs in radius {}", mobsInRange.size(), radius);
        }
        for (Mob mob : mobsInRange) {
            if (scanned++ >= mobBudget) {
                break;
            }
            Vec3 mobEye = mob.getEyePosition();
            double distSq = mobEye.distanceToSqr(flashPos);
            if (distSq > radiusSq) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            double factor = 1.0 - (dist / radius);
            if (factor <= 0) {
                continue;
            }

            if (requireLos && !OptimizedLOS.hasLineOfSight(level, mobEye, flashPos, mob)) {
                continue;
            }
            if (requireFov && !FovEvents.isTargetInFov(mob, grenade, false)) {
                continue;
            }

            long duration = (long) (baseDuration * factor);
            FlashbangEffect.blind(mob, duration, currentTick);
            blinded++;
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] Blinded mob {} for {} ticks", mob.getUUID(), duration);
            }
        }
        if (blinded > 0) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] Flashbang blinded {} mobs", blinded);
            }
        }
    }

    private static void processTrackedGrenades(ServerLevel level) {
        if (!CsGrenadesCompat.isLoaded() || !SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) {
            return;
        }
        ensureFlashbangReflection();
        if (!FLASHBANG_REFLECTION_INITIALIZED) {
            return;
        }

        Set<Entity> trackedSnapshot;
        synchronized (TRACKED_GRENADES) {
            trackedSnapshot = new HashSet<>(TRACKED_GRENADES);
        }
        for (Entity grenade : trackedSnapshot) {
            if (grenade == null) {
                TRACKED_GRENADES.remove(grenade);
                continue;
            }
            if (isGrenadeActivated(grenade)) {
                UUID id = grenade.getUUID();
                if ("FlashBangEntity".equals(grenade.getClass().getSimpleName())) {
                    if (PROCESSED_FLASHBANGS.asMap().putIfAbsent(id, Boolean.TRUE) == null) {
                        processExplodedFlashbang(grenade, level);
                    }
                    TRACKED_GRENADES.remove(grenade);
                }
            } else if (!grenade.isAlive()) {
                TRACKED_GRENADES.remove(grenade);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) {
            return;
        }

        long currentTick = level.getGameTime();
        int scanInterval = SoundAttractConfig.COMMON.csgrenadesTrackerScanIntervalTicks.get();

        synchronized (LOCK) {
            net.minecraft.resources.ResourceLocation dimension = level.dimension().location();
            long lastScanTick = LAST_SCAN_TICKS.getOrDefault(dimension, -1L);
            if (currentTick - lastScanTick >= scanInterval) {
                int smokeMaxAge = SoundAttractConfig.COMMON.smokeLifetimeMaxTicks.get();
                CsGrenadesTracker.scanAndRebuild(level, currentTick, smokeMaxAge);
                FlashbangEffect.cleanup(currentTick);
                LAST_SCAN_TICKS.put(dimension, currentTick);
            }
        }

        processTrackedGrenades(level);
        cleanupFleeCooldowns(currentTick);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        Entity entity = event.getEntity();

        ensureFlashbangReflection();
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
        SoundAttractMod.LOGGER.debug("[CSGrenades] Entity joined: {} (class: {}, isGrenadeClass={})",
                entity.getUUID(), entity.getClass().getName(),
                GRENADE_ENTITY_CLASS != null && GRENADE_ENTITY_CLASS.isInstance(entity));
        }
        if (GRENADE_ENTITY_CLASS != null && GRENADE_ENTITY_CLASS.isInstance(entity)) {
            TRACKED_GRENADES.add(entity);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] Tracking grenade entity: {} (type={})", entity.getUUID(), entity.getType().getDescriptionId());
            }
            return;
        }

        if (!(entity instanceof PathfinderMob mob)) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableFireFlee.get()) {
            return;
        }
        if (mob.fireImmune()) {
            return;
        }

        String entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        if (!SoundAttractConfig.FLEE_FROM_FIRE_ELIGIBLE_SET.contains(entityId)
                && !isTaczCombatNpc(mob)) {
            return;
        }

        boolean hasGoal = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wg -> wg.getGoal() instanceof FleeFromFireGoal);
        if (!hasGoal) {
            mob.goalSelector.addGoal(1, new FleeFromFireGoal(mob));
        }
    }

    private static boolean isTaczCombatNpc(PathfinderMob mob) {
        String className = mob.getClass().getName();
        return className.startsWith("com.corrinedev.tachostiles.")
                || className.startsWith("com.corrinedev.tacznpcs.");
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        TRACKED_GRENADES.remove(entity);
        if (entity instanceof Mob mob) {
            FLEE_COOLDOWNS.invalidate(mob);
        }

        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
                || !SoundAttractConfig.COMMON.enableFlashbangBlinding.get()) {
            return;
        }
        if (!"FlashBangEntity".equals(entity.getClass().getSimpleName())) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel sLevel)) {
            return;
        }
        ensureFlashbangReflection();
        if (!isGrenadeActivated(entity)) {
            return;
        }
        UUID id = entity.getUUID();
        if (PROCESSED_FLASHBANGS.asMap().putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }
        processExplodedFlashbang(entity, sLevel);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CsGrenadesTracker.clear();
        FlashbangEffect.clear();
        FLEE_COOLDOWNS.invalidateAll();
        PROCESSED_FLASHBANGS.invalidateAll();
        TRACKED_GRENADES.clear();
        LAST_SCAN_TICKS.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event != null && event.getLevel() instanceof ServerLevel level) {
            LAST_SCAN_TICKS.remove(level.dimension().location());
            CsGrenadesTracker.clearLevel(level);
            synchronized (TRACKED_GRENADES) {
                TRACKED_GRENADES.removeIf(entity -> entity == null || entity.level() == level);
            }
        }
    }

    public static boolean isOnFleeCooldown(Mob mob, long currentTick) {
        if (mob == null) {
            return false;
        }
        Long cooldown = FLEE_COOLDOWNS.getIfPresent(mob);
        if (cooldown == null) {
            return false;
        }
        return currentTick < cooldown;
    }

    public static void setFleeCooldown(Mob mob, long currentTick) {
        int cooldownTicks = SoundAttractConfig.COMMON.fireFleeCooldownTicks.get();
        if (mob != null) {
            FLEE_COOLDOWNS.put(mob, currentTick + cooldownTicks);
        }
    }

    private static void cleanupFleeCooldowns(long currentTick) {
        FLEE_COOLDOWNS.asMap().entrySet().removeIf(entry ->
                entry.getKey() == null || entry.getValue() == null || currentTick >= entry.getValue());
    }
}
