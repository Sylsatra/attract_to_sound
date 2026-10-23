package com.example.soundattract.los;

import com.example.soundattract.event.FovEvents;
import com.example.soundattract.Soundattract;
import com.example.soundattract.async.AsyncManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OptimizedLOS {

    private static final double TIE_EPS = 1.0e-12;
    private static final int MAX_RUNTIME_LOS_QUEUE_SIZE = 16_384;
    private static final int MAX_RUNTIME_PAIR_CACHE_SIZE = 65_536;

    private static final AtomicInteger DEBUG_CANSEE_CALLS = new AtomicInteger(0);
    private static final AtomicInteger DEBUG_HASLOS_CALLS = new AtomicInteger(0);
    private static final AtomicInteger DEBUG_DDA_CALLS = new AtomicInteger(0);
    private static final AtomicLong DEBUG_LAST_LOG_GAME_TIME = new AtomicLong(-1L);

    private record LosPairKey(String dim, int lookerId, int targetId) {
    }

    private record LosPairEntry(boolean result, long gameTime) {
    }

    private static final ConcurrentHashMap<LosPairKey, LosPairEntry> LOS_PAIR_CACHE = new ConcurrentHashMap<>();

    private record LosRequest(Level level, Mob looker, Entity target, LosPairKey key) {
    }

    private static final ArrayDeque<LosRequest> LOS_QUEUE = new ArrayDeque<>();
    private static final ConcurrentHashMap<LosPairKey, CompletableFuture<Boolean>> LOS_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Object LOS_QUEUE_LOCK = new Object();
    private static int losQueueSize;
    private static volatile boolean acceptingRequests = true;

    private OptimizedLOS() {
    }

    public static boolean canSee(Mob looker, Entity target) {
        if (looker == null || target == null) {
            return false;
        }
        Level level = looker.level();
        if (level == null || level != target.level()) {
            return false;
        }

        boolean enableOptimized = true;
        boolean enablePairCache = true;
        int pairCacheMax = 8192;
        try {
            enableOptimized = SoundAttractConfig.COMMON.enableOptimizedLos.get();
            enablePairCache = SoundAttractConfig.COMMON.enableOptimizedLosPairCache.get();
            pairCacheMax = SoundAttractConfig.COMMON.optimizedLosPairCacheMaxEntries.get();
        } catch (Throwable ignored) {
        }

        final long now = level.getGameTime();
        final String dim = level.dimension().location().toString();

        boolean debug = false;
        try {
            debug = SoundAttractConfig.COMMON.debugLogging.get();
        } catch (Throwable ignored) {
        }
        if (debug) {
            DEBUG_CANSEE_CALLS.incrementAndGet();
            long last = DEBUG_LAST_LOG_GAME_TIME.get();
            if (last < 0L || (now - last) >= 200L) {
                if (DEBUG_LAST_LOG_GAME_TIME.compareAndSet(last, now)) {
                    Soundattract.LOGGER.info(
                            "[OptimizedLOS] canSee active. calls={} enableOptimized={} pairCache={} pairCacheSize={} queue={} inFlight={}",
                            Integer.valueOf(DEBUG_CANSEE_CALLS.get()),
                            Boolean.valueOf(enableOptimized),
                            Boolean.valueOf(enablePairCache),
                            Integer.valueOf(LOS_PAIR_CACHE.size()),
                            Integer.valueOf(getQueuedRequestCount()),
                            Integer.valueOf(LOS_IN_FLIGHT.size()));
                }
            }
        }

        LosPairKey key = new LosPairKey(dim, looker.getId(), target.getId());

        if (enablePairCache) {
            LosPairEntry existing = LOS_PAIR_CACHE.get(key);
            if (existing != null && (now - existing.gameTime()) <= 1L) {
                return existing.result();
            }
        }

        Vec3 start = looker.getEyePosition();
        Vec3 eyeToEye = target.getEyePosition();
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 feet = target.position().add(0, Math.max(0.1, target.getBbHeight() * 0.15), 0);

        boolean result;
        if (enableOptimized) {
            result = hasLineOfSight(level, start, eyeToEye, looker)
                    || hasLineOfSight(level, start, center, looker)
                    || hasLineOfSight(level, start, feet, looker);
        } else {
            result = hasLineOfSightVanillaIgnoringNonBlocking(level, start, eyeToEye, looker)
                    || hasLineOfSightVanillaIgnoringNonBlocking(level, start, center, looker)
                    || hasLineOfSightVanillaIgnoringNonBlocking(level, start, feet, looker);
        }

        if (enablePairCache) {
            LOS_PAIR_CACHE.put(key, new LosPairEntry(result, now));
            int maxEntries = Math.min(MAX_RUNTIME_PAIR_CACHE_SIZE, Math.max(512, pairCacheMax));
            if (LOS_PAIR_CACHE.size() > maxEntries) {
                LOS_PAIR_CACHE.entrySet().removeIf(e -> (now - e.getValue().gameTime()) > 1L);
                if (LOS_PAIR_CACHE.size() > maxEntries) {
                    int toRemove = LOS_PAIR_CACHE.size() - maxEntries;
                    java.util.Iterator<java.util.Map.Entry<LosPairKey, LosPairEntry>> it = LOS_PAIR_CACHE.entrySet().iterator();
                    for (int i = 0; i < toRemove && it.hasNext(); i++) {
                        it.next();
                        it.remove();
                    }
                }
            }
        }

        return result;
    }

    public static CompletableFuture<Boolean> canSeeAsync(Mob looker, Entity target) {
        if (looker == null || target == null) {
            return CompletableFuture.completedFuture(false);
        }

        Level level = looker.level();
        if (level == null || level.isClientSide() || level != target.level()) {
            return CompletableFuture.completedFuture(false);
        }

        boolean enableBatching = false;
        int queueMax = 4096;
        try {
            enableBatching = SoundAttractConfig.COMMON.enableLosBatching.get();
            queueMax = SoundAttractConfig.COMMON.losBatchQueueMaxSize.get();
        } catch (Throwable ignored) {
        }

        if (!enableBatching) {
            return AsyncManager.callOnMain(() -> Boolean.valueOf(canSee(looker, target))).thenApply(Boolean::booleanValue);
        }

        final String dim = level.dimension().location().toString();
        LosPairKey key = new LosPairKey(dim, looker.getId(), target.getId());

        CompletableFuture<Boolean> existing = LOS_IN_FLIGHT.get(key);
        if (existing != null) {
            return existing;
        }

        if (!acceptingRequests) {
            return CompletableFuture.completedFuture(false);
        }

        int queueCapacity = Math.min(MAX_RUNTIME_LOS_QUEUE_SIZE, Math.max(64, queueMax));
        if (LOS_IN_FLIGHT.size() >= queueCapacity) {
            return AsyncManager.callOnMain(() -> Boolean.valueOf(canSee(looker, target))).thenApply(Boolean::booleanValue);
        }

        CompletableFuture<Boolean> created = new CompletableFuture<>();
        synchronized (LOS_QUEUE_LOCK) {
            if (!acceptingRequests) return CompletableFuture.completedFuture(false);
            CompletableFuture<Boolean> prior = LOS_IN_FLIGHT.get(key);
            if (prior != null) return prior;
            if (losQueueSize >= queueCapacity) {
                return AsyncManager.callOnMain(() -> Boolean.valueOf(canSee(looker, target))).thenApply(Boolean::booleanValue);
            }
            LOS_IN_FLIGHT.put(key, created);
            LOS_QUEUE.addLast(new LosRequest(level, looker, target, key));
            losQueueSize++;
        }
        return created;
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null || !acceptingRequests) return;
        resetMufflingBudget();
        if (getQueuedRequestCount() == 0) return;

        int budget = 64;
        try {
            budget = SoundAttractConfig.COMMON.losBatchBudgetPerTick.get();
        } catch (Throwable ignored) {
        }
        budget = Math.max(1, budget);

        for (int i = 0; i < budget; i++) {
            LosRequest req;
            synchronized (LOS_QUEUE_LOCK) {
                req = LOS_QUEUE.pollFirst();
                if (req != null) losQueueSize--;
            }
            if (req == null) {
                break;
            }

            CompletableFuture<Boolean> future = LOS_IN_FLIGHT.remove(req.key());
            if (future == null || future.isDone()) {
                continue;
            }

            boolean result = false;
            try {
                Level level = req.level();
                Mob looker = req.looker();
                Entity target = req.target();
                if (level != null && looker != null && target != null
                        && !level.isClientSide()
                        && looker.level() == level
                        && target.level() == level
                        && !looker.isRemoved()
                        && !target.isRemoved()) {
                    result = canSee(looker, target);
                }
            } catch (Throwable t) {
                result = false;
            }

            future.complete(result);
        }
    }

    public static void onServerStarting() {
        synchronized (LOS_QUEUE_LOCK) {
            acceptingRequests = false;
            for (CompletableFuture<Boolean> future : LOS_IN_FLIGHT.values()) {
                future.complete(false);
            }
            losQueueSize = 0;
            LOS_QUEUE.clear();
            LOS_PAIR_CACHE.clear();
            LOS_IN_FLIGHT.clear();
            acceptingRequests = true;
        }
    }

    public static void onServerStopped() {
        synchronized (LOS_QUEUE_LOCK) {
            acceptingRequests = false;
            LOS_QUEUE.clear();
            losQueueSize = 0;
            LOS_PAIR_CACHE.clear();
            for (CompletableFuture<Boolean> future : LOS_IN_FLIGHT.values()) {
                future.complete(false);
            }
            LOS_IN_FLIGHT.clear();
        }
    }

    private static int getQueuedRequestCount() {
        synchronized (LOS_QUEUE_LOCK) {
            return losQueueSize;
        }
    }

    public static boolean hasLineOfSight(Level level, Vec3 start, Vec3 end, LivingEntity looker) {
        if (level == null || start == null || end == null) {
            return false;
        }

        boolean debug = false;
        try {
            debug = SoundAttractConfig.COMMON.debugLogging.get();
        } catch (Throwable ignored) {
        }
        if (debug) {
            DEBUG_HASLOS_CALLS.incrementAndGet();
        }

        if (!Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)
                || !Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z)) {
            return false;
        }

        BlockPos startPos = BlockPos.containing(start);
        BlockPos endPos = BlockPos.containing(end);
        if (!level.isLoaded(startPos) || !level.isLoaded(endPos)) {
            return false;
        }

        boolean enableOptimized = true;
        boolean enableFallback = true;
        try {
            enableOptimized = SoundAttractConfig.COMMON.enableOptimizedLos.get();
            enableFallback = SoundAttractConfig.COMMON.enableOptimizedLosVanillaFallback.get();
        } catch (Throwable ignored) {
        }

        if (!enableOptimized) {
            return hasLineOfSightVanillaIgnoringNonBlocking(level, start, end, looker);
        }

        try {
            return hasLineOfSightDda(level, start, end, looker);
        } catch (Throwable t) {
            if (!enableFallback) {
                return false;
            }
            return hasLineOfSightVanillaIgnoringNonBlocking(level, start, end, looker);
        }
    }

    private static boolean hasLineOfSightDda(Level level, Vec3 start, Vec3 end, LivingEntity looker) {

        try {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                DEBUG_DDA_CALLS.incrementAndGet();
            }
        } catch (Throwable ignored) {
        }

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double distSqr = dx * dx + dy * dy + dz * dz;
        if (distSqr < 1.0e-8) {
            return true;
        }

        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);

        int endX = Mth.floor(end.x);
        int endY = Mth.floor(end.y);
        int endZ = Mth.floor(end.z);

        if (x == endX && y == endY && z == endZ) {
            return true;
        }

        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

        double invDx = stepX == 0 ? Double.POSITIVE_INFINITY : (1.0 / Math.abs(dx));
        double invDy = stepY == 0 ? Double.POSITIVE_INFINITY : (1.0 / Math.abs(dy));
        double invDz = stepZ == 0 ? Double.POSITIVE_INFINITY : (1.0 / Math.abs(dz));

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
                : ((stepX > 0 ? (x + 1.0) - start.x : start.x - x) * invDx);
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
                : ((stepY > 0 ? (y + 1.0) - start.y : start.y - y) * invDy);
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : ((stepZ > 0 ? (z + 1.0) - start.z : start.z - z) * invDz);

        double tDeltaX = invDx;
        double tDeltaY = invDy;
        double tDeltaZ = invDz;

        int maxSteps = 4 + Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        CollisionContext ctx = looker == null ? CollisionContext.empty() : CollisionContext.of(looker);

        for (int steps = 0; steps < maxSteps; steps++) {
            double tMin = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

            boolean stepAxisX = (tMaxX - tMin) <= TIE_EPS;
            boolean stepAxisY = (tMaxY - tMin) <= TIE_EPS;
            boolean stepAxisZ = (tMaxZ - tMin) <= TIE_EPS;

            if (stepAxisX) {
                x += stepX;
                tMaxX += tDeltaX;
            }
            if (stepAxisY) {
                y += stepY;
                tMaxY += tDeltaY;
            }
            if (stepAxisZ) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }

            pos.set(x, y, z);
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!FovEvents.isNonBlockingVisionForLos(state, level, pos)) {
                if (state.isCollisionShapeFullBlock(level, pos)) {
                    return false;
                }

                VoxelShape shape = state.getCollisionShape(level, pos, ctx);
                if (!shape.isEmpty()) {
                    for (AABB part : shape.toAabbs()) {
                        if (segmentIntersectsAabb(start, end, part.move(pos))) {
                            return false;
                        }
                    }
                }
            }

            if (x == endX && y == endY && z == endZ) {
                return true;
            }
        }

        boolean enableFallback = true;
        try {
            enableFallback = SoundAttractConfig.COMMON.enableOptimizedLosVanillaFallback.get();
        } catch (Throwable ignored) {
        }
        if (!enableFallback) {
            return false;
        }
        return hasLineOfSightVanillaIgnoringNonBlocking(level, start, end, looker);
    }

    private static BlockHitResult clip(Level level, Vec3 start, Vec3 end, LivingEntity looker) {
        if (looker != null) {
            return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker));
        }
        return BlockGetter.traverseBlocks(start, end, null, (ignored, pos) -> {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
            return shape.clip(start, end, pos);
        }, (ignored) -> {
            Vec3 diff = start.subtract(end);
            return BlockHitResult.miss(end, Direction.getNearest(diff.x, diff.y, diff.z), BlockPos.containing(end));
        });
    }

    private static boolean hasLineOfSightVanillaIgnoringNonBlocking(Level level, Vec3 start, Vec3 end, LivingEntity looker) {
        if (level == null || start == null || end == null) {
            return false;
        }

        Vec3 currentStart = start;
        for (int i = 0; i < 64; i++) {
            if (currentStart.distanceToSqr(end) < 1.0e-8) {
                return true;
            }

            BlockHitResult hit = clip(level, currentStart, end, looker);
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }

            BlockPos hitPos = hit.getBlockPos();
            if (!level.isLoaded(hitPos)) {
                return false;
            }

            BlockState state = level.getBlockState(hitPos);
            if (!FovEvents.isNonBlockingVisionForLos(state, level, hitPos)) {
                return false;
            }

            Vec3 loc = hit.getLocation();
            Vec3 dir = end.subtract(currentStart);
            double len = dir.length();
            if (len < 1.0e-8) {
                return true;
            }
            Vec3 n = dir.scale(1.0 / len);
            currentStart = loc.add(n.scale(1.0e-3));
        }

        return true;
    }

    private static boolean segmentIntersectsAabb(Vec3 start, Vec3 end, AABB box) {
        double t0 = 0.0;
        double t1 = 1.0;

        double dx = end.x - start.x;
        if (Math.abs(dx) < 1.0e-12) {
            if (start.x < box.minX || start.x > box.maxX) {
                return false;
            }
        } else {
            double inv = 1.0 / dx;
            double tNear = (box.minX - start.x) * inv;
            double tFar = (box.maxX - start.x) * inv;
            if (tNear > tFar) {
                double tmp = tNear;
                tNear = tFar;
                tFar = tmp;
            }
            t0 = Math.max(t0, tNear);
            t1 = Math.min(t1, tFar);
            if (t1 < t0) {
                return false;
            }
        }

        double dy = end.y - start.y;
        if (Math.abs(dy) < 1.0e-12) {
            if (start.y < box.minY || start.y > box.maxY) {
                return false;
            }
        } else {
            double inv = 1.0 / dy;
            double tNear = (box.minY - start.y) * inv;
            double tFar = (box.maxY - start.y) * inv;
            if (tNear > tFar) {
                double tmp = tNear;
                tNear = tFar;
                tFar = tmp;
            }
            t0 = Math.max(t0, tNear);
            t1 = Math.min(t1, tFar);
            if (t1 < t0) {
                return false;
            }
        }

        double dz = end.z - start.z;
        if (Math.abs(dz) < 1.0e-12) {
            if (start.z < box.minZ || start.z > box.maxZ) {
                return false;
            }
        } else {
            double inv = 1.0 / dz;
            double tNear = (box.minZ - start.z) * inv;
            double tFar = (box.maxZ - start.z) * inv;
            if (tNear > tFar) {
                double tmp = tNear;
                tNear = tFar;
                tFar = tmp;
            }
            t0 = Math.max(t0, tNear);
            t1 = Math.min(t1, tFar);
            if (t1 < t0) {
                return false;
            }
        }

        return true;
    }

    private static final AtomicInteger MUFFLING_RAYCASTS_THIS_TICK = new AtomicInteger(0);

    public static void resetMufflingBudget() {
        MUFFLING_RAYCASTS_THIS_TICK.set(0);
    }

    public static boolean tryConsumeMufflingBudget() {
        int max = 0;
        try {
            max = SoundAttractConfig.COMMON.maxMufflingRaycastsPerTick.get();
        } catch (Throwable ignored) {}
        if (max <= 0) return true; 
        return MUFFLING_RAYCASTS_THIS_TICK.incrementAndGet() <= max;
    }

    public static double[] computeMufflingDda(Level level, BlockPos src, BlockPos dst,
                                               double origRange, double origWeight, boolean skipBiomassMuffle) {
        if (level == null || src == null || dst == null) {
            return new double[]{origRange, origWeight};
        }

        Vec3 start = Vec3.atCenterOf(src);
        Vec3 end   = Vec3.atCenterOf(dst);

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double distSqr = dx * dx + dy * dy + dz * dz;
        if (distSqr < 1.0e-8) {
            return new double[]{origRange, origWeight};
        }

        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);

        int endX = Mth.floor(end.x);
        int endY = Mth.floor(end.y);
        int endZ = Mth.floor(end.z);

        if (x == endX && y == endY && z == endZ) {
            return new double[]{origRange, origWeight};
        }

        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

        double invDx = stepX == 0 ? Double.POSITIVE_INFINITY : (1.0 / Math.abs(dx));
        double invDy = stepY == 0 ? Double.POSITIVE_INFINITY : (1.0 / Math.abs(dy));
        double invDz = stepZ == 0 ? Double.POSITIVE_INFINITY : (1.0 / Math.abs(dz));

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
                : ((stepX > 0 ? (x + 1.0) - start.x : start.x - x) * invDx);
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
                : ((stepY > 0 ? (y + 1.0) - start.y : start.y - y) * invDy);
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : ((stepZ > 0 ? (z + 1.0) - start.z : start.z - z) * invDz);

        double tDeltaX = invDx;
        double tDeltaY = invDy;
        double tDeltaZ = invDz;

        int maxSteps = 4 + Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
        int maxBlocksToCheck = 32;
        try {
            maxBlocksToCheck = SoundAttractConfig.COMMON.maxMufflingBlocksToCheck.get();
        } catch (Throwable ignored) {}

        double currentRange  = origRange;
        double currentWeight = origWeight;
        int blocksHit = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        double factorWool, factorSolid, factorNonSolid, factorThin, factorLiquid, factorAir;
        try {
            factorWool     = SoundAttractConfig.COMMON.mufflingFactorWool.get();
            factorSolid    = SoundAttractConfig.COMMON.mufflingFactorSolid.get();
            factorNonSolid = SoundAttractConfig.COMMON.mufflingFactorNonSolid.get();
            factorThin     = SoundAttractConfig.COMMON.mufflingFactorThin.get();
            factorLiquid   = SoundAttractConfig.COMMON.mufflingFactorLiquid.get();
            factorAir      = SoundAttractConfig.COMMON.mufflingFactorAir.get();
        } catch (Throwable ignored) {
            factorWool = 0.15; factorSolid = 0.35; factorNonSolid = 0.7;
            factorThin = 0.9;  factorLiquid = 0.5; factorAir = 1.0;
        }

        for (int steps = 0; steps < maxSteps && blocksHit < maxBlocksToCheck
                && currentRange > 0.1 && currentWeight > 0.01; steps++) {
            double tMin = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

            boolean stepAxisX = (tMaxX - tMin) <= TIE_EPS;
            boolean stepAxisY = (tMaxY - tMin) <= TIE_EPS;
            boolean stepAxisZ = (tMaxZ - tMin) <= TIE_EPS;

            if (stepAxisX) { x += stepX; tMaxX += tDeltaX; }
            if (stepAxisY) { y += stepY; tMaxY += tDeltaY; }
            if (stepAxisZ) { z += stepZ; tMaxZ += tDeltaZ; }

            pos.set(x, y, z);
            if (!level.isLoaded(pos)) break;

            BlockState state = level.getBlockState(pos);
            if (skipBiomassMuffle) {
                if (x == endX && y == endY && z == endZ) break;
                continue;
            }

            if (state.isAir()) {
                if (factorAir >= 1.0) {
                    if (x == endX && y == endY && z == endZ) break;
                    continue;
                }
                currentRange  *= factorAir;
                currentWeight *= factorAir;
                blocksHit++;
                if (x == endX && y == endY && z == endZ) break;
                continue;
            }

            net.minecraft.world.level.block.Block block = state.getBlock();
            double factor = 1.0;
            if (SoundTracker.isCustomWool(state, block, level, pos)) {
                factor = factorWool;
            } else if (SoundTracker.isCustomLiquid(state, block, level, pos)) {
                factor = factorLiquid;
            } else if (SoundTracker.isCustomThin(state, block, level, pos)) {
                factor = factorThin;
            } else if (SoundTracker.isCustomSolid(state, block, level, pos)) {
                factor = factorSolid;
            } else if (SoundTracker.isCustomNonSolid(state, block, level, pos)) {
                factor = factorNonSolid;
            }
            if (factor < 1.0) {
                currentRange  *= factor;
                currentWeight *= factor;
                blocksHit++;
            }

            if (x == endX && y == endY && z == endZ) break;
        }

        return new double[]{Math.max(0, currentRange), Math.max(0, currentWeight)};
    }
}
