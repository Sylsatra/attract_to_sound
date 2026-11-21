package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.spatial.SpatialLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClueDirector {
    private static final int INGEST_PER_TICK = 64;
    private static final int INGEST_PER_SOURCE = 8;
    private static final int CELLS_PER_LEVEL = 256;
    private static final int CELLS_PER_SERVER = 1024;
    private static final int INTEREST_LIMIT = 2048;
    private static final int DISPATCH_INTERVAL = 10;
    private static final int RECIPIENTS_PER_DISPATCH = 8;
    private static final int ASSIGNMENTS_PER_CELL = 2;
    private static final int ASSIGNMENTS_PER_SOURCE = 4;
    private static final int CLUE_TTL = 120;
    private static final int SIGHT_TTL = 40;
    private static final int HINT_TTL = 60;
    private static final int INTEREST_TTL = 80;
    private static final int FAILED_BACKOFF = 40;
    private static final int BACKOFF_LIMIT = 4;
    private static final int CELL_RADIUS = 2;
    private static final int LOCAL_SIDE = CELL_RADIUS * 2 + 1;
    private static final double LOCAL_RANGE = 32.0;

    public enum Type {
        SOUND(0.6),
        SCENT(0.8),
        SIGHT(1.0);

        private final double priority;

        Type(double priority) {
            this.priority = priority;
        }
    }

    public enum Resolution {
        ARRIVED,
        DECLINED,
        FAILED,
        PREEMPTED,
        EXPIRED
    }

    public record Hint(ResourceKey<Level> dimension, BlockPos position, Type type, long expiresAt, long revision) {}

    public record Stats(int cells, int interests, long accepted, long coalesced, long ingestDrops, long sourceDrops,
                        long capacityDrops, long hintsIssued, long hintsReleased, long rangeRejected,
                        long timedDispatches, long dispatchNanos) {}

    private record CellKey(int x, int z, Type type) {
        static CellKey at(BlockPos position, Type type) {
            return new CellKey(position.getX() >> 4, position.getZ() >> 4, type);
        }

        static long columnKey(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }

    private record CellRef(ResourceKey<Level> dimension, CellKey key) {}
    private record RecipientRef(ResourceKey<Level> dimension, UUID id) {}
    private record ScoutStamp(UUID leaderId, int leaderEntityId, UUID scoutId, int scoutEntityId,
                              long rosterEpoch, Object evidence) {}
    private record Clue(BlockPos position, UUID source, double range, double strength, long seenAt, long expiresAt,
                        long revision, long signalId, ScoutStamp scoutStamp) {}
    private record Lease(CellKey cellKey, Cell cell, UUID source, long signalId, long deadline, Hint hint) {}
    private record Backoff(long revision, long deadline) {}

    private static final class Cell {
        private Clue clue;
        private final LinkedHashSet<UUID> recipients = new LinkedHashSet<>();

        private Cell(Clue clue) {
            this.clue = clue;
        }
    }

    private static final class Interest {
        private long deadline;
        private int cellCursor;
        private Lease lease;
        private LinkedHashMap<CellKey, Backoff> backoffs;
    }

    private static final class DimensionState {
        private final Map<CellKey, Cell> cells = new HashMap<>();
        private final Map<Long, LinkedHashSet<CellKey>> cellsByColumn = new HashMap<>();
        private final Map<UUID, Interest> interests = new HashMap<>();
        private final LinkedHashSet<UUID> ready = new LinkedHashSet<>();
    }

    private static final class DeadlineBuckets<K> {
        private final TreeMap<Long, LinkedHashSet<K>> buckets = new TreeMap<>();

        void put(K key, long deadline) {
            buckets.computeIfAbsent(deadline, ignored -> new LinkedHashSet<>()).add(key);
        }

        void remove(K key, long deadline) {
            LinkedHashSet<K> entries = buckets.get(deadline);
            if (entries == null) return;
            entries.remove(key);
            if (entries.isEmpty()) buckets.remove(deadline);
        }

        void expire(long now, int limit, Consumer<K> onExpired) {
            for (int count = 0; count < limit; count++) {
                Map.Entry<Long, LinkedHashSet<K>> bucket = buckets.firstEntry();
                if (bucket == null || bucket.getKey() > now) return;
                var iterator = bucket.getValue().iterator();
                K key = iterator.next();
                iterator.remove();
                if (bucket.getValue().isEmpty()) buckets.pollFirstEntry();
                onExpired.accept(key);
            }
        }
    }

    private static final Type[] TYPES = Type.values();
    private static final int LOCAL_COLUMNS = LOCAL_SIDE * LOCAL_SIDE;
    private static final AtomicLong policyRevision = new AtomicLong();
    private static final AtomicLong signalSequence = new AtomicLong();
    private static final Map<MinecraftServer, Map<ResourceKey<Level>, Long>> policyActivationTimes = new WeakHashMap<>();
    private static volatile ClueDirector active;

    private final MinecraftServer server;
    private final long policy;
    private final Map<ResourceKey<Level>, Long> activationTimes = new HashMap<>();
    private final Map<ResourceKey<Level>, DimensionState> levels = new HashMap<>();
    private final LinkedHashSet<ResourceKey<Level>> readyLevels = new LinkedHashSet<>();
    private final Map<UUID, Integer> admittedBySource = new HashMap<>();
    private final Map<UUID, Integer> leasesBySource = new HashMap<>();
    private final DeadlineBuckets<CellRef> clueDeadlines = new DeadlineBuckets<>();
    private final DeadlineBuckets<RecipientRef> interestDeadlines = new DeadlineBuckets<>();
    private final DeadlineBuckets<RecipientRef> leaseDeadlines = new DeadlineBuckets<>();
    private long ingestTick = Long.MIN_VALUE;
    private int ingestCount;
    private int cellCount;
    private int interestCount;
    private long accepted;
    private long coalesced;
    private long ingestDrops;
    private long sourceDrops;
    private long capacityDrops;
    private long hintsIssued;
    private long hintsReleased;
    private long rangeRejected;
    private long timedDispatches;
    private long dispatchNanos;

    private ClueDirector(MinecraftServer server, long policy, Map<ResourceKey<Level>, Long> activationTimes) {
        this.server = server;
        this.policy = policy;
        if (activationTimes != null) this.activationTimes.putAll(activationTimes);
        for (ServerLevel level : server.getAllLevels()) {
            this.activationTimes.putIfAbsent(level.dimension(), level.getGameTime());
        }
    }

    private static ClueDirector service(MinecraftServer server, boolean create) {
        if (server == null || !server.isSameThread()) return null;
        if (!com.example.soundattract.config.SoundAttractConfig.COMMON.enableSecondBrain.get()) {
            disable(server);
            return null;
        }
        ClueDirector director = active;
        long revision = policyRevision.get();
        if (director != null && director.server == server && director.policy == revision) return director;
        if (!create) return null;
        Map<ResourceKey<Level>, Long> activationTimes;
        synchronized (policyActivationTimes) {
            activationTimes = policyActivationTimes.remove(server);
        }
        director = new ClueDirector(server, revision, activationTimes);
        active = director;
        return director;
    }

    private static void disable(MinecraftServer server) {
        ClueDirector director = active;
        if (director == null || director.server != server) return;
        active = null;
        for (Map.Entry<ResourceKey<Level>, DimensionState> entry : new HashMap<>(director.levels).entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) continue;
            for (UUID id : entry.getValue().interests.keySet().toArray(UUID[]::new)) {
                if (level.getEntity(id) instanceof Mob mob) {
                    ClueInvestigationGoal.stopForBrainDisabled(mob);
                    com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat.stopSecondBrainMovement(mob);
                }
                director.removeInterest(new RecipientRef(entry.getKey(), id));
            }
        }
        director.levels.clear();
        director.readyLevels.clear();
        director.clueDeadlines.buckets.clear();
        director.interestDeadlines.buckets.clear();
        director.leaseDeadlines.buckets.clear();
        director.cellCount = 0;
        director.interestCount = 0;
    }

    public static long onSoundAccepted(String dimensionKey, BlockPos position, int lifetimeTicks,
                                       double range, double weight, UUID sourceId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || dimensionKey == null) return 0L;
        ResourceLocation dimension = ResourceLocation.tryParse(dimensionKey);
        if (dimension == null) return 0L;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) return 0L;
        return accept(level, Type.SOUND, position, level.getGameTime(), lifetimeTicks, range, weight, sourceId);
    }

    public static long onScentAccepted(ServerLevel level, BlockPos position, long seenTick, int lifetimeTicks,
                                       double range, double strength, UUID sourceId) {
        return accept(level, Type.SCENT, position, seenTick, lifetimeTicks, range, strength, sourceId);
    }

    public static long reportScoutSound(Mob scout, com.example.soundattract.tracking.SoundTracker.SoundRecord evidence) {
        if (scout == null || evidence == null || !(scout.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !evidence.dimensionKey.equals(level.dimension().location().toString())
                ) return 0L;
        com.example.soundattract.tracking.SoundTracker.SoundRecord liveEvidence =
                com.example.soundattract.tracking.SoundTracker.getLiveClueEvidence(evidence);
        if (liveEvidence == null) return 0L;
        ScoutStamp stamp = scoutStamp(scout, liveEvidence.clueSignalId);
        if (stamp == null) return 0L;
        return accept(level, Type.SOUND, liveEvidence.pos, level.getGameTime(), liveEvidence.ticksRemaining,
                liveEvidence.range, liveEvidence.weight, null, stamp);
    }

    public static long reportScoutScent(Mob scout, com.example.soundattract.scents.ScentNode evidence) {
        if (scout == null || evidence == null || !(scout.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || evidence.getOwnerUUID() == null
                || (evidence.getSourceType() != com.example.soundattract.scents.ScentSourceType.PLAYER_WALK
                && evidence.getSourceType() != com.example.soundattract.scents.ScentSourceType.PLAYER_WIND)) return 0L;
        com.example.soundattract.scents.ScentManager manager = level.getCapability(
                com.example.soundattract.scents.ScentManager.INSTANCE).orElse(null);
        if (manager == null || !manager.containsNodeIdentity(evidence)) return 0L;
        ScoutStamp stamp = scoutStamp(scout, evidence);
        if (stamp == null) return 0L;
        long age = level.getGameTime() - evidence.getTimestamp();
        if (age < 0L) return 0L;
        int lifetime = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        return accept(level, Type.SCENT, evidence.getBlockPos(), evidence.getTimestamp(), lifetime,
                SoundAttractConfig.COMMON.scentDetectionRadius.get(), evidence.getStrength(), evidence.getOwnerUUID(), stamp);
    }

    private static ScoutStamp scoutStamp(Mob scout, Object evidence) {
        if (!(scout.level() instanceof ServerLevel level) || !MobGroupManager.isEdgeMob(scout)) return null;
        Mob leader = MobGroupManager.getLeader(scout);
        MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
        if (leader == null || leader == scout || !leader.isAlive() || leader.level() != level || roster == null
                || !roster.members().contains(scout.getUUID())) return null;
        return new ScoutStamp(leader.getUUID(), leader.getId(), scout.getUUID(), scout.getId(), roster.epoch(), evidence);
    }

    public static void onConfirmedSight(ServerLevel level, BlockPos position, long seenTick, UUID sourceId, UUID targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) return;
        accept(level, Type.SIGHT, position, seenTick, SIGHT_TTL, LOCAL_RANGE, 1.0, targetId);
    }

    public static void onClueRemoved(ServerLevel level, Type type, BlockPos position, long signalId) {
        if (level == null || type == null || position == null || signalId == 0L) return;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return;
        DimensionState state = director.levels.get(level.dimension());
        if (state == null) return;
        CellKey key = CellKey.at(position, type);
        Cell cell = state.cells.get(key);
        if (cell == null) return;
        CellRef ref = new CellRef(level.dimension(), key);
        if (cell.clue.signalId() == signalId) {
            director.removeCell(ref);
            return;
        }
        for (UUID id : cell.recipients.toArray(UUID[]::new)) {
            Interest interest = state.interests.get(id);
            if (interest != null && interest.lease != null && interest.lease.signalId() == signalId) {
                director.release(new RecipientRef(level.dimension(), id), interest, Resolution.EXPIRED);
            }
        }
    }

    private static long accept(ServerLevel level, Type type, BlockPos position, long seenTick, int lifetimeTicks,
                               double range, double strength, UUID sourceId) {
        return accept(level, type, position, seenTick, lifetimeTicks, range, strength, sourceId, null);
    }

    private static long accept(ServerLevel level, Type type, BlockPos position, long seenTick, int lifetimeTicks,
                               double range, double strength, UUID sourceId, ScoutStamp scoutStamp) {
        if (level == null || position == null || !Double.isFinite(range) || range <= 0.0
                || !Double.isFinite(strength) || strength <= 0.0) return 0L;
        ClueDirector director = service(level.getServer(), true);
        if (director == null || director.server.getLevel(level.dimension()) != level) return 0L;
        long age = level.getGameTime() - seenTick;
        long remaining = Math.min(lifetimeTicks, CLUE_TTL) - age;
        if (age < 0L || remaining <= 0L) return 0L;
        long activationTime = director.activationTimes.computeIfAbsent(level.dimension(), ignored -> level.getGameTime());
        if (seenTick < activationTime) return 0L;
        return director.ingest(level, type, position, age, remaining, Math.min(range, LOCAL_RANGE), strength, sourceId, scoutStamp);
    }

    private long ingest(ServerLevel level, Type type, BlockPos position, long age, long remaining,
                        double range, double strength, UUID sourceId, ScoutStamp scoutStamp) {
        long now = server.getTickCount();
        if (ingestTick != now) {
            ingestTick = now;
            ingestCount = 0;
            admittedBySource.clear();
        }
        if (ingestCount >= INGEST_PER_TICK) {
            ingestDrops++;
            return 0L;
        }
        int sourceCount = sourceId == null ? 0 : admittedBySource.getOrDefault(sourceId, 0);
        if (sourceCount >= INGEST_PER_SOURCE) {
            sourceDrops++;
            return 0L;
        }
        ingestCount++;
        if (sourceId != null) admittedBySource.put(sourceId, sourceCount + 1);
        if (!level.hasChunkAt(position)) return 0L;

        ResourceKey<Level> dimension = level.dimension();
        DimensionState state = levels.get(dimension);
        CellKey key = CellKey.at(position, type);
        Cell cell = state == null ? null : state.cells.get(key);
        if (cell == null && (cellCount >= CELLS_PER_SERVER || state != null && state.cells.size() >= CELLS_PER_LEVEL)) {
            capacityDrops++;
            return 0L;
        }

        long seenAt = now - age;
        Clue previous = cell == null ? null : cell.clue;
        if (previous != null && previous.seenAt() > seenAt) return 0L;
        CellRef ref = new CellRef(dimension, key);
        long signalId = signalSequence.incrementAndGet();
        boolean sameArea = previous != null && java.util.Objects.equals(previous.source(), sourceId)
                && previous.position().getY() == position.getY() && previous.position().distSqr(position) <= 4.0;
        Clue clue = new Clue(position.immutable(), sourceId, range, strength, seenAt, now + remaining,
                sameArea ? previous.revision() : signalId, signalId, scoutStamp);
        if (cell == null) {
            DimensionState target = levels.computeIfAbsent(dimension, ignored -> new DimensionState());
            target.cells.put(key, new Cell(clue));
            target.cellsByColumn.computeIfAbsent(CellKey.columnKey(key.x(), key.z()), ignored -> new LinkedHashSet<>()).add(key);
            cellCount++;
        } else {
            clueDeadlines.remove(ref, previous.expiresAt());
            cell.clue = clue;
            coalesced++;
        }
        clueDeadlines.put(ref, clue.expiresAt());
        accepted++;
        return signalId;
    }

    public static boolean registerInterest(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return false;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return false;
        DimensionState state = director.levels.get(level.dimension());
        if (state == null || state.cells.isEmpty() || !canInvestigate(mob)) return false;
        UUID id = mob.getUUID();
        Interest interest = state.interests.get(id);
        if (interest == null) {
            if (director.interestCount >= INTEREST_LIMIT) return false;
            interest = new Interest();
            interest.cellCursor = Math.floorMod(id.hashCode(), LOCAL_COLUMNS);
            state.interests.put(id, interest);
            director.interestCount++;
        }
        long now = level.getServer().getTickCount();
        if (interest.deadline - now < HINT_TTL) {
            RecipientRef ref = new RecipientRef(level.dimension(), id);
            director.interestDeadlines.remove(ref, interest.deadline);
            interest.deadline = now + INTEREST_TTL;
            director.interestDeadlines.put(ref, interest.deadline);
        }
        state.ready.add(id);
        director.readyLevels.add(level.dimension());
        return true;
    }

    public static void cancelInterest(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return;
        RecipientRef ref = new RecipientRef(level.dimension(), mob.getUUID());
        DimensionState state = director.levels.get(ref.dimension());
        if (state == null) return;
        Interest interest = state.interests.get(ref.id());
        if (interest == null) return;
        director.release(ref, interest, Resolution.PREEMPTED);
        if (interest.backoffs == null || interest.backoffs.isEmpty()) {
            director.removeInterest(ref);
        } else {
            state.ready.remove(ref.id());
            if (state.ready.isEmpty()) director.readyLevels.remove(ref.dimension());
        }
    }

    public static Hint takeHint(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return null;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return null;
        RecipientRef ref = new RecipientRef(level.dimension(), mob.getUUID());
        Interest interest = director.interest(ref);
        if (interest == null || interest.lease == null) return null;
        Lease lease = interest.lease;
        if (lease.deadline() <= director.server.getTickCount() || !level.hasChunkAt(lease.hint().position())) {
            director.release(ref, interest, Resolution.EXPIRED);
            return null;
        }
        if (lease.cell().clue.scoutStamp() != null && !isCurrentScoutEvidence(mob, lease.cell().clue)) {
            director.release(ref, interest, Resolution.EXPIRED);
            return null;
        }
        if (!canInvestigate(mob)) {
            cancelInterest(mob);
            return null;
        }
        return lease.hint();
    }

    public static Hint getActiveHint(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return null;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return null;
        DimensionState state = director.levels.get(level.dimension());
        Interest interest = state == null ? null : state.interests.get(mob.getUUID());
        Lease lease = interest == null ? null : interest.lease;
        if (lease == null || interest.deadline <= director.server.getTickCount()
                || lease.deadline() <= director.server.getTickCount() || !level.hasChunkAt(lease.hint().position())) return null;
        if (lease.cell().clue.scoutStamp() != null && !isCurrentScoutEvidence(mob, lease.cell().clue)) return null;
        return lease.hint();
    }

    public static com.example.soundattract.tracking.SoundTracker.SoundRecord getSoundEvidence(Mob mob, Hint hint) {
        if (mob == null || hint == null || !(mob.level() instanceof ServerLevel level)) return null;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return null;
        Interest interest = director.interest(new RecipientRef(level.dimension(), mob.getUUID()));
        if (interest == null || interest.lease == null || interest.lease.hint() != hint
                || hint.type() != Type.SOUND) return null;
        ScoutStamp stamp = interest.lease.cell().clue.scoutStamp();
        long signalId = stamp != null && stamp.evidence() instanceof Long externalId
                ? externalId : interest.lease.signalId();
        return com.example.soundattract.tracking.SoundTracker.getLiveClueEvidence(
                signalId, level.dimension().location().toString(), hint.position());
    }

    public static boolean isHintActive(Mob mob, Hint hint) {
        return hint != null && takeHint(mob) == hint;
    }

    public static void releaseHint(Mob mob, Hint hint, Resolution resolution) {
        if (mob == null || hint == null || resolution == null || !(mob.level() instanceof ServerLevel level)) return;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return;
        RecipientRef ref = new RecipientRef(level.dimension(), mob.getUUID());
        Interest interest = director.interest(ref);
        if (interest != null && interest.lease != null && interest.lease.hint() == hint) {
            director.release(ref, interest, resolution);
        }
    }

    public static void invalidatePolicy() {
        policyRevision.incrementAndGet();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;
        Map<ResourceKey<Level>, Long> activationTimes = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            activationTimes.put(level.dimension(), level.getGameTime());
        }
        synchronized (policyActivationTimes) {
            policyActivationTimes.put(server, activationTimes);
        }
    }

    public static Stats stats(MinecraftServer server) {
        ClueDirector director = service(server, false);
        if (director == null) return new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new Stats(director.cellCount, director.interestCount, director.accepted, director.coalesced,
                director.ingestDrops, director.sourceDrops, director.capacityDrops, director.hintsIssued,
                director.hintsReleased, director.rangeRejected, director.timedDispatches, director.dispatchNanos);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        ClueDirector director = service(server, true);
        if (director == null) {
            ClueDirector previous = active;
            if (previous != null && previous.server == server) active = null;
            return;
        }
        director.tick();
    }

    private void tick() {
        long now = server.getTickCount();
        leaseDeadlines.expire(now, 32, ref -> {
            Interest interest = interest(ref);
            if (interest != null) release(ref, interest, Resolution.EXPIRED);
        });
        interestDeadlines.expire(now, 128, this::removeInterest);
        clueDeadlines.expire(now, 64, this::removeCell);
        if (now % DISPATCH_INTERVAL != 0L || readyLevels.isEmpty()
                || StealthDetectionEvents.getPerfTier(server) != StealthDetectionEvents.StealthPerfTier.FULL) return;

        boolean timeDispatch = DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SECOND_BRAIN);
        long start = timeDispatch ? System.nanoTime() : 0L;
        dispatch(now);
        if (timeDispatch) {
            timedDispatches++;
            dispatchNanos += System.nanoTime() - start;
        }
    }

    private void dispatch(long now) {
        HashSet<RecipientRef> checked = new HashSet<>(RECIPIENTS_PER_DISPATCH);
        for (int slot = 0; slot < RECIPIENTS_PER_DISPATCH && !readyLevels.isEmpty(); slot++) {
            ResourceKey<Level> dimension = rotate(readyLevels);
            DimensionState state = levels.get(dimension);
            if (state == null || state.ready.isEmpty()) {
                readyLevels.remove(dimension);
                continue;
            }
            UUID id = rotate(state.ready);
            RecipientRef ref = new RecipientRef(dimension, id);
            if (!checked.add(ref)) continue;
            Interest interest = state.interests.get(id);
            ServerLevel level = server.getLevel(dimension);
            if (interest == null || interest.deadline <= now || level == null
                    || !(level.getEntity(id) instanceof Mob mob) || !canInvestigate(mob)) {
                removeInterest(ref);
                continue;
            }
            if (interest.lease != null || !mob.getNavigation().isDone()) continue;
            if (!SpatialLifecycleEvents.isEligible(mob)
                    || SoundAttractionEvents.getCachedBlacklistedEntityTypes().contains(mob.getType())) {
                removeInterest(ref);
                continue;
            }
            CellKey key = chooseCell(state, level, mob, interest, now);
            if (key == null) continue;
            issue(ref, interest, key, state.cells.get(key), level, now);
        }
    }

    private CellKey chooseCell(DimensionState state, ServerLevel level, Mob mob, Interest interest, long now) {
        BlockPos mobPosition = mob.blockPosition();
        CellKey best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int cursor = interest.cellCursor;
        int originX = mobPosition.getX() >> 4;
        int originZ = mobPosition.getZ() >> 4;
        for (int step = 0; step < LOCAL_COLUMNS; step++) {
            int column = cursor;
            cursor = (cursor + 1) % LOCAL_COLUMNS;
            int cellX = originX + column % LOCAL_SIDE - CELL_RADIUS;
            int cellZ = originZ + column / LOCAL_SIDE - CELL_RADIUS;
            LinkedHashSet<CellKey> keys = state.cellsByColumn.get(CellKey.columnKey(cellX, cellZ));
            if (keys == null) continue;
            for (CellKey key : keys) {
                Cell cell = state.cells.get(key);
                if (cell == null || !canUseClue(mob, key.type(), cell.clue)
                        || cell.recipients.size() >= ASSIGNMENTS_PER_CELL
                        || cell.clue.source() != null
                        && leasesBySource.getOrDefault(cell.clue.source(), 0) >= ASSIGNMENTS_PER_SOURCE) continue;
                Clue clue = cell.clue;
                if (clue.expiresAt() <= now) continue;
                Backoff backoff = interest.backoffs == null ? null : interest.backoffs.get(key);
                if (backoff != null && backoff.revision() == clue.revision() && backoff.deadline() > now) continue;
                double distance = mobPosition.distSqr(clue.position());
                double rangeSq = clue.range() * clue.range();
                if (distance > rangeSq) {
                    rangeRejected++;
                    continue;
                }
                if (!level.hasChunkAt(clue.position())) continue;
                double score = key.type().priority + 2.0 * (1.0 - distance / rangeSq)
                        + (1.0 - (double) (now - clue.seenAt()) / CLUE_TTL) + Math.min(1.0, clue.strength());
                if (score > bestScore) {
                    bestScore = score;
                    best = key;
                }
            }
        }
        interest.cellCursor = cursor;
        return best;
    }

    private void issue(RecipientRef ref, Interest interest, CellKey key, Cell cell, ServerLevel level, long now) {
        Clue clue = cell.clue;
        long deadline = Math.min(Math.min(now + HINT_TTL, clue.expiresAt()), interest.deadline);
        Hint hint = new Hint(ref.dimension(), clue.position(), key.type(), level.getGameTime() + deadline - now, clue.revision());
        interest.lease = new Lease(key, cell, clue.source(), clue.signalId(), deadline, hint);
        cell.recipients.add(ref.id());
        if (clue.source() != null) leasesBySource.merge(clue.source(), 1, Integer::sum);
        leaseDeadlines.put(ref, deadline);
        hintsIssued++;
    }

    private void release(RecipientRef ref, Interest interest, Resolution resolution) {
        Lease lease = interest.lease;
        if (lease == null) return;
        interest.lease = null;
        leaseDeadlines.remove(ref, lease.deadline());
        lease.cell().recipients.remove(ref.id());
        if (lease.source() != null) {
            leasesBySource.computeIfPresent(lease.source(), (source, count) -> count <= 1 ? null : count - 1);
        }
        hintsReleased++;
        if (resolution != Resolution.FAILED && resolution != Resolution.ARRIVED && resolution != Resolution.DECLINED) return;
        if (interest.backoffs == null) interest.backoffs = new LinkedHashMap<>();
        interest.backoffs.remove(lease.cellKey());
        if (interest.backoffs.size() >= BACKOFF_LIMIT) {
            interest.backoffs.remove(interest.backoffs.keySet().iterator().next());
        }
        long deadline = resolution == Resolution.FAILED ? server.getTickCount() + FAILED_BACKOFF : lease.cell().clue.expiresAt();
        interest.backoffs.put(lease.cellKey(), new Backoff(lease.hint().revision(), deadline));
        if (interest.deadline < deadline) {
            interestDeadlines.remove(ref, interest.deadline);
            interest.deadline = deadline;
            interestDeadlines.put(ref, deadline);
        }
    }

    private Interest interest(RecipientRef ref) {
        DimensionState state = levels.get(ref.dimension());
        return state == null ? null : state.interests.get(ref.id());
    }

    private void removeInterest(RecipientRef ref) {
        DimensionState state = levels.get(ref.dimension());
        if (state == null) return;
        Interest interest = state.interests.remove(ref.id());
        if (interest == null) return;
        release(ref, interest, Resolution.PREEMPTED);
        interestDeadlines.remove(ref, interest.deadline);
        state.ready.remove(ref.id());
        if (state.ready.isEmpty()) readyLevels.remove(ref.dimension());
        if (state.cells.isEmpty() && state.interests.isEmpty()) levels.remove(ref.dimension());
        interestCount--;
    }

    private void removeCell(CellRef ref) {
        DimensionState state = levels.get(ref.dimension());
        if (state == null) return;
        Cell cell = state.cells.remove(ref.key());
        if (cell == null) return;
        long columnKey = CellKey.columnKey(ref.key().x(), ref.key().z());
        LinkedHashSet<CellKey> column = state.cellsByColumn.get(columnKey);
        if (column != null) {
            column.remove(ref.key());
            if (column.isEmpty()) state.cellsByColumn.remove(columnKey);
        }
        clueDeadlines.remove(ref, cell.clue.expiresAt());
        for (UUID id : cell.recipients.toArray(UUID[]::new)) {
            Interest interest = state.interests.get(id);
            if (interest != null) release(new RecipientRef(ref.dimension(), id), interest, Resolution.EXPIRED);
        }
        if (state.cells.isEmpty() && state.interests.isEmpty()) levels.remove(ref.dimension());
        cellCount--;
    }

    private static <T> T rotate(LinkedHashSet<T> ring) {
        var iterator = ring.iterator();
        T first = iterator.next();
        iterator.remove();
        ring.add(first);
        return first;
    }

    private static boolean canInvestigate(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (!mob.isAlive() || mob.isRemoved() || mob.isNoAi() || mob.isVehicle() || mob.isPassenger() || mob.isSleeping()
                || target != null && target.isAlive() || mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || LivingWallCoordinator.isControlled(mob)) {
            return false;
        }
        LivingEntity attacker = mob.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200) return false;
        Mob leader = MobGroupManager.getLeader(mob);
        if (SoundAttractionEvents.getCachedBlacklistedEntityTypes().contains(mob.getType())
                || !(SoundAttractionEvents.getCachedAttractedEntityTypes().contains(mob.getType())
                || SoundAttractConfig.getMatchingProfile(mob) != null || SoundAttractionEvents.isCustomNpcsMob(mob))) return false;
        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        return (!smartEdge || leader == null || leader == mob)
                && !RaidManager.isRaidTicking(mob) && !RaidManager.isRaidAdvancing(mob);
    }

    private static boolean canUseClue(Mob mob, Type type, Clue clue) {
        if (clue.scoutStamp() != null && !isCurrentScoutEvidence(mob, clue)) return false;
        Mob leader = MobGroupManager.getLeader(mob);
        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get() && leader != null) {
            MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
            boolean groupedLeader = leader == mob && roster != null && !roster.members().isEmpty();
            if ((leader != mob || groupedLeader) && (clue.scoutStamp() == null
                    || !clue.scoutStamp().leaderId().equals(leader.getUUID()))) return false;
        }
        if (type == Type.SCENT) {
            if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return false;
            ResourceLocation id = EntityType.getKey(mob.getType());
            return id != null && SoundAttractConfig.COMMON.scentEligibleMobs.get().contains(id.toString());
        }
        return true;
    }

    private static boolean isCurrentScoutEvidence(Mob recipient, Clue clue) {
        ScoutStamp stamp = clue.scoutStamp();
        if (stamp == null || !(recipient.level() instanceof ServerLevel level)) return stamp == null;
        Entity scoutEntity = level.getEntity(stamp.scoutId());
        Entity leaderEntity = level.getEntity(stamp.leaderId());
        if (!(scoutEntity instanceof Mob scout) || scout.getId() != stamp.scoutEntityId()
                || !(leaderEntity instanceof Mob leader) || leader.getId() != stamp.leaderEntityId()
                || MobGroupManager.getLeader(scout) != leader || !MobGroupManager.isEdgeMob(scout)) return false;
        MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
        if (roster == null || roster.epoch() != stamp.rosterEpoch()
                || !roster.members().contains(scout.getUUID())) return false;
        if (recipient != leader && (MobGroupManager.getLeader(recipient) != leader
                || roster.epoch() != MobGroupManager.getGroupRoster(leader).epoch())) return false;
        if (stamp.evidence() instanceof Long signalId) {
            return com.example.soundattract.tracking.SoundTracker.isLiveClueEvidence(
                    signalId, level.dimension().location().toString(), clue.position());
        }
        if (stamp.evidence() instanceof com.example.soundattract.scents.ScentNode node) {
            com.example.soundattract.scents.ScentManager manager = level.getCapability(
                    com.example.soundattract.scents.ScentManager.INSTANCE).orElse(null);
            return manager != null && manager.containsNodeIdentity(node)
                    && level.getGameTime() - node.getTimestamp() < SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        }
        return false;
    }


    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getLevel() instanceof ServerLevel level) {
            ClueDirector director = service(level.getServer(), false);
            if (director != null) director.removeInterest(new RecipientRef(level.dimension(), mob.getUUID()));
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ClueDirector director = service(level.getServer(), false);
        if (director == null) return;
        ResourceKey<Level> dimension = level.dimension();
        DimensionState state = director.levels.get(dimension);
        if (state == null) return;
        for (UUID id : state.interests.keySet().toArray(UUID[]::new)) {
            director.removeInterest(new RecipientRef(dimension, id));
        }
        for (CellKey key : state.cells.keySet().toArray(CellKey[]::new)) {
            director.removeCell(new CellRef(dimension, key));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ClueDirector director = active;
        if (director != null && director.server == event.getServer()) active = null;
    }
}
