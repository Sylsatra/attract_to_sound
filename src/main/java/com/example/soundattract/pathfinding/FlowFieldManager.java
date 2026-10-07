package com.example.soundattract.pathfinding;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;
import org.admany.quantified.api.compute.QuantifiedCompute;
import org.admany.quantified.api.compute.QuantifiedCompute.Complexity;
import org.admany.quantified.api.compute.QuantifiedCompute.WorkloadKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlowFieldManager {

    private static final Map<String, FlowField> FIELDS = new ConcurrentHashMap<>();
    private static final int FIELD_RADIUS = 32;
    private static final int MAX_NODES = 2000;
    private static final long CACHE_TTL_MS = 2000;
    private static final int MAX_FIELDS = 16;
    private static final Set<CompletableFuture<?>> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicLong LIFECYCLE_GENERATION =
            new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean acceptingWork = true;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Set<CompletableFuture<?>> toCancel;
        synchronized (FIELDS) {
            acceptingWork = true;
            LIFECYCLE_GENERATION.incrementAndGet();
            FIELDS.clear();
            toCancel = Set.copyOf(IN_FLIGHT);
            IN_FLIGHT.clear();
        }
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
            FIELDS.clear();
            toCancel = Set.copyOf(IN_FLIGHT);
            IN_FLIGHT.clear();
        }
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
        FlowField field = FIELDS.get(key);

        long now = System.currentTimeMillis();

        if (field == null || field.isExpired(now) || field.isTargetMoved(target)) {
            if (field == null || !field.generating.get()) {
                triggerGeneration(key, mob, target, now);
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

    private static void triggerGeneration(String key, Mob mob, Vec3 target, long now) {
        FlowField field;
        long generation;
        synchronized (FIELDS) {
            if (!acceptingWork) return;
            generation = LIFECYCLE_GENERATION.get();
            FlowField existing = FIELDS.get(key);
            if (existing != null && existing.generating.get()) return;

            if (existing == null && FIELDS.size() >= MAX_FIELDS) {
                FIELDS.entrySet().removeIf(e -> e.getValue().isExpired(now));
                if (FIELDS.size() >= MAX_FIELDS) {
                    FIELDS.entrySet().stream()
                            .min(Comparator.comparingLong(e -> e.getValue().created))
                            .map(Map.Entry::getKey)
                            .ifPresent(FIELDS::remove);
                }
            }




            field = new FlowField(target, now);
            field.generating.set(true);
            FIELDS.put(key, field);
        }

        Level level = mob.level();
        if (level == null) {
            FIELDS.remove(key, field);
            field.generating.set(false);
            return;
        }
        BlockPos startNode = new BlockPos((int) target.x, (int) target.y, (int) target.z);

        Map<Long, Boolean> walkabilitySnapshot;
        try {
            walkabilitySnapshot = snapshotWalkability(level, mob, startNode);
        } catch (RuntimeException ex) {
            FIELDS.remove(key, field);
            field.generating.set(false);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
                SoundAttractMod.LOGGER.debug("[FlowField] Snapshot rejected for {}", key, ex);
            }
            return;
        }

        CompletableFuture<Map<BlockPos, Vec3>> future;
        try {
            synchronized (FIELDS) {
                if (!acceptingWork || generation != LIFECYCLE_GENERATION.get() || FIELDS.get(key) != field) {
                    FIELDS.remove(key, field);
                    field.generating.set(false);
                    return;
                }
            }
            QuantifiedCompute.Builder<Map<BlockPos, Vec3>> builder = QuantifiedCompute.<Map<BlockPos, Vec3>>builder(
                        SoundAttractMod.MOD_ID, "flow_field", System.nanoTime())
                    .cpuFallback(() -> generateFlowField(walkabilitySnapshot, startNode))
                    .dataSizeBytes(Math.max(4096L, walkabilitySnapshot.size() * 9L))
                    .parallelUnits(1)
                    .complexity(Complexity.COMPLEX)
                    .kind(WorkloadKind.SPATIAL_ANALYSIS)
                    .cpuOnly()
                    .allowMainThreadRerouting(false);
            try {
                Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
                if (budget != null) {
                    builder.timeout(Duration.ofMillis(Math.max(5L, budget.longValue())));
                }
            } catch (Throwable ignored) {

            }
            future = builder.submit();
        } catch (RuntimeException ex) {
            FIELDS.remove(key, field);
            field.generating.set(false);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
                SoundAttractMod.LOGGER.debug("[FlowField] Quantified rejected field {}", key, ex);
            }
            return;
        }
        if (future == null) {
            FIELDS.remove(key, field);
            field.generating.set(false);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
                SoundAttractMod.LOGGER.debug("[FlowField] Quantified returned no future for {}", key);
            }
            return;
        }
        synchronized (FIELDS) {
            if (!acceptingWork || generation != LIFECYCLE_GENERATION.get() || FIELDS.get(key) != field) {
                future.cancel(false);
                field.generating.set(false);
                return;
            }
            IN_FLIGHT.add(future);
        }
        future.whenComplete((vectors, throwable) -> {
            try {
                if (throwable == null && vectors != null && acceptingWork
                        && generation == LIFECYCLE_GENERATION.get() && FIELDS.get(key) == field) {
                    field.update(vectors, System.currentTimeMillis());
                }
            } finally {
                IN_FLIGHT.remove(future);
                field.generating.set(false);
            }
        });
    }

    private static Map<Long, Boolean> snapshotWalkability(Level level, Mob mob, BlockPos start) {
        Map<Long, Boolean> snapshot = new HashMap<>();
        int radius = FIELD_RADIUS;
        int radiusSqr = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) continue;
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = start.offset(dx, dy, dz);
                    long k = pos.asLong();
                    if (!level.hasChunkAt(pos)) {
                        snapshot.put(k, false);
                        continue;
                    }
                    boolean walkable = NodeRouter.isWalkable(mob, Vec3.atBottomCenterOf(pos));
                    snapshot.put(k, walkable);
                }
            }
        }
        return snapshot;
    }

    private static Map<BlockPos, Vec3> generateFlowField(Map<Long, Boolean> walkabilitySnapshot, BlockPos start) {
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

                Boolean walkable = walkabilitySnapshot.get(nPos.asLong());
                if (walkable == null || !walkable) continue;

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

    private static class Node {
        BlockPos pos;
        int cost;
        Node(BlockPos p, int c) { this.pos = p; this.cost = c; }
    }

    private static class FlowField {
        final Vec3 target;
        volatile Map<BlockPos, Vec3> vectors = new ConcurrentHashMap<>();
        volatile long created;
        AtomicBoolean generating = new AtomicBoolean(false);

        FlowField(Vec3 target, long now) {
            this.target = target;
            this.created = now;
        }

        void update(Map<BlockPos, Vec3> newVectors, long now) {
            this.vectors = Collections.unmodifiableMap(newVectors);
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