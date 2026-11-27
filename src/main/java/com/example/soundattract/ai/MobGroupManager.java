package com.example.soundattract.ai;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.spatial.SpatialLifecycleEvents;
import com.example.soundattract.spatial.SpatialContracts.IndexedEntitySnapshot;
import com.example.soundattract.worker.WorkerScheduler.ConfigSnapshot;
import com.example.soundattract.worker.WorkerScheduler.GroupComputeResult;
import com.example.soundattract.worker.WorkerScheduler.MobSnapshot;
import com.example.soundattract.worker.WorkSchedulerManager;
import com.example.soundattract.worker.WorkerComputations;
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
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.AABB;

@net.neoforged.fml.common.EventBusSubscriber(modid = com.example.soundattract.SoundAttractMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class MobGroupManager {

  private static final Map<ResourceLocation, PerWorldData> worldData = new ConcurrentHashMap<>();
  private static final AtomicLong serverGeneration = new AtomicLong();
  private static final int RELAY_SOUND_TTL = 40;
  private static final int RELAY_SOUND_RATE_LIMIT = 20;
  private static final int MAX_RELAY_SOUNDS_PER_LEADER = 128;

  private static class PerWorldData {

    final Map<UUID, Mob> uuidToLeader = Collections.synchronizedMap(
      new HashMap<>()
    );
    final Map<UUID, List<UUID>> followersByLeader = Collections.synchronizedMap(
      new HashMap<>()
    );
    final Map<UUID, GroupRoster> sharingRosters = new HashMap<>();
    long nextRosterEpoch;
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
      new WeakHashMap<>()
    );
    long lastGroupUpdateTime = -1;
    long lastCleanupTime = -1;

    volatile Future<?> inFlightGroupCompute;
  }

  private static PerWorldData getData(ResourceLocation dimension) {
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
    if (level == null || level.getServer() == null || SoundAttractConfig.COMMON == null) {
      return false;
    }
    try {
      PerWorldData data = getData(level.dimension().location());
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

      Set<net.minecraft.world.entity.EntityType<?>> configuredTypes = com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes();
      Set<net.minecraft.world.entity.EntityType<?>> attractedEntityTypes = configuredTypes == null
        ? Collections.emptySet()
        : configuredTypes;
      int simDistChunks = level.getServer().getPlayerList().getViewDistance();
      int simDistBlocks = Math.min(simDistChunks * 16, 96);
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
      ConfigSnapshot cfg = createGroupConfig();
      if (mobsSet.isEmpty()) {
        GroupComputeResult empty = WorkerComputations.computeGroups(
          Collections.emptyList(), cfg, Long.MAX_VALUE, level.dimension().location(), serverGeneration.get()
        );
        applyGroupResult(level, empty);
        return true;
      }
      List<MobSnapshot> snapshots = new ArrayList<>(mobsSet.size());
      for (Mob m : mobsSet) {
        snapshots.add(
          new MobSnapshot(
            m.getUUID(),
            m.getX(),
            m.getY(),
            m.getZ(),
            m.getHealth(),
            m.isAlive(),
            isExistingLeader(m),
            isAerialScoutCandidate(m)
          )
        );
      }
      Future<?> submitted = WorkSchedulerManager.get().submitGroupCompute(snapshots, cfg, level.dimension().location());
      if (submitted == null) return false;
      data.inFlightGroupCompute = submitted;
      if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.MOB_GROUPS)) {
        SoundAttractMod.LOGGER.info(
          "[MobGroupManager] Submitted async group compute for {} mobs in dimension {}",
          snapshots.size(),
          level.dimension().location()
        );
      }
      return true;
    } catch (Throwable t) {
      if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.MOB_GROUPS)) {
        SoundAttractMod.LOGGER.error(
          "[MobGroupManager] submitGroupComputeSnapshot failed for dimension " +
          level.dimension().location(),
          t
        );
      }
      return false;
    }
  }

  private static ConfigSnapshot createGroupConfig() {
    return new ConfigSnapshot(
      SoundAttractConfig.COMMON.leaderGroupRadius.get(),
      SoundAttractConfig.COMMON.maxLeaders.get(),
      SoundAttractConfig.COMMON.maxGroupSize.get(),
      SoundAttractConfig.COMMON.leaderSpacingMultiplier.get(),
      SoundAttractConfig.COMMON.numEdgeSectors.get(),
      SoundAttractConfig.COMMON.edgeMobsPerSector.get(),
      serverGeneration.get()
    );
  }

  private static boolean isExistingLeader(Mob mob) {
    if (mob == null || mob.level() == null) return false;
    PerWorldData data = worldData.get(mob.level().dimension().location());
    return data != null && data.uuidToLeader.get(mob.getUUID()) == mob;
  }

  public static void applyGroupResult(
    ServerLevel level,
    GroupComputeResult result
  ) {
    if (level == null || result == null || result.mobUuidToLeaderUuid() == null
      || result.edgeMobsByLeaderUuid() == null || result.deserterUuids() == null
      || result.dimension() == null) return;
    if (!result.dimension().equals(level.dimension().location())
      || result.generation() != serverGeneration.get()) return;
    PerWorldData data = getData(level.dimension().location());
    Map<UUID, Mob> uuidToMob = new HashMap<>();
    for (Map.Entry<UUID, UUID> entry : result.mobUuidToLeaderUuid().entrySet()) {
      resolveMob(level, uuidToMob, entry.getKey());
      resolveMob(level, uuidToMob, entry.getValue());
    }
    for (Map.Entry<UUID, java.util.Set<UUID>> entry : result.edgeMobsByLeaderUuid().entrySet()) {
      resolveMob(level, uuidToMob, entry.getKey());
      for (UUID mobId : entry.getValue()) resolveMob(level, uuidToMob, mobId);
    }
    data.uuidToLeader.clear();
    data.followersByLeader.clear();
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
    rebuildFollowerIndex(data);
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
    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.MOB_GROUPS)) {
      SoundAttractMod.LOGGER.info(
        "[MobGroupManager] Applied group result for dimension {}: leaders={}, deserters={}",
        level.dimension().location(),
        leaderUuids.size(),
        data.deserterUuids.size()
      );
    }
  }

  private static void resolveMob(ServerLevel level, Map<UUID, Mob> resolved, UUID id) {
    if (id == null || resolved.containsKey(id)) return;
    net.minecraft.world.entity.Entity entity = level.getEntity(id);
    if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
      resolved.put(id, mob);
    }
  }

  public static boolean isEdgeMob(Mob mob) {
    if (mob == null || mob.level() == null) return false;
    PerWorldData data = worldData.get(mob.level().dimension().location());
    if (data == null) return false;
    Mob leader = getLeader(mob);
    if (leader == mob) {
      return false;
    }
    Set<Mob> edgeSet = data.lastEdgeMobMap.get(leader);
    return edgeSet != null && edgeSet.contains(mob);
  }

  public static List<Mob> getFollowers(Mob leader) {
      if (leader == null || leader.level() == null) return Collections.emptyList();
      PerWorldData data = worldData.get(leader.level().dimension().location());
      if (data == null) return Collections.emptyList();
      List<Mob> followers = new ArrayList<>();
      List<UUID> followerIds;
      synchronized (data.followersByLeader) {
          followerIds = new ArrayList<>(data.followersByLeader.getOrDefault(
            leader.getUUID(), Collections.emptyList()));
      }
      if (leader.level() instanceof ServerLevel sl) {
          for (UUID followerId : followerIds) {
              net.minecraft.world.entity.Entity e = sl.getEntity(followerId);
              if (e instanceof Mob m && m != leader && m.isAlive() && !m.isRemoved()) {
                  followers.add(m);
              }
          }
      }
      return followers;
  }

  private static List<Mob> collectIndexedEligibleMobs(ServerLevel level, int radiusBlocks) {
    if (level == null) {
      return List.of();
    }
    List<IndexedEntitySnapshot> snapshots = SpatialLifecycleEvents.index()
        .queryRadius(level.dimension().location(), new net.minecraft.world.phys.Vec3(level.getSharedSpawnPos().getX(), level.getSharedSpawnPos().getY(), level.getSharedSpawnPos().getZ()), radiusBlocks);
    if (snapshots.isEmpty()) {
      return List.of();
    }
    Map<UUID, Mob> live = new HashMap<>();
    for (net.minecraft.server.level.ServerPlayer player : level.players()) {
      AABB area = player.getBoundingBox().inflate(radiusBlocks);
      for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
        live.put(mob.getUUID(), mob);
      }
    }
    List<Mob> mobs = new ArrayList<>();
    for (IndexedEntitySnapshot snapshot : snapshots) {
      Mob mob = live.get(snapshot.entityUuid());
      if (mob != null && SpatialLifecycleEvents.isEligible(mob)) {
        mobs.add(mob);
      }
    }
    mobs.sort(Comparator.comparing(Mob::getUUID));
    return mobs;
  }

  private static boolean submitIndexedGroupCompute(ServerLevel level, List<Mob> mobs) {
    if (mobs == null || mobs.isEmpty()) {
      return false;
    }
    PerWorldData data = getData(level.dimension().location());
    List<MobSnapshot> snapshots = new ArrayList<>(mobs.size());
    for (Mob m : mobs) {
      snapshots.add(new MobSnapshot(m.getUUID(), m.getX(), m.getY(), m.getZ(), m.getHealth(), m.isAlive(), false, isAerialScoutCandidate(m)));
    }
    ConfigSnapshot cfg = new ConfigSnapshot(
        SoundAttractConfig.COMMON.leaderGroupRadius.get(),
        SoundAttractConfig.COMMON.maxLeaders.get(),
        SoundAttractConfig.COMMON.maxGroupSize.get(),
        SoundAttractConfig.COMMON.leaderSpacingMultiplier.get(),
        SoundAttractConfig.COMMON.numEdgeSectors.get(),
        SoundAttractConfig.COMMON.edgeMobsPerSector.get(),
        serverGeneration.get());
    Future<?> submitted = WorkSchedulerManager.get().submitGroupCompute(snapshots, cfg, level.dimension().location());
    if (submitted == null) return false;
    data.inFlightGroupCompute = submitted;
    return true;
  }

  public static List<Mob> getKnownLeaders(ServerLevel level, int limit) {
    if (level == null || limit <= 0) return Collections.emptyList();
    PerWorldData data = worldData.get(level.dimension().location());
    if (data == null) return Collections.emptyList();
    List<Mob> result = new ArrayList<>(Math.min(limit, data.leaders.size()));
    synchronized (data.leaders) {
      data.leaders.removeIf(ref -> ref.get() == null || ref.get().isRemoved());
      for (WeakReference<Mob> reference : data.leaders) {
        Mob leader = reference.get();
        if (leader == null || leader.isRemoved() || !leader.isAlive()) continue;
        result.add(leader);
        if (result.size() >= limit) break;
      }
    }
    return result;
  }

  private static void cleanupStaleEntries(ServerLevel level) {
    PerWorldData data = getData(level.dimension().location());
    data.leaders.removeIf(ref -> {
      Mob mob = ref.get();
      return mob == null || mob.isRemoved();
    });
    data.uuidToLeader.values().removeIf(mob -> mob == null || mob.isRemoved());
    rebuildFollowerIndex(data);
    data.mobToRelayedSounds.keySet().removeIf(mob -> mob == null || mob.isRemoved());
    data.mobLastRelayTime.keySet().removeIf(mob -> mob == null || mob.isRemoved());

    synchronized (data.lastEdgeMobMap) {
      data.lastEdgeMobMap.keySet().removeIf(mob -> mob == null || mob.isRemoved());
      for (Set<Mob> edges : data.lastEdgeMobMap.values()) {
        if (edges != null) {
            edges.removeIf(mob -> mob == null || mob.isRemoved());
        }
      }
    }
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel serverLevel) {
          ResourceLocation dimension = serverLevel.dimension().location();
          PerWorldData removed = worldData.remove(dimension);
          if (removed != null && removed.inFlightGroupCompute != null) {
              removed.inFlightGroupCompute.cancel(false);
              removed.inFlightGroupCompute = null;
          }
          serverGeneration.incrementAndGet();
          if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.MOB_GROUPS)) {
              SoundAttractMod.LOGGER.info("[MobGroupManager] Cleared cluster memory for unloaded dimension: {}", dimension);
          }
      }
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
      for (PerWorldData data : worldData.values()) {
          if (data != null && data.inFlightGroupCompute != null) {
              data.inFlightGroupCompute.cancel(false);
              data.inFlightGroupCompute = null;
          }
      }
      worldData.clear();
      serverGeneration.incrementAndGet();
  }

  public static void updateGroups(ServerLevel level) {
    if (level == null || level.getServer() == null || SoundAttractConfig.COMMON == null) {
      return;
    }
    PerWorldData data = getData(level.dimension().location());
    long time = level.getGameTime();
    if (time - data.lastGroupUpdateTime < SoundAttractConfig.COMMON.groupUpdateInterval.get()) return;
    data.lastGroupUpdateTime = time;
    if (
      DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.MOB_GROUPS) &&
      (time % 100 == 0)
    ) {
      SoundAttractMod.LOGGER.info(
        "[MobGroupManager] Updating groups for dimension: {}",
        level.dimension().location()
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
    Set<net.minecraft.world.entity.EntityType<?>> configuredTypes = com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes();
    Set<net.minecraft.world.entity.EntityType<?>> attractedEntityTypes = configuredTypes == null
      ? Collections.emptySet()
      : configuredTypes;
    int simDistChunks = level.getServer().getPlayerList().getViewDistance();
    int simDistBlocks = Math.min(simDistChunks * 16, 96);
    Set<Mob> mobsSet = new HashSet<>();
    for (net.minecraft.server.level.ServerPlayer player : level.players()) {
      net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(simDistBlocks);
      List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, box, mob ->
        mob.isAlive() && !mob.isRemoved() && isEligibleForGrouping(mob, attractedEntityTypes)
      );
      mobsSet.addAll(nearbyMobs);
    }
    List<MobSnapshot> snapshots = new ArrayList<>(mobsSet.size());
    for (Mob mob : mobsSet) {
      snapshots.add(new MobSnapshot(mob.getUUID(), mob.getX(), mob.getY(), mob.getZ(), mob.getHealth(), mob.isAlive(), isExistingLeader(mob), isAerialScoutCandidate(mob)));
    }
    GroupComputeResult result = WorkerComputations.computeGroups(
      snapshots, createGroupConfig(), Long.MAX_VALUE, level.dimension().location(), serverGeneration.get()
    );
    applyGroupResult(level, result);
  }

  private static boolean isEligibleForGrouping(Mob mob, Set<net.minecraft.world.entity.EntityType<?>> attractedEntityTypes) {
    return attractedEntityTypes.contains(mob.getType())
      || SoundAttractConfig.getMatchingProfile(mob) != null
      || com.example.soundattract.event.SoundAttractionEvents.isCustomNpcsMob(mob);
  }

  private static boolean isAerialScoutCandidate(Mob mob) {
    return mob != null && mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
  }

  public static Set<UUID> discoverEligibleMobUuidsLegacy(ServerLevel level, int simDistBlocks) {
    if (level == null) {
      return Set.of();
    }
    Set<UUID> mobsSet = new HashSet<>();
    Set<net.minecraft.world.entity.EntityType<?>> attractedEntityTypes = com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes();
    for (net.minecraft.server.level.ServerPlayer player : level.players()) {
      AABB box = player.getBoundingBox().inflate(simDistBlocks);
      for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
        boolean eligible = attractedEntityTypes.contains(mob.getType())
                || SoundAttractConfig.getMatchingProfile(mob) != null
                || com.example.soundattract.event.SoundAttractionEvents.isCustomNpcsMob(mob);
        if (eligible) {
          mobsSet.add(mob.getUUID());
        }
      }
    }
    return mobsSet;
  }

  public static Set<UUID> discoverEligibleMobUuidsIndexed(ServerLevel level, int simDistBlocks) {
    if (level == null) {
      return Set.of();
    }
    Set<UUID> out = new HashSet<>();
    for (net.minecraft.server.level.ServerPlayer player : level.players()) {
      AABB box = player.getBoundingBox().inflate(simDistBlocks);
      List<IndexedEntitySnapshot> snapshots = SpatialLifecycleEvents.index()
              .queryRadius(level.dimension().location(), new net.minecraft.world.phys.Vec3(player.getX(), player.getY(), player.getZ()), simDistBlocks);
      for (IndexedEntitySnapshot snapshot : snapshots) {
        if (snapshot.position().x >= box.minX && snapshot.position().x <= box.maxX
                && snapshot.position().z >= box.minZ && snapshot.position().z <= box.maxZ) {
          out.add(snapshot.entityUuid());
        }
      }
    }
    return out;
  }

  private static void rebuildFollowerIndex(PerWorldData data) {
    if (data == null) {
      return;
    }
    synchronized (data.uuidToLeader) {
      synchronized (data.followersByLeader) {
        data.followersByLeader.clear();
        for (Map.Entry<UUID, Mob> entry : data.uuidToLeader.entrySet()) {
          UUID followerId = entry.getKey();
          Mob leader = entry.getValue();
          if (followerId == null || leader == null || leader.isRemoved()
              || followerId.equals(leader.getUUID())) {
            continue;
          }
          data.followersByLeader.computeIfAbsent(leader.getUUID(), ignored -> new ArrayList<>())
            .add(followerId);
        }
        data.sharingRosters.keySet().retainAll(data.followersByLeader.keySet());
        for (Map.Entry<UUID, List<UUID>> entry : data.followersByLeader.entrySet()) {
          UUID leaderId = entry.getKey();
          List<UUID> followers = entry.getValue();
          GroupRoster previous = data.sharingRosters.get(leaderId);
          if (previous != null && previous.members().size() == followers.size()
              && previous.members().containsAll(followers)) {
            continue;
          }
          data.sharingRosters.put(leaderId, new GroupRoster(++data.nextRosterEpoch, Set.copyOf(followers), List.copyOf(followers)));
        }
      }
    }
  }

  public record GroupRoster(long epoch, Set<UUID> members, List<UUID> followers) {}

  public static GroupRoster getGroupRoster(Mob leader) {
    if (leader == null) return null;
    PerWorldData data = worldData.get(leader.level().dimension().location());
    if (data == null) return null;
    synchronized (data.followersByLeader) {
      return data.sharingRosters.get(leader.getUUID());
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
    if (mob == null || mob.level() == null || soundId == null) return;
    PerWorldData data = getData(mob.level().dimension().location());
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
    long now = mob.level().getGameTime();
    relays.removeIf(r -> r == null || now - r.timestamp > RELAY_SOUND_TTL);
    if (!relays.contains(relay)) {
      if (relays.size() >= MAX_RELAY_SOUNDS_PER_LEADER) {
        relays.remove(0);
      }
      relays.add(relay);
    }
  }

  public static List<SoundRelay> consumeRelayedSounds(Mob leader) {
    if (leader == null || leader.level() == null) return Collections.emptyList();
    PerWorldData data = getData(leader.level().dimension().location());
    List<SoundRelay> relays = data.mobToRelayedSounds.remove(leader);
    if (relays == null) return Collections.emptyList();
    long now = leader.level().getGameTime();
    relays.removeIf(r -> now - r.timestamp > RELAY_SOUND_TTL);
    return relays;
  }

  public static Mob getLeader(Mob mob) {
    if (mob == null || mob.level() == null) return null;
    PerWorldData data = worldData.get(mob.level().dimension().location());
    if (data == null) return mob;
    return data.uuidToLeader.getOrDefault(mob.getUUID(), mob);
  }


  public static Mob getNearestLeader(Mob mob) {
    if (mob == null || mob.level() == null) return null;
    PerWorldData data = worldData.get(mob.level().dimension().location());
    if (data == null) return null;
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
    if (mob == null || mob.level() == null) return;
    PerWorldData data = worldData.get(mob.level().dimension().location());
    if (data == null) return;
    data.deserterUuids.add(mob.getUUID());
  }

  public static boolean isDeserter(Mob mob) {
    if (mob == null || mob.level() == null) return false;
    PerWorldData data = worldData.get(mob.level().dimension().location());
    if (data == null) return false;
    return data.deserterUuids.contains(mob.getUUID());
  }

  public static int getFollowerCount(Mob leader) {
    if (leader == null || leader.level() == null) return 0;
    PerWorldData data = worldData.get(leader.level().dimension().location());
    if (data == null) return 0;
    synchronized (data.followersByLeader) {
      List<UUID> followers = data.followersByLeader.get(leader.getUUID());
      return followers == null ? 0 : followers.size();
    }
  }


  public static boolean isMobInHighWeightOverride(Mob mob) {
    if (mob == null || !mob.isAlive()) return false;

    if (hasHighWeightOverride(mob, true)) return true;

    Mob leader = getLeader(mob);
    return leader != null && leader != mob && hasHighWeightOverride(leader, false);
  }

  private static boolean hasHighWeightOverride(Mob mob, boolean includeFollowerEdgeRelay) {
    for (WrappedGoal wrappedGoal : mob.goalSelector.getAvailableGoals()) {
      Goal goal = wrappedGoal.getGoal();
      if (goal instanceof AttractionGoal attraction && attraction.isHighWeightOverrideActive()) return true;
      if (goal instanceof LeaderAttractionGoal leaderAttraction && leaderAttraction.isHighWeightOverrideActive()) return true;
      if (includeFollowerEdgeRelay && goal instanceof FollowerEdgeRelayGoal edgeRelay
          && edgeRelay.isHighWeightOverrideActive()) return true;
    }
    return false;
  }
}
