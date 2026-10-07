package com.example.soundattract.los;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.event.FovEvents;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.async.AsyncManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OptimizedLOS {


    private static final int MAX_BATCH_BUDGET_PER_TICK = 4096;

    private static final double TIE_EPS = 1.0e-12;
    private static final int MAX_BATCH_QUEUE = 4096;

    private static final AtomicInteger DEBUG_CANSEE_CALLS = new AtomicInteger(0);
    private static final AtomicInteger DEBUG_HASLOS_CALLS = new AtomicInteger(0);
    private static final AtomicInteger DEBUG_DDA_CALLS = new AtomicInteger(0);
    private static final AtomicLong DEBUG_LAST_LOG_GAME_TIME = new AtomicLong(-1L);

    private record LosPairKey(String dim, int lookerId, int targetId) {
    }

    private record LosPairEntry(boolean result, long gameTime) {
    }

    private static final Cache<LosPairKey, LosPairEntry> LOS_PAIR_CACHE = Caffeine.newBuilder()
            .maximumSize(65536)
            .expireAfterWrite(10, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private record LosRequest(Level level, Mob looker, Entity target, LosPairKey key) {
    }

    private static final Queue<LosRequest> LOS_QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<LosPairKey, CompletableFuture<Boolean>> LOS_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final AtomicInteger LOS_QUEUE_SIZE = new AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicBoolean ACCEPTING_REQUESTS = new java.util.concurrent.atomic.AtomicBoolean(true);
    private static volatile MinecraftServer activeServer;
    private static volatile MinecraftServer stoppedServer;

    private OptimizedLOS() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {



        List<CompletableFuture<Boolean>> pending;
        synchronized (LOS_QUEUE) {
            ACCEPTING_REQUESTS.set(false);
            stoppedServer = event == null ? activeServer : event.getServer();
            activeServer = null;
            LOS_QUEUE.clear();
            LOS_QUEUE_SIZE.set(0);
            pending = new ArrayList<>(LOS_IN_FLIGHT.values());
            LOS_IN_FLIGHT.clear();
        }
        for (CompletableFuture<Boolean> future : pending) {
            if (future != null) {
                future.complete(false);
            }
        }
        LOS_PAIR_CACHE.invalidateAll();
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
            debug = DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING);
        } catch (Throwable ignored) {
        }
        if (debug) {
            DEBUG_CANSEE_CALLS.incrementAndGet();
            long last = DEBUG_LAST_LOG_GAME_TIME.get();
            if (last < 0L || (now - last) >= 200L) {
                if (DEBUG_LAST_LOG_GAME_TIME.compareAndSet(last, now)) {
                    SoundAttractMod.LOGGER.info(
                            "[OptimizedLOS] canSee active. calls={} enableOptimized={} pairCache={} pairCacheSize={} queue={} inFlight={}",
                            Integer.valueOf(DEBUG_CANSEE_CALLS.get()),
                            Boolean.valueOf(enableOptimized),
                            Boolean.valueOf(enablePairCache),
                            Integer.valueOf(LOS_PAIR_CACHE.asMap().size()),
                            Integer.valueOf(LOS_QUEUE_SIZE.get()),
                            Integer.valueOf(LOS_IN_FLIGHT.size()));
                }
            }
        }

        LosPairKey key = new LosPairKey(dim, looker.getId(), target.getId());

        if (enablePairCache) {
            LosPairEntry existing = LOS_PAIR_CACHE.getIfPresent(key);
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
            int maxEntries = Math.max(512, pairCacheMax);
            if (LOS_PAIR_CACHE.asMap().size() > maxEntries) {
                LOS_PAIR_CACHE.asMap().entrySet().removeIf(e -> (now - e.getValue().gameTime()) > 1L);
                if (LOS_PAIR_CACHE.asMap().size() > maxEntries) {
                    int toRemove = LOS_PAIR_CACHE.asMap().size() - maxEntries;
                    java.util.Iterator<java.util.Map.Entry<LosPairKey, LosPairEntry>> it = LOS_PAIR_CACHE.asMap().entrySet().iterator();
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
        if (!ACCEPTING_REQUESTS.get()) {
            return CompletableFuture.completedFuture(false);
        }

        boolean enableBatching = false;
        int queueMax = 4096;
        try {
            enableBatching = SoundAttractConfig.COMMON.enableLosBatching.get();
            queueMax = Math.min(MAX_BATCH_QUEUE, SoundAttractConfig.COMMON.losBatchQueueMaxSize.get());
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

        if (LOS_IN_FLIGHT.size() >= Math.max(64, queueMax)) {
            return AsyncManager.callOnMain(() -> Boolean.valueOf(canSee(looker, target))).thenApply(Boolean::booleanValue);
        }

        if (LOS_QUEUE_SIZE.get() >= Math.max(64, queueMax)) {
            return AsyncManager.callOnMain(() -> Boolean.valueOf(canSee(looker, target))).thenApply(Boolean::booleanValue);
        }

        synchronized (LOS_QUEUE) {
            if (!ACCEPTING_REQUESTS.get()) {
                return CompletableFuture.completedFuture(false);
            }
            int boundedMax = Math.max(64, queueMax);
            CompletableFuture<Boolean> existingInLock = LOS_IN_FLIGHT.get(key);
            if (existingInLock != null) {
                return existingInLock;
            }
            if (LOS_QUEUE_SIZE.get() >= boundedMax || LOS_IN_FLIGHT.size() >= boundedMax) {
                return AsyncManager.callOnMain(() -> Boolean.valueOf(canSee(looker, target))).thenApply(Boolean::booleanValue);
            }
            CompletableFuture<Boolean> created = new CompletableFuture<>();
            LOS_IN_FLIGHT.put(key, created);
            LOS_QUEUE.add(new LosRequest(level, looker, target, key));
            LOS_QUEUE_SIZE.incrementAndGet();
            return created;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer tickServer = event == null ? null : event.getServer();
        if (tickServer == null) return;
        MinecraftServer knownServer = activeServer;
        if (stoppedServer == tickServer || (knownServer != null && knownServer != tickServer)) {
            return;
        }
        activeServer = tickServer;
        stoppedServer = null;
        ACCEPTING_REQUESTS.set(true);
        if (event.phase != TickEvent.Phase.END) return;
        resetMufflingBudget();
        if (LOS_QUEUE.isEmpty()) return;

        int budget = 64;
        try {
            budget = SoundAttractConfig.COMMON.losBatchBudgetPerTick.get();
        } catch (Throwable ignored) {
        }
        budget = Math.min(MAX_BATCH_BUDGET_PER_TICK, Math.max(1, budget));

        for (int i = 0; i < budget; i++) {
            LosRequest req = LOS_QUEUE.poll();
            if (req == null) {
                break;
            }
            LOS_QUEUE_SIZE.decrementAndGet();

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

    public static boolean hasLineOfSight(Level level, Vec3 start, Vec3 end, Mob looker) {
        if (level == null || start == null || end == null) {
            return false;
        }

        boolean debug = false;
        try {
            debug = DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING);
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

    private static boolean hasLineOfSightDda(Level level, Vec3 start, Vec3 end, Mob looker) {

        try {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
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

    private static boolean hasLineOfSightVanillaIgnoringNonBlocking(Level level, Vec3 start, Vec3 end, Mob looker) {
        if (level == null || start == null || end == null) {
            return false;
        }

        Vec3 currentStart = start;
        for (int i = 0; i < 64; i++) {
            if (currentStart.distanceToSqr(end) < 1.0e-8) {
                return true;
            }

            BlockHitResult hit = level.clip(new ClipContext(currentStart, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    looker));
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
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                if (id != null && id.getNamespace().equals("spore")) {
                    if (x == endX && y == endY && z == endZ) break;
                    continue;
                }
            }

            if (state.isAir()) {
                if (factorAir >= 1.0) continue;
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