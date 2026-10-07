package com.example.soundattract.raid;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RaidReinforcementManager {
    private RaidReinforcementManager() {}

    private static final int MAX_RUNTIME_REINFORCEMENTS = 65536;

    private static final Cache<Mob, RaidSpawnState> STATES = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private static volatile Map<net.minecraft.resources.ResourceLocation, Integer> cachedCaps = null;
    private static volatile long cachedCapsTick = -1;

    private static final class RaidSpawnState {
        long lastSpawnTick;
        final Map<EntityType<?>, Integer> spawnedCounts = new HashMap<>();
        int totalSpawned;
    }

    public static void trySpawnReinforcement(ServerLevel level, Mob leader, BlockPos raidTarget) {
        if (level == null || leader == null || raidTarget == null || leader.isRemoved() || !leader.isAlive()) return;
        try {
            if (!SoundAttractConfig.COMMON.enableRaidReinforcements.get()) return;
        } catch (Throwable ignored) { return; }

        long now = level.getGameTime();
        RaidSpawnState state = STATES.get(leader, ignored -> new RaidSpawnState());

        int interval = 40;
        try {
            interval = Math.max(20, SoundAttractConfig.COMMON.raidReinforcementInterval.get());
        } catch (Throwable ignored) {
            interval = 20;
        }
        if (now - state.lastSpawnTick < interval) return;
        state.lastSpawnTick = now;

        int maxTotal = 80;
        try {
            maxTotal = Math.max(0, Math.min(MAX_RUNTIME_REINFORCEMENTS,
                    SoundAttractConfig.COMMON.raidMaxTotalMobs.get()));
        } catch (Throwable ignored) {}
        if (state.totalSpawned >= maxTotal) return;

        double diffMult = 1.0;
        try { diffMult = Math.max(0.0, SoundAttractConfig.COMMON.raidReinforcementDifficultyMultiplier.get()); } catch (Throwable ignored) {}

        Map<ResourceLocation, Integer> caps = getCachedCaps(level.getGameTime());
        if (caps.isEmpty()) return;

        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class,
                leader.getBoundingBox().inflate(32.0),
                m -> m.isAlive() && !m.isRemoved());

        if (nearbyMobs.isEmpty()) return;

        Mob template = nearbyMobs.get(level.getRandom().nextInt(nearbyMobs.size()));
        EntityType<?> type = template.getType();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);

        Integer capForType = caps.get(typeId);
        if (capForType == null) return;

        int adjustedCap = (int) Math.ceil(capForType * diffMult);
        int currentCount = state.spawnedCounts.getOrDefault(type, 0);
        if (currentCount >= adjustedCap) return;

        BlockPos spawnPos = findSpawnPos(level, leader.blockPosition());
        if (spawnPos == null) return;

        try {
            @SuppressWarnings("unchecked")
            EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) type;
            Mob spawned = mobType.create(level);
            if (spawned == null) return;

            spawned.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
            spawned.setPersistenceRequired();
            level.addFreshEntity(spawned);

            com.example.soundattract.ai.RaidManager.registerPersistentRaidMob(leader, spawned);

            state.spawnedCounts.merge(type, 1, Integer::sum);
            state.totalSpawned++;

            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.RAIDS)) {
                SoundAttractMod.LOGGER.info("[RaidReinforcementManager] Spawned {} at {} for leader {} (total: {}/{})",
                        typeId, spawnPos, leader.getName().getString(), state.totalSpawned, maxTotal);
            }
        } catch (Throwable e) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.RAIDS)) {
                SoundAttractMod.LOGGER.warn("[RaidReinforcementManager] Failed to spawn reinforcement: {}", e.getMessage());
            }
        }
    }

    public static void clearState(Mob leader) {
        if (leader != null) STATES.invalidate(leader);
    }


    public static void clearAll() {
        STATES.invalidateAll();
        cachedCaps = null;
        cachedCapsTick = -1L;
    }


    public static void clearLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        STATES.asMap().entrySet().removeIf(entry -> {
                Mob leader = entry.getKey();
                return leader == null || leader.level() == level;
        });
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos near) {
        var rand = level.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = rand.nextInt(11) - 5;
            int dz = rand.nextInt(11) - 5;
            BlockPos candidate = near.offset(dx, 0, dz);

            int groundY = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    candidate.getX(), candidate.getZ());
            BlockPos ground = new BlockPos(candidate.getX(), groundY, candidate.getZ());

            if (!level.getBlockState(ground.below()).isAir()
                    && level.getBlockState(ground).isAir()
                    && level.getBlockState(ground.above()).isAir()) {
                return ground;
            }
        }
        return null;
    }

    private static Map<ResourceLocation, Integer> getCachedCaps(long gameTick) {
        if (cachedCaps != null && Math.abs(gameTick - cachedCapsTick) < 200) {
            return cachedCaps;
        }
        Map<ResourceLocation, Integer> parsed = parseCaps();
        cachedCaps = parsed;
        cachedCapsTick = gameTick;
        return parsed;
    }

    private static Map<ResourceLocation, Integer> parseCaps() {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        try {
            List<? extends String> entries = SoundAttractConfig.COMMON.raidReinforcementCaps.get();
            for (String entry : entries) {
                String[] parts = entry.split(",");
                if (parts.length == 2) {
                    ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
                    int cap = Integer.parseInt(parts[1].trim());
                    if (cap > 0) result.put(id, cap);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }
}