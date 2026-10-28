package com.example.soundattract.pathfinding;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.async.AsyncManager;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class FlowFieldManager {

    private static final int FIELD_RADIUS = 32;
    private static final int MAX_NODES = 2000;
    private static final long CACHE_TTL_MS = 2000;
    private static final int MAX_FIELDS = 16;
    private static final int MAX_PENDING_SCANS = 64;

    private static final int SCAN_SLICE_POSITIONS = 128;
    private static final int MAX_SCAN_POSITIONS_PER_TICK = 8192;
    private static final long SCAN_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(2);
    private static final Cache<String, FlowField> FIELDS = Caffeine.newBuilder()
            .maximumSize(MAX_FIELDS)
            .recordStats()
            .build();
    private static final Object SCAN_QUEUE_LOCK = new Object();
    private static final Map<ResourceKey<Level>, ArrayDeque<ScanJob>> SCAN_QUEUES = new HashMap<>();
    private static final ArrayDeque<ResourceKey<Level>> DIMENSION_ORDER = new ArrayDeque<>();
    private static final Map<String, GenerationRequest> PENDING_SCANS = new ConcurrentHashMap<>();
    private static final Set<CompletableFuture<?>> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicLong LIFECYCLE_GENERATION =
            new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean acceptingWork = true;
    private static long metricsTicks;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Set<CompletableFuture<?>> toCancel;
        synchronized (FIELDS) {
            acceptingWork = true;
            LIFECYCLE_GENERATION.incrementAndGet();
            FIELDS.invalidateAll();
            toCancel = Set.copyOf(IN_FLIGHT);
            IN_FLIGHT.clear();
        }
        clearPendingScans();
        metricsTicks = 0;
        for (CompletableFuture<?> future : toCancel) {
            future.cancel(false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Set<CompletableFuture<?>> toCancel;
        synchronized (FIELDS) {
            acceptingWork = false;
            LIFECYCLE_GENERATION.incrementAndGet();
            FIELDS.invalidateAll();
            toCancel = Set.copyOf(IN_FLIGHT);
            IN_FLIGHT.clear();
        }
        clearPendingScans();
        for (CompletableFuture<?> future : toCancel) {
            future.cancel(false);
        }
        HazardClassifier.clear();
    }

    public static Vec3 getNextStep(Mob mob, Vec3 target) {
        if (!acceptingWork || mob == null || target == null) return null;
        HazardClassifier.markPursuer(mob);

        Mob leader = MobGroupManager.getLeader(mob);
        int groupId = leader != null ? leader.getId() : mob.getId();
        String key = getKey(mob, groupId, target);
        cancelObsoleteScans(mob.level().dimension(), mob.getId(), key);
        FlowField field = FIELDS.getIfPresent(key);

        long now = System.currentTimeMillis();

        if (field == null || field.isExpired(now) || field.isTargetMoved(target)) {
            if (!PENDING_SCANS.containsKey(key)) {
                triggerGeneration(key, mob, groupId, target);
            }
            if (field == null) return null;
        }

        return field.getVector(mob.blockPosition());
    }

    private static String getKey(Mob mob, int groupId, Vec3 target) {
        BlockPos p = new BlockPos((int) target.x, (int) target.y, (int) target.z);
        String dimension = mobDimension(mob);
        return dimension + "_" + groupId + "_" + p.getX() + "_" + p.getY() + "_" + p.getZ();
    }

    private static String mobDimension(Mob mob) {
        try {
            return mob.level().dimension().location().toString();
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static void triggerGeneration(String key, Mob mob, int groupId, Vec3 target) {
        Level level = mob.level();
        if (!acceptingWork || level == null) return;
        long generation = LIFECYCLE_GENERATION.get();
        GenerationRequest request = new GenerationRequest(
                key, mob, level, target,
                new BlockPos((int) target.x, (int) target.y, (int) target.z),
                level.dimension(), generation, mob.getId());
        synchronized (SCAN_QUEUE_LOCK) {
            if (!acceptingWork || PENDING_SCANS.containsKey(key)
                    || PENDING_SCANS.size() >= MAX_PENDING_SCANS) return;
            PENDING_SCANS.put(key, request);
            enqueueScanLocked(new ScanJob(request));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event == null || !acceptingWork) return;
        drainScanBudget();
        logCacheMetrics();
    }

    private static void drainScanBudget() {
        long startNanos = System.nanoTime();
        int scannedPositions = 0;
        while (scannedPositions < MAX_SCAN_POSITIONS_PER_TICK
                && System.nanoTime() - startNanos < SCAN_BUDGET_NANOS) {
            ScanJob job = pollNextScan();
            if (job == null) return;
            GenerationRequest request = job.request;
            if (!isRequestCurrent(request)) {
                PENDING_SCANS.remove(request.key, request);
                continue;
            }

            try {
                int visited = job.scanSlice(SCAN_SLICE_POSITIONS);
                scannedPositions += visited;
                if (job.isComplete()) {
                    submitFlowField(request, job.walkabilitySnapshot);
                } else {
                    requeueScan(job);
                }
            } catch (RuntimeException ex) {
                PENDING_SCANS.remove(request.key, request);
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
                    SoundAttractMod.LOGGER.debug("[FlowField] Snapshot rejected for {}", request.key, ex);
                }
            }
        }
    }

    private static void logCacheMetrics() {
        if (!DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)
                || ++metricsTicks % 100 != 0) return;
        int dimensions;
        synchronized (SCAN_QUEUE_LOCK) {
            dimensions = SCAN_QUEUES.size();
        }
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = FIELDS.stats();
        SoundAttractMod.LOGGER.debug(
                "[FlowField] pending={} dimensions={} cached={} cacheHits={} cacheMisses={} evictions={}",
                PENDING_SCANS.size(), dimensions, FIELDS.estimatedSize(), stats.hitCount(),
                stats.missCount(), stats.evictionCount());
    }

    private static boolean isRequestCurrent(GenerationRequest request) {
        Mob mob = request.mob;
        return !request.cancelled && acceptingWork
                && request.generation == LIFECYCLE_GENERATION.get()
                && PENDING_SCANS.get(request.key) == request
                && mob != null && !mob.isRemoved() && mob.isAlive()
                && mob.level() == request.level
                && mob.level().dimension().equals(request.dimension);
    }

    private static ScanJob pollNextScan() {
        synchronized (SCAN_QUEUE_LOCK) {
            while (!DIMENSION_ORDER.isEmpty()) {
                ResourceKey<Level> dimension = DIMENSION_ORDER.removeFirst();
                ArrayDeque<ScanJob> queue = SCAN_QUEUES.get(dimension);
                if (queue == null || queue.isEmpty()) {
                    SCAN_QUEUES.remove(dimension);
                    continue;
                }
                ScanJob job = queue.removeFirst();
                if (!queue.isEmpty()) DIMENSION_ORDER.addLast(dimension);
                else SCAN_QUEUES.remove(dimension);
                return job;
            }
            return null;
        }
    }

    private static void requeueScan(ScanJob job) {
        synchronized (SCAN_QUEUE_LOCK) {
            enqueueScanLocked(job);
        }
    }

    private static void enqueueScanLocked(ScanJob job) {
        ResourceKey<Level> dimension = job.request.dimension;
        ArrayDeque<ScanJob> queue = SCAN_QUEUES.computeIfAbsent(dimension, ignored -> new ArrayDeque<>());
        boolean wasEmpty = queue.isEmpty();
        queue.addLast(job);
        if (wasEmpty && !DIMENSION_ORDER.contains(dimension)) DIMENSION_ORDER.addLast(dimension);
    }

    private static void clearPendingScans() {
        synchronized (SCAN_QUEUE_LOCK) {
            PENDING_SCANS.values().forEach(request -> request.cancelled = true);
            PENDING_SCANS.clear();
            SCAN_QUEUES.clear();
            DIMENSION_ORDER.clear();
        }
    }

    private static void cancelObsoleteScans(ResourceKey<Level> dimension, int requesterId, String currentKey) {
        synchronized (SCAN_QUEUE_LOCK) {
            for (GenerationRequest request : PENDING_SCANS.values()) {
                if (request.dimension.equals(dimension) && request.requesterId == requesterId
                        && !request.key.equals(currentKey)) {
                    request.cancelled = true;
                    PENDING_SCANS.remove(request.key, request);
                    CompletableFuture<?> future = request.future;
                    if (future != null) future.cancel(false);
                }
            }

            Iterator<Map.Entry<ResourceKey<Level>, ArrayDeque<ScanJob>>> queues = SCAN_QUEUES.entrySet().iterator();
            while (queues.hasNext()) {
                Map.Entry<ResourceKey<Level>, ArrayDeque<ScanJob>> entry = queues.next();
                if (entry.getKey().equals(dimension)) {
                    entry.getValue().removeIf(job -> job.request.cancelled);
                }
                if (entry.getValue().isEmpty()) {
                    DIMENSION_ORDER.remove(entry.getKey());
                    queues.remove();
                }
            }
        }
    }

    private static void submitFlowField(GenerationRequest request, WalkabilitySnapshot walkabilitySnapshot) {
        if (!isRequestCurrent(request)) {
            PENDING_SCANS.remove(request.key, request);
            return;
        }
        Duration timeout = Duration.ofMillis(10L);
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) timeout = Duration.ofMillis(Math.max(5L, budget.longValue()));
        } catch (Throwable ignored) {

        }
        Supplier<Map<BlockPos, Vec3>> work = () -> generateFlowField(walkabilitySnapshot, request.startNode);
        CompletableFuture<Map<BlockPos, Vec3>> submittedFuture = QuantifiedOptionalBridge.trySubmitTask(
                SoundAttractMod.MOD_ID, "soundattract_flow_field", work, true, false, timeout, request.key);
        final CompletableFuture<Map<BlockPos, Vec3>> future = submittedFuture != null ? submittedFuture
                : AsyncManager.submit("flow_field", work, AsyncManager.Priority.LOW, true);
        if (future == null) {
            PENDING_SCANS.remove(request.key, request);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
                SoundAttractMod.LOGGER.debug("[FlowField] Quantified returned no future for {}", request.key);
            }
            return;
        }
        request.future = future;
        synchronized (FIELDS) {
            if (!isRequestCurrent(request)) {
                future.cancel(false);
                PENDING_SCANS.remove(request.key, request);
                return;
            }
            IN_FLIGHT.add(future);
        }
        future.whenComplete((vectors, throwable) -> {
            try {
                synchronized (FIELDS) {
                    if (throwable == null && vectors != null && acceptingWork
                            && request.generation == LIFECYCLE_GENERATION.get()
                            && PENDING_SCANS.get(request.key) == request) {
                        FIELDS.put(request.key, new FlowField(request.target, vectors, System.currentTimeMillis()));
                    }
                }
            } finally {
                IN_FLIGHT.remove(future);
                PENDING_SCANS.remove(request.key, request);
            }
        });
    }

    private static Map<BlockPos, Vec3> generateFlowField(WalkabilitySnapshot walkabilitySnapshot, BlockPos start) {
        Map<BlockPos, Integer> costs = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));

        costs.put(start, 0);
        open.add(new Node(start, 0));

        int processed = 0;

        while (!open.isEmpty() && processed < MAX_NODES) {
            Node current = open.poll();
            BlockPos pos = current.pos;

            if (costs.getOrDefault(pos, Integer.MAX_VALUE) < current.cost) continue;
            processed++;

            for (Direction dir : Direction.values()) {
                BlockPos nPos = pos.relative(dir);

                if (Math.abs(nPos.getX() - start.getX()) > FIELD_RADIUS ||
                        Math.abs(nPos.getZ() - start.getZ()) > FIELD_RADIUS ||
                        Math.abs(nPos.getY() - start.getY()) > 10) continue;

                if (!walkabilitySnapshot.isWalkable(nPos)) continue;

                int newCost = current.cost + 1;
                if (newCost < costs.getOrDefault(nPos, Integer.MAX_VALUE)) {
                    costs.put(nPos, newCost);
                    open.add(new Node(nPos, newCost));
                }
            }
        }

        Map<BlockPos, Vec3> flow = new HashMap<>();
        for (BlockPos pos : costs.keySet()) {
            if (pos.equals(start)) {
                flow.put(pos, Vec3.ZERO);
                continue;
            }

            BlockPos bestNeighbor = null;
            int minCost = costs.get(pos);

            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (costs.containsKey(n)) {
                    int c = costs.get(n);
                    if (c < minCost) {
                        minCost = c;
                        bestNeighbor = n;
                    }
                }
            }

            if (bestNeighbor != null) {
                double ex = bestNeighbor.getX() + 0.5;
                double ey = bestNeighbor.getY() + 0.5;
                double ez = bestNeighbor.getZ() + 0.5;

                double sx = pos.getX() + 0.5;
                double sy = pos.getY() + 0.5;
                double sz = pos.getZ() + 0.5;

                double ddx = ex - sx;
                double ddy = ey - sy;
                double ddz = ez - sz;

                double len = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (len > 1.0e-5) {
                    ddx /= len;
                    ddy /= len;
                    ddz /= len;
                }

                flow.put(pos, new Vec3(ddx, ddy, ddz));
            }
        }

        return flow;
    }

    private static final class GenerationRequest {
        final String key;
        final Mob mob;
        final Level level;
        final Vec3 target;
        final BlockPos startNode;
        final ResourceKey<Level> dimension;
        final long generation;
        final int requesterId;
        volatile boolean cancelled;
        volatile CompletableFuture<?> future;

        GenerationRequest(String key, Mob mob, Level level, Vec3 target, BlockPos startNode,
                          ResourceKey<Level> dimension, long generation, int requesterId) {
            this.key = key;
            this.mob = mob;
            this.level = level;
            this.target = target;
            this.startNode = startNode;
            this.dimension = dimension;
            this.generation = generation;
            this.requesterId = requesterId;
        }
    }

    private static final class ScanJob {
        private static final int VERTICAL_LAYERS = WalkabilitySnapshot.VERTICAL_LAYERS;
        private static final int DIAMETER = WalkabilitySnapshot.DIAMETER;
        private static final int TOTAL_POSITIONS = DIAMETER * DIAMETER * VERTICAL_LAYERS;

        final GenerationRequest request;
        final WalkabilitySnapshot walkabilitySnapshot;
        private int cursor;

        ScanJob(GenerationRequest request) {
            this.request = request;
            this.walkabilitySnapshot = new WalkabilitySnapshot(request.startNode);
        }

        int scanSlice(int maxPositions) {
            int visited = 0;
            int radiusSquared = FIELD_RADIUS * FIELD_RADIUS;
            while (cursor < TOTAL_POSITIONS && visited < maxPositions) {
                int index = cursor++;
                visited++;

                int layer = index % VERTICAL_LAYERS;
                int column = index / VERTICAL_LAYERS;
                int dx = column / DIAMETER - FIELD_RADIUS;
                int dz = column % DIAMETER - FIELD_RADIUS;
                if (dx * dx + dz * dz > radiusSquared) continue;

                int dy = layer - 4;
                BlockPos pos = request.startNode.offset(dx, dy, dz);
                boolean walkable = request.level.hasChunkAt(pos)
                        && NodeRouter.isWalkable(request.mob, Vec3.atBottomCenterOf(pos));
                walkabilitySnapshot.set(dx, dy, dz, walkable);
            }
            return visited;
        }

        boolean isComplete() {
            return cursor >= TOTAL_POSITIONS;
        }
    }

    private static final class WalkabilitySnapshot {
        private static final int VERTICAL_LAYERS = 9;
        private static final int DIAMETER = FIELD_RADIUS * 2 + 1;
        private static final int LAYER_OFFSET = 4;

        final BlockPos origin;
        final byte[] cells = new byte[DIAMETER * DIAMETER * VERTICAL_LAYERS];

        WalkabilitySnapshot(BlockPos origin) {
            this.origin = origin;
        }

        void set(int dx, int dy, int dz, boolean walkable) {
            cells[index(dx, dy, dz)] = (byte) (walkable ? 1 : 0);
        }

        boolean isWalkable(BlockPos pos) {
            int dx = pos.getX() - origin.getX();
            int dy = pos.getY() - origin.getY();
            int dz = pos.getZ() - origin.getZ();
            if (Math.abs(dx) > FIELD_RADIUS || Math.abs(dz) > FIELD_RADIUS
                    || dy < -LAYER_OFFSET || dy > LAYER_OFFSET) return false;
            return cells[index(dx, dy, dz)] != 0;
        }

        private int index(int dx, int dy, int dz) {
            return ((dx + FIELD_RADIUS) * DIAMETER + dz + FIELD_RADIUS) * VERTICAL_LAYERS
                    + dy + LAYER_OFFSET;
        }
    }

    private static class Node {
        BlockPos pos;
        int cost;
        Node(BlockPos p, int c) { this.pos = p; this.cost = c; }
    }

    private static class FlowField {
        final Vec3 target;
        final Map<BlockPos, Vec3> vectors;
        final long created;

        FlowField(Vec3 target, Map<BlockPos, Vec3> vectors, long now) {
            this.target = target;
            this.vectors = Collections.unmodifiableMap(vectors);
            this.created = now;
        }

        boolean isExpired(long now) {
            return (now - created) > CACHE_TTL_MS;
        }

        boolean isTargetMoved(Vec3 newTarget) {
            return newTarget.distanceToSqr(target) > 2.0;
        }

        Vec3 getVector(BlockPos pos) {
            return vectors.get(pos);
        }
    }
}
