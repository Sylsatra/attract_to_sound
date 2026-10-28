package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized manager for RAID orchestration per dimension.
 * A RAID is started by an edge mob detecting a player and returning to its leader.
 * - Ticking: countdown until the raid begins (followers rally to leader).
 * - Advancing: leader and followers move toward the recorded target position.
 */
public class RaidManager {


    private static final int DEFAULT_RAID_COUNTDOWN_TICKS = 200;

    private static class PerWorldData {
        final Map<Mob, Raid> leaderToRaid = Collections.synchronizedMap(new WeakHashMap<>());
        long lastCleanupTime = -1L;
    }

    private static final Map<ResourceLocation, PerWorldData> DATA = new ConcurrentHashMap<>();

    private static PerWorldData getData(ServerLevel level) {
        return DATA.computeIfAbsent(level.dimension().location(), k -> new PerWorldData());
    }

    public static void scheduleRaid(Mob leader, BlockPos targetPos, long now) {
        if (!(leader.level() instanceof ServerLevel level)) return;
        PerWorldData d = getData(level);
        Raid raid = new Raid(leader, targetPos, now);
        
        leader.setPersistenceRequired(); 
        raid.persistentParticipants.add(leader.getUUID());
        
        List<Mob> followers = MobGroupManager.getFollowers(leader);
        if (followers != null) {
            for (Mob follower : followers) {
                if (follower != null && follower.isAlive() && !follower.isRemoved()) {
                    follower.setPersistenceRequired(); 
                    raid.persistentParticipants.add(follower.getUUID());
                }
            }
        }

        d.leaderToRaid.put(leader, raid);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            int countdown = SoundAttractConfig.COMMON.raidCountdownTicks.get();
            SoundAttractMod.LOGGER.info("[RaidManager] Scheduled RAID for leader {} at {} (countdown {} ticks). Made {} participants persistent.",
                    leader.getName().getString(), targetPos, countdown, raid.persistentParticipants.size());
        }
    }

    public static boolean isRaidTicking(Mob leader) {
        Raid r = getRaid(leader);
        return r != null && !r.advancing && r.ticksRemaining > 0;
    }

    public static boolean isRaidAdvancing(Mob leader) {
        Raid r = getRaid(leader);
        return r != null && r.advancing;
    }

    public static BlockPos getRaidTarget(Mob leader) {
        Raid r = getRaid(leader);
        return r != null ? r.target : null;
    }

    public static int getTicksRemaining(Mob leader) {
        Raid r = getRaid(leader);
        return r != null ? r.ticksRemaining : -1;
    }

    public static void clearRaid(Mob leader) {
        if (!(leader.level() instanceof ServerLevel level)) return;
        PerWorldData d = getData(level);
        Raid raid = d.leaderToRaid.remove(leader);
        if (raid != null) {
            clearPersistence(level, raid);
        }
        LivingWallCoordinator.clearRaid(leader);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[RaidManager] Cleared RAID for leader {}", leader.getName().getString());
        }
    }

    private static void clearPersistence(ServerLevel level, Raid raid) {
        if (raid == null || raid.persistentParticipants.isEmpty()) return;
        for (UUID uuid : raid.persistentParticipants) {
            net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
            if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
                ((com.example.soundattract.mixin.MobAccessor) mob).setPersistenceRequiredFlag(false);
            }
        }
        raid.persistentParticipants.clear();
    }

    public static void updateRaidTarget(Mob leader, BlockPos newTarget) {
        Raid r = getRaid(leader);
        if (r == null || !r.advancing) return;
        r.target = newTarget;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[RaidManager] RAID target updated for leader {} to {}",
                    leader.getName().getString(), newTarget);
        }
    }

    private static Raid getRaid(Mob leader) {
        if (!(leader.level() instanceof ServerLevel level)) return null;
        PerWorldData d = getData(level);
        Raid r = d.leaderToRaid.get(leader);
        if (r == null) return null;
        if (!r.isValid()) {
            if (d.leaderToRaid.remove(leader, r)) {
                clearPersistence(level, r);
                com.example.soundattract.raid.RaidReinforcementManager.clearState(leader);
                LivingWallCoordinator.clearRaid(leader);
            }
            return null;
        }
        return r;
    }

    public static void registerPersistentRaidMob(Mob leader, Mob spawned) {
        Raid r = getRaid(leader);
        if (r != null) {
            r.persistentParticipants.add(spawned.getUUID());
        }
    }

    public static void tick(ServerLevel level) {
        PerWorldData d = getData(level);
        long now = level.getGameTime();

        if (d.lastCleanupTime == -1L || now - d.lastCleanupTime > 200) {
            synchronized (d.leaderToRaid) {
                d.leaderToRaid.entrySet().removeIf(e -> {
                    Mob leader = e.getKey();
                    Raid raid = e.getValue();
                    if (leader == null || leader.isRemoved() || !leader.isAlive() || raid == null || !raid.isValid()) {
                        if (raid != null) {
                            clearPersistence(level, raid);
                        }
                        com.example.soundattract.raid.RaidReinforcementManager.clearState(leader);
                        LivingWallCoordinator.clearRaid(leader);
                        return true;
                    }
                    return false;
                });
            }
            d.lastCleanupTime = now;
        }
        synchronized (d.leaderToRaid) {
            for (Iterator<Map.Entry<Mob, Raid>> it = d.leaderToRaid.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Mob, Raid> entry = it.next();
                Mob leader = entry.getKey();
                Raid raid = entry.getValue();
                if (leader == null || leader.isRemoved() || !leader.isAlive() || raid == null) {
                    if (raid != null) {
                        clearPersistence(level, raid);
                    }
                    com.example.soundattract.raid.RaidReinforcementManager.clearState(leader);
                    LivingWallCoordinator.clearRaid(leader);
                    it.remove();
                    continue;
                }
                if (!raid.advancing) {
                    if (raid.ticksRemaining > 0) {
                        raid.ticksRemaining--;
                        if (raid.ticksRemaining % 20 == 0 && SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[RaidManager] RAID ticking for leader {}: {} ticks remaining",
                                    leader.getName().getString(), raid.ticksRemaining);
                        }
                        com.example.soundattract.raid.RaidReinforcementManager.trySpawnReinforcement(level, leader, raid.target);
                    }
                    if (raid.ticksRemaining <= 0) {
                        raid.ticksRemaining = 0;
                        raid.advancing = true;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[RaidManager] RAID READY: leader {} advancing to {}",
                                    leader.getName().getString(), raid.target);
                        }
                    }
                }
            }
        }
        LivingWallCoordinator.tick(level);
        synchronized (d.leaderToRaid) {
            for (Iterator<Map.Entry<Mob, Raid>> it = d.leaderToRaid.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Mob, Raid> entry = it.next();
                Mob leader = entry.getKey();
                Raid raid = entry.getValue();
                if (leader != null && raid != null && raid.advancing) {
                    LivingWallCoordinator.prepareRaid(leader, raid.target, level.getGameTime());
                }
            }
        }
    }

    private static class Raid {
        final WeakReference<Mob> leaderRef;
        volatile BlockPos target;
        int ticksRemaining;
        boolean advancing;
        final Set<UUID> persistentParticipants = new HashSet<>();

        Raid(Mob leader, BlockPos target, long now) {
            this.leaderRef = new WeakReference<>(leader);
            this.target = target;

            int configured = SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.raidCountdownTicks != null
                    ? SoundAttractConfig.COMMON.raidCountdownTicks.get()
                    : DEFAULT_RAID_COUNTDOWN_TICKS;
            this.ticksRemaining = configured;
            this.advancing = false;
        }

        boolean isValid() {
            Mob leader = leaderRef.get();
            return leader != null && leader.isAlive() && !leader.isRemoved();
        }
    }

    public static void onScentEmittedByMobProjectile(ServerLevel level, UUID shooterUuid, BlockPos scentPos, Set<UUID> notifiedMobIds) {
    }
}
