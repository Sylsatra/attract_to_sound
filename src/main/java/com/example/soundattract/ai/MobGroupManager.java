package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.worker.WorkerScheduler.ConfigSnapshot;
import com.example.soundattract.worker.WorkerScheduler.GroupComputeResult;
import com.example.soundattract.worker.WorkerScheduler.MobSnapshot;
import com.example.soundattract.worker.WorkSchedulerManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

@net.neoforged.fml.common.EventBusSubscriber(modid = com.example.soundattract.SoundAttractMod.MOD_ID)
public class MobGroupManager {

  private static final Map<Identifier, PerWorldData> worldData = new ConcurrentHashMap<>();
  private static final int RELAY_SOUND_TTL = 40;
  private static final int RELAY_SOUND_RATE_LIMIT = 20;

  private static class PerWorldData {

    final Map<UUID, Mob> uuidToLeader = Collections.synchronizedMap(
      new HashMap<>()
    );
    final List<WeakReference<Mob>> leaders = Collections.synchronizedList(
      new ArrayList<>()
    );
    final Map<Mob, List<SoundRelay>> mobToRelayedSounds = Collections.synchronizedMap(
      new WeakHashMap<>()
    );
    final Map<Mob, Long> mobLastRelayTime = Collections.synchronizedMap(
      new WeakHashMap<>()
    );
    final Set<UUID> deserterUuids = Collections.synchronizedSet(
      new HashSet<>()
    );
    final Map<Mob, Set<Mob>> lastEdgeMobMap = Collections.synchronizedMap(
      new HashMap<>()
    );
    long lastGroupUpdateTime = -1;
    long lastCleanupTime = -1;

    volatile Future<?> inFlightGroupCompute;
  }

  private static PerWorldData getData(Identifier dimension) {
    return worldData.computeIfAbsent(dimension, k -> new PerWorldData());
  }

  public static class SoundRelay {

    public final String soundId;
    public final double x, y, z, range, weight;
    public final long timestamp;
    public final int hash;

    public SoundRelay(
      String soundId,
      double x,
      double y,
      double z,
      double range,
      double weight,
      long timestamp
    ) {
      this.soundId = soundId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.range = range;
      this.weight = weight;
      this.timestamp = timestamp;
      this.hash = Objects.hash(
        soundId,
        (int) x,
        (int) y,
        (int) z,
        (int) range,
        (int) (weight * 100)
      );
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SoundRelay other)) return false;
      return (
        this.hash == other.hash &&
        Objects.equals(this.soundId, other.soundId) &&
        Math.abs(this.x - other.x) < 0.1 &&
        Math.abs(this.y - other.y) < 0.1 &&
        Math.abs(this.z - other.z) < 0.1 &&
        Math.abs(this.range - other.range) < 0.1 &&
        Math.abs(this.weight - other.weight) < 0.01 &&
        Math.abs(this.timestamp - other.timestamp) < RELAY_SOUND_TTL
      );
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  private static boolean submitGroupComputeSnapshot(ServerLevel level) {
    try {
      PerWorldData data = getData(level.dimension().identifier());
      Future<?> inFlight = data.inFlightGroupCompute;
      if (inFlight != null) {
        try {
          if (!inFlight.isDone()) {
            return true;
          }
        } catch (Throwable ignored) {
        }
        data.inFlightGroupCompute = null;
      }

      Set<net.minecraft.world.entity.EntityType<?>> attractedEntityTypes = com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes();
      if (attractedEntityTypes == null || attractedEntityTypes.isEmpty()) return false;
      int simDistChunks = level.getServer().getPlayerList().getViewDistance();
      int simDistBlocks = simDistChunks * 16;
      Set<Mob> mobsSet = new HashSet<>();
      for (net.minecraft.server.level.ServerPlayer player : level.players()) {
        AABB box = player.getBoundingBox().inflate(simDistBlocks);
        List<Mob> nearbyMobs = level.getEntitiesOfClass(
          Mob.class,
          box,
          m ->
            m.isAlive() &&
            !m.isRemoved() &&
            (
              attractedEntityTypes.contains(m.getType()) ||
              com.example.soundattract.config.SoundAttractConfig.getMatchingProfile(m) != null ||
              com.example.soundattract.event.SoundAttractionEvents.isCustomNpcsMob(m)
            )
        );
        mobsSet.addAll(nearbyMobs);
      }
      if (mobsSet.isEmpty()) return false;
      List<MobSnapshot> snapshots = new ArrayList<>(mobsSet.size());
      for (Mob m : mobsSet) {
        snapshots.add(
          new MobSnapshot(
            m.getUUID(),
            m.getX(),
            m.getY(),
            m.getZ(),
            m.getHealth(),
            m.isAlive()
          )
        );
      }
      ConfigSnapshot cfg = new ConfigSnapshot(
        SoundAttractConfig.COMMON.leaderGroupRadius.get(),
        SoundAttractConfig.COMMON.maxLeaders.get(),
        SoundAttractConfig.COMMON.maxGroupSize.get(),
        SoundAttractConfig.COMMON.leaderSpacingMultiplier.get(),
        SoundAttractConfig.COMMON.numEdgeSectors.get(),
        SoundAttractConfig.COMMON.edgeMobsPerSector.get()
      );
      data.inFlightGroupCompute = WorkSchedulerManager.get().submitGroupCompute(snapshots, cfg, level.dimension().identifier());
      if (SoundAttractConfig.COMMON.debugLogging.get()) {
        SoundAttractMod.LOGGER.info(
          "[MobGroupManager] Submitted async group compute for {} mobs in dimension {}",
          snapshots.size(),
          level.dimension().identifier()
        );
      }
      return true;
    } catch (Throwable t) {
      if (SoundAttractConfig.COMMON.debugLogging.get()) {
        SoundAttractMod.LOGGER.error(
          "[MobGroupManager] submitGroupComputeSnapshot failed for dimension " +
          level.dimension().identifier(),
          t
        );
      }
      return false;
    }
  }

  public static void applyGroupResult(
    ServerLevel level,
    GroupComputeResult result
  ) {
    if (result == null) return;
    PerWorldData data = getData(level.dimension().identifier());
    Map<UUID, Mob> uuidToMob = new HashMap<>();
    int simDistBlocks = level.getServer().getPlayerList().getViewDistance() * 16;
    for (net.minecraft.server.level.ServerPlayer player : level.players()) {
      AABB area = player.getBoundingBox().inflate(simDistBlocks);
      for (Mob m : level.getEntitiesOfClass(Mob.class, area)) {
        uuidToMob.put(m.getUUID(), m);
      }
    }
    data.uuidToLeader.clear();
    data.leaders.clear();
    data.deserterUuids.clear();
    data.lastEdgeMobMap.clear();
    Set<UUID> leaderUuids = new HashSet<>();
    for (Map.Entry<UUID, UUID> e : result.mobUuidToLeaderUuid().entrySet()) {
      UUID mobId = e.getKey();
      UUID leaderId = e.getValue();
      Mob mob = uuidToMob.get(mobId);
      Mob leader = uuidToMob.get(leaderId);
      if (mob == null || leader == null) continue;
      data.uuidToLeader.put(mob.getUUID(), leader);
      if (leaderId.equals(mobId)) {
        leaderUuids.add(leaderId);
      }
    }
    for (UUID lid : leaderUuids) {
      Mob leader = uuidToMob.get(lid);
      if (leader != null) {
        data.leaders.add(new WeakReference<>(leader));
      }
    }
    for (Map.Entry<UUID, java.util.Set<UUID>> e : result.edgeMobsByLeaderUuid().entrySet()) {
      UUID lid = e.getKey();
      Mob leader = uuidToMob.get(lid);
      if (leader == null) continue;
      Set<Mob> edges = new HashSet<>();
      for (UUID mid : e.getValue()) {
        Mob mm = uuidToMob.get(mid);
        if (mm != null) edges.add(mm);
      }
      data.lastEdgeMobMap.put(leader, edges);
    }
    data.deserterUuids.addAll(result.deserterUuids());
    if (SoundAttractConfig.COMMON.debugLogging.get()) {
      SoundAttractMod.LOGGER.info(
        "[MobGroupManager] Applied group result for dimension {}: leaders={}, deserters={}",
        level.dimension().identifier(),
        leaderUuids.size(),
        data.deserterUuids.size()
      );
    }
  }

  public static boolean isEdgeMob(Mob mob) {
    PerWorldData data = getData(mob.level().dimension().identifier());
    Mob leader = getLeader(mob);
    if (leader == mob) {
      return false;
    }
    Set<Mob> edgeSet = data.lastEdgeMobMap.get(leader);
    return edgeSet != null && edgeSet.contains(mob);
  }

  public static List<Mob> getFollowers(Mob leader) {
      if (leader == null || leader.level() == null) return Collections.emptyList();
      PerWorldData data = getData(leader.level().dimension().identifier());
      List<Mob> followers = new ArrayList<>();
      
      synchronized (data.uuidToLeader) {
          for (Map.Entry<UUID, Mob> entry : data.uuidToLeader.entrySet()) {
              if (entry.getValue() == leader) {
                  UUID followerId = entry.getKey();
                  if (leader.level() instanceof ServerLevel sl) {
                      net.minecraft.world.entity.Entity e = sl.getEntity(followerId);
                      if (e instanceof Mob m && m != leader && m.isAlive() && !m.isRemoved()) {
                          followers.add(m);
                      }
                  }
              }
          }
      }
      return followers;
  }

  private static void cleanupStaleEntries(ServerLevel level) {
    PerWorldData data = getData(level.dimension().identifier());
    data.leaders.removeIf(ref -> {
      Mob mob = ref.get();
      return mob == null || mob.isRemoved();
    });
    data.uuidToLeader.values().removeIf(mob -> mob == null || mob.isRemoved());
    data.mobToRelayedSounds.keySet().removeIf(mob -> mob == null || mob.isRemoved());
    data.mobLastRelayTime.keySet().removeIf(mob -> mob == null || mob.isRemoved());
    
    data.lastEdgeMobMap.keySet().removeIf(mob -> mob == null || mob.isRemoved());
    for (Set<Mob> edges : data.lastEdgeMobMap.values()) {
        if (edges != null) {
            edges.removeIf(mob -> mob == null || mob.isRemoved());
        }
    }
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel serverLevel) {
          Identifier dimension = serverLevel.dimension().identifier();
          worldData.remove(dimension);
          if (SoundAttractConfig.COMMON.debugLogging.get()) {
              SoundAttractMod.LOGGER.info("[MobGroupManager] Cleared cluster memory for unloaded dimension: {}", dimension);
          }
      }
  }

  public static void updateGroups(ServerLevel level) {
    PerWorldData data = getData(level.dimension().identifier());
    long time = level.getGameTime();
    if (time - data.lastGroupUpdateTime < SoundAttractConfig.COMMON.groupUpdateInterval.get()) return;
    data.lastGroupUpdateTime = time;
    if (
      SoundAttractConfig.COMMON.debugLogging.get() &&
      (time % 100 == 0)
    ) {
      SoundAttractMod.LOGGER.info(
        "[MobGroupManager] Updating groups for dimension: {}",
        level.dimension().identifier()
      );
    }
    int scanCooldown = com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks;
    if (
      scanCooldown > 0 &&
      (
        data.lastCleanupTime == -1 ||
        time - data.lastCleanupTime > 10L * scanCooldown
      )
    ) {
      cleanupStaleEntries(level);
      data.lastCleanupTime = time;
    }
    if (submitGroupComputeSnapshot(level)) {
      return;
    }
    Set<net.minecraft.world.entity.EntityType<?>> attractedEntityTypes = com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes();
    int simDistChunks = level.getServer().getPlayerList().getViewDistance();
    int simDistBlocks = simDistChunks * 16;
    Set<Mob> mobsSet = new HashSet<>();
    for (net.minecraft.server.level.ServerPlayer player : level.players()) {
      net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(simDistBlocks);
      List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, box);
      mobsSet.addAll(nearbyMobs);
    }
    List<Mob> attractedMobs = new ArrayList<>();
    for (Mob mob : mobsSet) {
      boolean byType = attractedEntityTypes.contains(mob.getType());
      boolean hasProfile = SoundAttractConfig.getMatchingProfile(mob) != null;
      boolean isCustomNpcs = com.example.soundattract.event.SoundAttractionEvents.isCustomNpcsMob(mob);
      if (byType || hasProfile || isCustomNpcs) {
        attractedMobs.add(mob);
      }
    }
    data.uuidToLeader.clear();
    data.leaders.clear();
    data.lastEdgeMobMap.clear();
    data.deserterUuids.clear();
    if (attractedMobs.isEmpty()) return;
    for (Mob m : attractedMobs) {
      if (SoundAttractConfig.getMatchingProfile(m) != null) {
        data.leaders.add(new WeakReference<>(m));
        data.uuidToLeader.put(m.getUUID(), m);
      }
    }
    Collections.shuffle(attractedMobs, new java.util.Random(level.getGameTime()));
    double groupRadius = SoundAttractConfig.COMMON.leaderGroupRadius.get();
    int maxLeaders = SoundAttractConfig.COMMON.maxLeaders.get();
    int maxGroupSize = SoundAttractConfig.COMMON.maxGroupSize.get();
    double leaderSpacing = groupRadius * SoundAttractConfig.COMMON.leaderSpacingMultiplier.get();
    Set<Mob> assigned = new HashSet<>();
    List<Mob> leaderList = new ArrayList<>();
    synchronized (data.leaders) {
      data.leaders.removeIf(ref -> ref.get() == null || ref.get().isRemoved());
      attractedMobs.sort(Comparator.comparingDouble(m -> -m.getHealth()));
      for (Mob potentialLeader : attractedMobs) {
        if (data.leaders.size() >= maxLeaders) break;
        if (data.leaders.stream().anyMatch(ref -> ref.get() == potentialLeader)) continue;
        boolean tooCloseToExistingLeader = false;
        for (WeakReference<Mob> leaderRef : data.leaders) {
          Mob existingLeader = leaderRef.get();
          if (
            existingLeader != null &&
            potentialLeader.distanceToSqr(existingLeader) < leaderSpacing * leaderSpacing
          ) {
            tooCloseToExistingLeader = true;
            break;
          }
        }
        if (!tooCloseToExistingLeader) {
          data.leaders.add(new WeakReference<>(potentialLeader));
          data.uuidToLeader.put(potentialLeader.getUUID(), potentialLeader);
          leaderList.add(potentialLeader);
        }
      }
    }
    Map<Mob, List<Mob>> leaderToGroup = new HashMap<>();
    for (Mob leader : leaderList) {
      List<Mob> group = new ArrayList<>();
      group.add(leader);
      for (Mob mob : attractedMobs) {
        if (mob == leader || assigned.contains(mob)) continue;
        if (mob.distanceTo(leader) <= groupRadius && group.size() < maxGroupSize) {
          group.add(mob);
          assigned.add(mob);
        }
      }
      for (Mob mob : group) {
        data.uuidToLeader.put(mob.getUUID(), leader);
      }
      leaderToGroup.put(leader, group);
    }
    for (Mob mob : attractedMobs) {
      if (!assigned.contains(mob)) {
        data.deserterUuids.add(mob.getUUID());
      }
    }
    for (Mob leader : leaderList) {
      List<Mob> group = leaderToGroup.get(leader);
      if (group == null) continue;
      int sectors = SoundAttractConfig.COMMON.numEdgeSectors.get();
      Map<Integer, List<Mob>> sectorToFarthestList = new HashMap<>();
      double leaderX = leader.getX(), leaderZ = leader.getZ();
      for (Mob m : group) {
        if (m == leader) continue;
        double dx = m.getX() - leaderX, dz = m.getZ() - leaderZ;
        double angle = Math.atan2(dz, dx);
        int sector = (int) Math.floor(
          ((angle + Math.PI) / (2 * Math.PI)) * sectors
        ) % sectors;
        sectorToFarthestList.computeIfAbsent(sector, k -> new ArrayList<>()).add(m);
      }
      Set<Mob> edgeMobs = new HashSet<>();
      for (Map.Entry<Integer, List<Mob>> entry : sectorToFarthestList.entrySet()) {
        List<Mob> mobsInSector = entry.getValue();
        mobsInSector.sort(
          (a, b) -> Double.compare(b.distanceTo(leader), a.distanceTo(leader))
        );
        int perSector = SoundAttractConfig.COMMON.edgeMobsPerSector.get();
        int edgeCount = Math.min(perSector, mobsInSector.size());
        for (int i = 0; i < edgeCount; i++) {
          edgeMobs.add(mobsInSector.get(i));
        }
      }
      data.lastEdgeMobMap.put(leader, edgeMobs);
    }
  }

  public static void relaySoundToLeader(
    Mob mob,
    String soundId,
    double x,
    double y,
    double z,
    double range,
    double weight,
    long timestamp
  ) {
    PerWorldData data = getData(mob.level().dimension().identifier());
    Mob leader = getLeader(mob);
    if (leader == mob) return;
    Long lastRelay = data.mobLastRelayTime.get(mob);
    if (lastRelay != null && timestamp - lastRelay < RELAY_SOUND_RATE_LIMIT) return;
    data.mobLastRelayTime.put(mob, timestamp);
    SoundRelay relay = new SoundRelay(soundId, x, y, z, range, weight, timestamp);
    List<SoundRelay> relays = data.mobToRelayedSounds.computeIfAbsent(
      leader,
      k -> new ArrayList<>()
    );
    if (!relays.contains(relay)) {
      relays.add(relay);
    }
  }

  public static List<SoundRelay> consumeRelayedSounds(Mob leader) {
    PerWorldData data = getData(leader.level().dimension().identifier());
    List<SoundRelay> relays = data.mobToRelayedSounds.remove(leader);
    if (relays == null) return Collections.emptyList();
    long now = leader.level().getGameTime();
    relays.removeIf(r -> now - r.timestamp > RELAY_SOUND_TTL);
    return relays;
  }

  public static Mob getLeader(Mob mob) {
    PerWorldData data = getData(mob.level().dimension().identifier());
    return data.uuidToLeader.getOrDefault(mob.getUUID(), mob);
  }

  /**
   * Returns the nearest known leader in the mob's dimension, or null if none exist.
   * Useful for deserters whose leader mapping resolves to themselves.
   */
  public static Mob getNearestLeader(Mob mob) {
    PerWorldData data = getData(mob.level().dimension().identifier());
    Mob best = null;
    double bestDistSq = Double.MAX_VALUE;
    synchronized (data.leaders) {
      data.leaders.removeIf(ref -> ref.get() == null || ref.get().isRemoved());
      for (WeakReference<Mob> ref : data.leaders) {
        Mob candidate = ref.get();
        if (candidate == null || candidate == mob || candidate.isRemoved() || !candidate.isAlive()) continue;
        double d = mob.distanceToSqr(candidate);
        if (d < bestDistSq) {
          bestDistSq = d;
          best = candidate;
        }
      }
    }
    return best;
  }

  public static void promoteToDeserter(Mob mob) {
    PerWorldData data = getData(mob.level().dimension().identifier());
    data.deserterUuids.add(mob.getUUID());
  }

  public static boolean isDeserter(Mob mob) {
    PerWorldData data = getData(mob.level().dimension().identifier());
    return data.deserterUuids.contains(mob.getUUID());
  }

  public static int getFollowerCount(Mob leader) {
    if (leader == null || leader.level() == null) return 0;
    PerWorldData data = getData(leader.level().dimension().identifier());
    int count = 0;
    synchronized (data.uuidToLeader) {
      for (Mob l : data.uuidToLeader.values()) {
        if (l == leader) count++;
      }
    }
    return count;
  }

  /**
   * Returns true if the mob or its current leader is actively investigating a
   * high-weight sound. Used to suppress target acquisition.
   */
  public static boolean isMobInHighWeightOverride(Mob mob) {
    if (mob == null || !mob.isAlive()) return false;

    boolean ownOverride = mob.goalSelector.getAvailableGoals().stream()
            .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
            .anyMatch(g -> (g instanceof AttractionGoal ag && ag.isHighWeightOverrideActive()) ||
                           (g instanceof LeaderAttractionGoal lag && lag.isHighWeightOverrideActive()) ||
                           (g instanceof FollowerEdgeRelayGoal feg && feg.isHighWeightOverrideActive()));
    if (ownOverride) return true;

    Mob leader = getLeader(mob);
    if (leader != null && leader != mob) {
      return leader.goalSelector.getAvailableGoals().stream()
              .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
              .anyMatch(g -> (g instanceof AttractionGoal ag && ag.isHighWeightOverrideActive()) ||
                             (g instanceof LeaderAttractionGoal lag && lag.isHighWeightOverrideActive()));
    }

    return false;
  }
}
