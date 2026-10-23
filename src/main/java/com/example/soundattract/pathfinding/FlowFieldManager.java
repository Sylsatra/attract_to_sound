package com.example.soundattract.pathfinding;

import com.example.soundattract.Soundattract;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class FlowFieldManager {
    private static final int FIELD_RADIUS = 32;
    private static final int MAX_NODES = 2000;
    private static final long CACHE_TTL_MS = 2000L;
    private static final int MAX_FIELDS = 16;
    private static final int MAX_PENDING_SCANS = 64;
    private static final int SCAN_SLICE_POSITIONS = 128;
    private static final int MAX_SCAN_POSITIONS_PER_TICK = 8192;
    private static final long SCAN_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(2L);

    private static final Map<String, FlowField> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, GenerationRequest> PENDING_SCANS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, ArrayDeque<ScanJob>> SCAN_QUEUES = new HashMap<>();
    private static final ArrayDeque<ResourceKey<Level>> DIMENSION_ORDER = new ArrayDeque<>();
    private static final Object SCAN_QUEUE_LOCK = new Object();
    private static final Set<GenerationRequest> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Object FIELD_CACHE_LOCK = new Object();
    private static final AtomicLong LIFECYCLE_GENERATION = new AtomicLong();
    private static volatile int lastTickScannedPositions;
    private static volatile boolean scanDrainPausedForTesting;
    private static volatile LongSupplier scanClock = System::nanoTime;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SA-FlowField-Worker");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean acceptingWork = true;

    private FlowFieldManager() {
    }

    /** Exposes queued scan count to deterministic lifecycle GameTests. */
    public static int getPendingScanCountForTesting() {
        return PENDING_SCANS.size();
    }

    public static int getPendingScanCapacityForTesting() {
        return MAX_PENDING_SCANS;
    }

    public static int getLastTickScannedPositionsForTesting() {
        return lastTickScannedPositions;
    }

    public static int getScanPositionBudgetForTesting() {
        return MAX_SCAN_POSITIONS_PER_TICK;
    }

    public static long getScanTimeBudgetNanosForTesting() {
        return SCAN_BUDGET_NANOS;
    }

    /** Installs a deterministic monotonic clock for the scan-budget GameTest. */
    public static void setScanClockForTesting(LongSupplier clock) {
        scanClock = clock == null ? System::nanoTime : clock;
    }

    /** Prevents normal tick callbacks from consuming a deterministic queue fixture. */
    public static void setScanDrainPausedForTesting(boolean paused) {
        scanDrainPausedForTesting = paused;
    }

    /** Checks request replacement without exposing mutable queue state to GameTests. */
    public static boolean isScanPendingForTesting(Mob mob, Vec3 target) {
        if (mob == null || target == null) return false;
        Mob leader = MobGroupManager.getLeader(mob);
        int groupId = leader != null ? leader.getId() : mob.getId();
        return PENDING_SCANS.containsKey(getKey(mob, groupId, target));
    }

    public static Vec3 getNextStep(Mob mob, Vec3 target) {
        if (!acceptingWork || mob == null || target == null || mob.level().isClientSide()) return null;
        HazardClassifier.markPursuer(mob);

        Mob leader = MobGroupManager.getLeader(mob);
        int groupId = leader != null ? leader.getId() : mob.getId();
        String key = getKey(mob, groupId, target);
        cancelObsoleteScans(mob.level().dimension(), mob.getId(), key);

        long now = System.currentTimeMillis();
        FlowField field = FIELDS.get(key);
        if (field != null && field.isExpired(now)) {
            FIELDS.remove(key, field);
            field = null;
        }
        if (field == null || field.isTargetMoved(target)) {
            if (!PENDING_SCANS.containsKey(key)) triggerGeneration(key, mob, target);
            if (field == null) return null;
        }
        Vec3 vector = field.getVector(mob.blockPosition());
        if (vector == null) return null;




        if (!NodeRouter.isWalkable(mob, mob.position().add(vector))) {
            FIELDS.remove(key, field);
            GenerationRequest pending = PENDING_SCANS.get(key);
            if (pending != null) cancelRequest(pending);
            triggerGeneration(key, mob, target);
            return null;
        }
        return vector;
    }

    public static void onEndServerTick() {
        if (!acceptingWork) return;
        drainScanBudget();
        long now = System.currentTimeMillis();
        FIELDS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    public static void onLevelUnload(ServerLevel level) {
        if (level == null) return;
        ResourceKey<Level> dimension = level.dimension();
        FIELDS.keySet().removeIf(key -> key.startsWith(dimension.identifier() + "|"));
        synchronized (SCAN_QUEUE_LOCK) {
            for (GenerationRequest request : PENDING_SCANS.values()) {
                if (request.dimension.equals(dimension)) cancelRequest(request);
            }
            PENDING_SCANS.entrySet().removeIf(entry -> entry.getValue().dimension.equals(dimension));
            SCAN_QUEUES.remove(dimension);
            DIMENSION_ORDER.remove(dimension);
        }
        for (GenerationRequest request : IN_FLIGHT) {
            if (request.dimension.equals(dimension)) cancelRequest(request);
        }
    }

    public static void onServerStopped() {
        acceptingWork = false;
        LIFECYCLE_GENERATION.incrementAndGet();
        FIELDS.clear();
        synchronized (SCAN_QUEUE_LOCK) {
            for (GenerationRequest request : PENDING_SCANS.values()) cancelRequest(request);
            PENDING_SCANS.clear();
            SCAN_QUEUES.clear();
            DIMENSION_ORDER.clear();
        }
        for (GenerationRequest request : IN_FLIGHT) cancelRequest(request);
        IN_FLIGHT.clear();
        HazardClassifier.clear();
    }

    public static void onServerStarting() {
        acceptingWork = true;
        LIFECYCLE_GENERATION.incrementAndGet();
        FIELDS.clear();
        synchronized (SCAN_QUEUE_LOCK) {
            PENDING_SCANS.clear();
            SCAN_QUEUES.clear();
            DIMENSION_ORDER.clear();
        }
        for (GenerationRequest request : IN_FLIGHT) cancelRequest(request);
        IN_FLIGHT.clear();
        HazardClassifier.clear();
    }

    private static String getKey(Mob mob, int groupId, Vec3 target) {
        BlockPos pos = BlockPos.containing(target);
        return mob.level().dimension().identifier() + "|" + groupId + "|"
                + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static void triggerGeneration(String key, Mob mob, Vec3 target) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        GenerationRequest request = new GenerationRequest(
                key, mob, level, target, BlockPos.containing(target), level.dimension(),
                LIFECYCLE_GENERATION.get(), mob.getId());
        synchronized (SCAN_QUEUE_LOCK) {
            if (!acceptingWork || mob.isRemoved() || !mob.isAlive() || mob.level() != level
                    || PENDING_SCANS.size() >= MAX_PENDING_SCANS
                    || PENDING_SCANS.putIfAbsent(key, request) != null) return;
            enqueueScanLocked(new ScanJob(request));
        }
        cacheField(key, new FlowField(target, System.currentTimeMillis()));
    }

    private static void cacheField(String key, FlowField field) {
        synchronized (FIELD_CACHE_LOCK) {
            long now = System.currentTimeMillis();
            FIELDS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            if (!FIELDS.containsKey(key) && FIELDS.size() >= MAX_FIELDS) {
                Map.Entry<String, FlowField> oldest = FIELDS.entrySet().stream()
                        .min(Comparator.comparingLong(entry -> entry.getValue().createdAt()))
                        .orElse(null);
                if (oldest != null) FIELDS.remove(oldest.getKey(), oldest.getValue());
            }
            FIELDS.putIfAbsent(key, field);
        }
    }

    private static void drainScanBudget() {
        if (scanDrainPausedForTesting) {
            lastTickScannedPositions = 0;
            return;
        }
        LongSupplier clock = scanClock;
        long started = clock.getAsLong();
        int visited = 0;
        while (visited < MAX_SCAN_POSITIONS_PER_TICK
                && clock.getAsLong() - started < SCAN_BUDGET_NANOS) {
            ScanJob job = pollNextScan();
            if (job == null) {
                lastTickScannedPositions = visited;
                return;
            }
            GenerationRequest request = job.request;
            if (!isRequestCurrent(request)) {
                PENDING_SCANS.remove(request.key, request);
                continue;
            }
            try {
                visited += job.scanSlice(SCAN_SLICE_POSITIONS);
                if (job.isComplete()) {
                    submitFlowField(request, job.walkabilitySnapshot);
                } else {
                    requeueScan(job);
                }
            } catch (RuntimeException ignored) {
                PENDING_SCANS.remove(request.key, request);
            }
        }
        lastTickScannedPositions = visited;
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
            if (isRequestCurrent(job.request)) enqueueScanLocked(job);
            else PENDING_SCANS.remove(job.request.key, job.request);
        }
    }

    private static void enqueueScanLocked(ScanJob job) {
        ResourceKey<Level> dimension = job.request.dimension;
        ArrayDeque<ScanJob> queue = SCAN_QUEUES.computeIfAbsent(dimension, ignored -> new ArrayDeque<>());
        boolean wasEmpty = queue.isEmpty();
        queue.addLast(job);
        if (wasEmpty && !DIMENSION_ORDER.contains(dimension)) DIMENSION_ORDER.addLast(dimension);
    }

    private static void cancelObsoleteScans(ResourceKey<Level> dimension, int requesterId, String currentKey) {
        synchronized (SCAN_QUEUE_LOCK) {
            for (GenerationRequest request : PENDING_SCANS.values()) {
                if (request.dimension.equals(dimension) && request.requesterId == requesterId
                        && !request.key.equals(currentKey)) {
                    cancelRequest(request);
                    PENDING_SCANS.remove(request.key, request);
                }
            }
            Iterator<Map.Entry<ResourceKey<Level>, ArrayDeque<ScanJob>>> iterator = SCAN_QUEUES.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ResourceKey<Level>, ArrayDeque<ScanJob>> entry = iterator.next();
                if (entry.getKey().equals(dimension)) entry.getValue().removeIf(job -> job.request.cancelled);
                if (entry.getValue().isEmpty()) {
                    DIMENSION_ORDER.remove(entry.getKey());
                    iterator.remove();
                }
            }
        }
        for (GenerationRequest request : IN_FLIGHT) {
            if (request.dimension.equals(dimension) && request.requesterId == requesterId
                    && !request.key.equals(currentKey)) cancelRequest(request);
        }
    }

    private static void submitFlowField(GenerationRequest request, Map<Long, Boolean> snapshot) {
        if (!isRequestCurrent(request)) {
            PENDING_SCANS.remove(request.key, request);
            return;
        }
        IN_FLIGHT.add(request);
        CompletableFuture<Map<BlockPos, Vec3>> future = null;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            Duration timeout = Duration.ofMillis(Math.max(5L, budget == null ? 10L : budget.longValue()));
            future = QuantifiedOptionalBridge.trySubmitTask(
                    Soundattract.MOD_ID, "soundattract_flow_field",
                    () -> generateFlowField(snapshot, request.startNode),
                    true, false, timeout, request.key);
        } catch (RuntimeException ignored) {
        }
        if (future == null) {
            future = CompletableFuture.supplyAsync(() -> generateFlowField(snapshot, request.startNode), EXECUTOR);
        }
        request.future = future;
        future.whenComplete((vectors, failure) -> {
            try {
                if (failure == null && vectors != null && isRequestCurrent(request)) {
                    FlowField field = FIELDS.get(request.key);
                    if (field != null) field.update(vectors, System.currentTimeMillis());
                } else if (failure != null || vectors == null) {
                    FIELDS.remove(request.key);
                }
            } finally {
                PENDING_SCANS.remove(request.key, request);
                IN_FLIGHT.remove(request);
            }
        });
    }

    private static boolean isRequestCurrent(GenerationRequest request) {
        Mob mob = request.mob;
        return !request.cancelled && acceptingWork && request.generation == LIFECYCLE_GENERATION.get()
                && PENDING_SCANS.get(request.key) == request && mob != null && !mob.isRemoved()
                && mob.isAlive() && mob.level() == request.level
                && mob.level().dimension().equals(request.dimension);
    }

    private static void cancelRequest(GenerationRequest request) {
        request.cancelled = true;
        PENDING_SCANS.remove(request.key, request);
        IN_FLIGHT.remove(request);
        Future<?> future = request.future;
        if (future != null) future.cancel(false);
    }

    private static Map<BlockPos, Vec3> generateFlowField(Map<Long, Boolean> walkabilitySnapshot, BlockPos start) {
        Map<BlockPos, Integer> costs = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(node -> node.cost));
        costs.put(start, 0);
        open.add(new Node(start, 0));
        int processed = 0;
        while (!open.isEmpty() && processed < MAX_NODES) {
            Node current = open.poll();
            BlockPos pos = current.pos;
            if (costs.getOrDefault(pos, Integer.MAX_VALUE) < current.cost) continue;
            processed++;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (Math.abs(next.getX() - start.getX()) > FIELD_RADIUS
                        || Math.abs(next.getZ() - start.getZ()) > FIELD_RADIUS
                        || Math.abs(next.getY() - start.getY()) > 10) continue;
                if (!Boolean.TRUE.equals(walkabilitySnapshot.get(next.asLong()))) continue;
                int newCost = current.cost + 1;
                if (newCost < costs.getOrDefault(next, Integer.MAX_VALUE)) {
                    costs.put(next, newCost);
                    open.add(new Node(next, newCost));
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
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                Integer cost = costs.get(neighbor);
                if (cost != null && cost < minCost) {
                    minCost = cost;
                    bestNeighbor = neighbor;
                }
            }
            if (bestNeighbor != null) {
                Vec3 delta = Vec3.atCenterOf(bestNeighbor).subtract(Vec3.atCenterOf(pos)).normalize();
                flow.put(pos, delta);
            }
        }
        return flow;
    }

    private static final class ScanJob {
        private final GenerationRequest request;
        private final Map<Long, Boolean> walkabilitySnapshot = new HashMap<>();
        private int dx = -FIELD_RADIUS;
        private int dz = -FIELD_RADIUS;
        private int dy = -4;
        private boolean complete;

        private ScanJob(GenerationRequest request) {
            this.request = request;
        }

        private int scanSlice(int limit) {
            int scanned = 0;
            while (!complete && scanned < limit) {
                int currentX = dx;
                int currentZ = dz;
                int currentY = dy;
                advance();
                if (currentX * currentX + currentZ * currentZ > FIELD_RADIUS * FIELD_RADIUS) continue;
                BlockPos pos = request.startNode.offset(currentX, currentY, currentZ);
                boolean walkable = request.level.hasChunkAt(pos)
                        && NodeRouter.isWalkable(request.mob, Vec3.atBottomCenterOf(pos));
                walkabilitySnapshot.put(pos.asLong(), walkable);
                scanned++;
            }
            return scanned;
        }

        private void advance() {
            if (++dy > 4) {
                dy = -4;
                if (++dz > FIELD_RADIUS) {
                    dz = -FIELD_RADIUS;
                    if (++dx > FIELD_RADIUS) complete = true;
                }
            }
        }

        private boolean isComplete() {
            return complete;
        }
    }

    private static final class GenerationRequest {
        private final String key;
        private final Mob mob;
        private final Level level;
        private final Vec3 target;
        private final BlockPos startNode;
        private final ResourceKey<Level> dimension;
        private final long generation;
        private final int requesterId;
        private volatile boolean cancelled;
        private volatile Future<?> future;

        private GenerationRequest(String key, Mob mob, Level level, Vec3 target, BlockPos startNode,
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

    private record Node(BlockPos pos, int cost) {
    }

    private static final class FlowField {
        private final Vec3 target;
        private volatile Map<BlockPos, Vec3> vectors = Collections.emptyMap();
        private volatile long created;

        private FlowField(Vec3 target, long created) {
            this.target = target;
            this.created = created;
        }

        private void update(Map<BlockPos, Vec3> newVectors, long now) {
            vectors = Collections.unmodifiableMap(newVectors);
            created = now;
        }

        private boolean isExpired(long now) {
            return now - created > CACHE_TTL_MS;
        }

        private long createdAt() {
            return created;
        }

        private boolean isTargetMoved(Vec3 newTarget) {
            return newTarget.distanceToSqr(target) > 2.0;
        }

        private Vec3 getVector(BlockPos pos) {
            return vectors.get(pos);
        }
    }
}
