package com.example.soundattract.raid;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RaidReinforcementManager {
    private RaidReinforcementManager() {}

    private static final Map<Mob, RaidSpawnState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile Map<net.minecraft.resources.Identifier, Integer> cachedCaps = null;
    private static volatile long cachedCapsTick = -1;

    private static final class RaidSpawnState {
        long lastSpawnTick;
        final Map<EntityType<?>, Integer> spawnedCounts = new HashMap<>();
        int totalSpawned;
    }

    public static void trySpawnReinforcement(ServerLevel level, Mob leader, BlockPos raidTarget) {
        try {
            if (!SoundAttractConfig.COMMON.enableRaidReinforcements.get()) return;
        } catch (Throwable ignored) { return; }

        long now = level.getGameTime();
        RaidSpawnState state = STATES.computeIfAbsent(leader, k -> new RaidSpawnState());

        int interval = 40;
        try { interval = Math.max(1, SoundAttractConfig.COMMON.raidReinforcementInterval.get()); } catch (Throwable ignored) {}
        if (now - state.lastSpawnTick < interval) return;
        state.lastSpawnTick = now;

        int maxTotal = 80;
        try { maxTotal = Math.max(0, SoundAttractConfig.COMMON.raidMaxTotalMobs.get()); } catch (Throwable ignored) {}
        if (state.totalSpawned >= maxTotal) return;

        double diffMult = 1.0;
        try { diffMult = Math.max(0.0, SoundAttractConfig.COMMON.raidReinforcementDifficultyMultiplier.get()); } catch (Throwable ignored) {}

        Map<Identifier, Integer> caps = getCachedCaps(level.getGameTime());
        if (caps.isEmpty()) return;

        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class,
                leader.getBoundingBox().inflate(32.0),
                m -> m.isAlive() && !m.isRemoved());

        if (nearbyMobs.isEmpty()) return;

        Mob template = nearbyMobs.get(level.getRandom().nextInt(nearbyMobs.size()));
        EntityType<?> type = template.getType();
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);

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
            Mob spawned = (Mob) mobType.create(level, null, spawnPos, net.minecraft.world.entity.EntitySpawnReason.EVENT, false, false);
            if (spawned == null) return;

            spawned.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.EntitySpawnReason.EVENT, null);
            spawned.setPersistenceRequired();
            level.addFreshEntity(spawned);
            
            com.example.soundattract.ai.RaidManager.registerPersistentRaidMob(leader, spawned);

            state.spawnedCounts.merge(type, 1, Integer::sum);
            state.totalSpawned++;

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[RaidReinforcementManager] Spawned {} at {} for leader {} (total: {}/{})",
                        typeId, spawnPos, leader.getName().getString(), state.totalSpawned, maxTotal);
            }
        } catch (Throwable e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[RaidReinforcementManager] Failed to spawn reinforcement: {}", e.getMessage());
            }
        }
    }

    public static void clearState(Mob leader) {
        STATES.remove(leader);
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

    private static Map<Identifier, Integer> getCachedCaps(long gameTick) {
        if (cachedCaps != null && Math.abs(gameTick - cachedCapsTick) < 200) {
            return cachedCaps;
        }
        Map<Identifier, Integer> parsed = parseCaps();
        cachedCaps = parsed;
        cachedCapsTick = gameTick;
        return parsed;
    }

    private static Map<Identifier, Integer> parseCaps() {
        Map<Identifier, Integer> result = new HashMap<>();
        try {
            List<? extends String> entries = SoundAttractConfig.COMMON.raidReinforcementCaps.get();
            for (String entry : entries) {
                String[] parts = entry.split(",");
                if (parts.length == 2) {
                    Identifier id = Identifier.tryParse(parts[0].trim());
                    int cap = Integer.parseInt(parts[1].trim());
                    if (cap > 0) result.put(id, cap);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }
}
