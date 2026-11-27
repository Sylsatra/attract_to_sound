package com.example.soundattract.ai;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.example.soundattract.config.separate.LivingWallConfig;

@GameTestHolder("soundattract_targetscanperf")
@PrefixGameTestTemplate(false)
public final class TargetScanLandingCacheGameTest {
    @GameTest(template = "targetscanperf.empty", batch = "targetLandingCache", timeoutTicks = 100, skyAccess = true)
    public void candidateValidationCacheMatchesDirectChecksAndStaysBounded(GameTestHelper helper) throws Exception {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
            }
        }
        var level = helper.getLevel();
        BlockPos pivot = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos safeLanding = helper.absolutePos(new BlockPos(5, 1, 2));
        BlockPos flatLanding = helper.absolutePos(new BlockPos(5, 1, 2));
        BlockPos raisedLanding = helper.absolutePos(new BlockPos(7, 4, 7));
        BlockPos unsupportedLanding = helper.absolutePos(new BlockPos(9, 8, 9));
        helper.setBlock(new BlockPos(7, 3, 7), Blocks.STONE.defaultBlockState());

        int regionRadius = Math.max(1, LivingWallConfig.MAX_GAP.get() + 1) + 8;
        int minChunkX = Math.floorDiv(pivot.getX() - regionRadius, 16);
        int maxChunkX = Math.floorDiv(pivot.getX() + regionRadius, 16);
        int minChunkZ = Math.floorDiv(pivot.getZ() - regionRadius, 16);
        int maxChunkZ = Math.floorDiv(pivot.getZ() + regionRadius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }

        Class<?> cacheType = Class.forName(LivingWallCoordinator.class.getName() + "$TargetLandingValidationCache");
        Constructor<?> constructor = cacheType.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        Object cache = constructor.newInstance(true);
        Method isLandingSurface = cacheType.getDeclaredMethod("isLandingSurface", ServerLevel.class, BlockPos.class, BlockPos.class);
        Method hasBridgeNeed = cacheType.getDeclaredMethod("hasBridgeNeed", ServerLevel.class, BlockPos.class, BlockPos.class);
        Method resolveCanonicalLanding = cacheType.getDeclaredMethod(
                "resolveCanonicalLanding", ServerLevel.class, BlockPos.class, BlockPos.class, Vec3.class);
        Method hits = cacheType.getDeclaredMethod("hits");
        Method size = cacheType.getDeclaredMethod("size");
        Method canonicalHits = cacheType.getDeclaredMethod("canonicalHits");
        Method canonicalSize = cacheType.getDeclaredMethod("canonicalSize");
        Field enabled = cacheType.getDeclaredField("enabled");
        isLandingSurface.setAccessible(true);
        hasBridgeNeed.setAccessible(true);
        resolveCanonicalLanding.setAccessible(true);
        hits.setAccessible(true);
        size.setAccessible(true);
        canonicalHits.setAccessible(true);
        canonicalSize.setAccessible(true);
        enabled.setAccessible(true);

        boolean directSafe = invokeBoolean("isSafeLanding", new Class<?>[]{ServerLevel.class, BlockPos.class}, level, safeLanding);
        boolean directSafeSurface = directSafe || invokeBoolean("isInvasionEndpoint",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class}, level, pivot, safeLanding);
        int hitsBeforeSafe = (int) hits.invoke(cache);
        boolean cachedSafeFirst = (boolean) isLandingSurface.invoke(cache, level, pivot, safeLanding);
        boolean cachedSafeAgain = (boolean) isLandingSurface.invoke(cache, level, pivot, safeLanding);
        require(directSafeSurface && cachedSafeFirst && cachedSafeAgain,
                "safe surface result changed through the cache: direct=" + directSafeSurface + ", first="
                        + cachedSafeFirst + ", again=" + cachedSafeAgain + ", support="
                        + level.getBlockState(safeLanding.below()) + ", feet=" + level.getBlockState(safeLanding));
        require(enabled.getBoolean(cache), "production chunk-readiness gate did not activate for loaded terrain");
        require((int) hits.invoke(cache) - hitsBeforeSafe == 1, "safe surface was not reused on the repeat check");

        boolean directUnsupported = invokeBoolean("isSafeLanding", new Class<?>[]{ServerLevel.class, BlockPos.class},
                level, unsupportedLanding)
                || invokeBoolean("isInvasionEndpoint", new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class},
                level, pivot, unsupportedLanding);
        int hitsBeforeUnsupported = (int) hits.invoke(cache);
        boolean cachedUnsupportedFirst = (boolean) isLandingSurface.invoke(cache, level, pivot, unsupportedLanding);
        boolean cachedUnsupportedAgain = (boolean) isLandingSurface.invoke(cache, level, pivot, unsupportedLanding);
        require(!directUnsupported && !cachedUnsupportedFirst && !cachedUnsupportedAgain,
                "unsafe surface result changed through the cache");
        require((int) hits.invoke(cache) - hitsBeforeUnsupported == 1,
                "unsafe landing result was not reused");

        boolean directBridgeNeed = invokeBoolean("hasBridgeNeed",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class}, level, pivot, flatLanding);
        int hitsBeforeFlatBridge = (int) hits.invoke(cache);
        boolean cachedBridgeNeedFirst = (boolean) hasBridgeNeed.invoke(cache, level, pivot, flatLanding);
        boolean cachedBridgeNeedAgain = (boolean) hasBridgeNeed.invoke(cache, level, pivot, flatLanding);
        require(directBridgeNeed == cachedBridgeNeedFirst && directBridgeNeed == cachedBridgeNeedAgain,
                "bridge-need result changed through the cache: direct=" + directBridgeNeed + ",first="
                        + cachedBridgeNeedFirst + ",again=" + cachedBridgeNeedAgain);
        require(!directBridgeNeed, "flat fixture unexpectedly reported a bridge need");
        require((int) hits.invoke(cache) - hitsBeforeFlatBridge == 1,
                "negative bridge-need result was not reused");

        boolean directRaisedBridgeNeed = invokeBoolean("hasBridgeNeed",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class}, level, pivot, raisedLanding);
        int hitsBeforeRaisedBridge = (int) hits.invoke(cache);
        boolean cachedRaisedFirst = (boolean) hasBridgeNeed.invoke(cache, level, pivot, raisedLanding);
        boolean cachedRaisedAgain = (boolean) hasBridgeNeed.invoke(cache, level, pivot, raisedLanding);
        require(directRaisedBridgeNeed && cachedRaisedFirst && cachedRaisedAgain,
                "raised endpoint result changed through the cache");
        require((int) hits.invoke(cache) - hitsBeforeRaisedBridge == 1,
                "positive bridge-need result was not reused");

        BlockPos structural = helper.absolutePos(new BlockPos(7, 1, 7));
        Vec3 eastRay = new Vec3(1.0D, 0.0D, 0.0D);
        BlockPos expectedRayLanding = structural.offset(1, 0, 0);
        boolean expectedRaySafe = invokeBoolean("isSafeLanding", new Class<?>[]{ServerLevel.class, BlockPos.class},
                level, expectedRayLanding);
        boolean expectedRayEgress = invokeBoolean("hasLandingAndEgress",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, BlockPos.class},
                level, expectedRayLanding, pivot, expectedRayLanding);
        boolean expectedRaySheer = invokeBoolean("isSheerWallFace",
                new Class<?>[]{ServerLevel.class, BlockPos.class, int.class, int.class},
                level, expectedRayLanding, 1, 1);
        require(expectedRaySafe && expectedRayEgress && !expectedRaySheer,
                "ray landing fixture was not safe: landing=" + expectedRayLanding + " support="
                        + level.getBlockState(expectedRayLanding.below()) + " feet="
                        + level.getBlockState(expectedRayLanding) + " head="
                        + level.getBlockState(expectedRayLanding.above()) + " safe=" + expectedRaySafe
                        + " egress=" + expectedRayEgress + " sheer=" + expectedRaySheer);
        BlockPos directCanonical = invokeBlockPos("resolveCanonicalLanding",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, Vec3.class},
                level, structural, pivot, eastRay);
        require(structural.offset(1, 0, 0).equals(directCanonical),
                "canonical landing did not select the first minimum-score ray candidate: " + directCanonical);
        BlockPos cachedCanonicalFirst = (BlockPos) resolveCanonicalLanding.invoke(cache, level, structural, pivot, eastRay);
        BlockPos cachedCanonicalAgain = (BlockPos) resolveCanonicalLanding.invoke(cache, level, structural, pivot, eastRay);
        require(java.util.Objects.equals(directCanonical, cachedCanonicalFirst)
                        && java.util.Objects.equals(directCanonical, cachedCanonicalAgain),
                "canonical landing changed through repeated same-ray memo lookups");
        require((int) canonicalHits.invoke(cache) == 1 && (int) canonicalSize.invoke(cache) == 1,
                "same structural cell and ray direction were not memoized");

        Vec3 northRay = new Vec3(0.0D, 0.0D, -1.0D);
        BlockPos directNorthCanonical = invokeBlockPos("resolveCanonicalLanding",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, Vec3.class},
                level, structural, pivot, northRay);
        BlockPos cachedNorthCanonical = (BlockPos) resolveCanonicalLanding.invoke(cache, level, structural, pivot, northRay);
        require(java.util.Objects.equals(directNorthCanonical, cachedNorthCanonical),
                "canonical landing changed for a distinct ray direction");
        require((int) canonicalHits.invoke(cache) == 1 && (int) canonicalSize.invoke(cache) == 2,
                "different effective ray directions shared a canonical landing entry");

        BlockPos unsupportedStructural = helper.absolutePos(new BlockPos(12, 300, 12));
        BlockPos directNullCanonical = invokeBlockPos("resolveCanonicalLanding",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, Vec3.class},
                level, unsupportedStructural, pivot, eastRay);
        BlockPos cachedNullFirst = (BlockPos) resolveCanonicalLanding.invoke(
                cache, level, unsupportedStructural, pivot, eastRay);
        BlockPos cachedNullAgain = (BlockPos) resolveCanonicalLanding.invoke(
                cache, level, unsupportedStructural, pivot, eastRay);
        require(directNullCanonical == null && cachedNullFirst == null && cachedNullAgain == null,
                "null canonical landing result changed through memoization");
        require((int) canonicalHits.invoke(cache) == 2 && (int) canonicalSize.invoke(cache) == 3,
                "null canonical landing result was not memoized");

        Method findCachedRoute = cacheType.getDeclaredMethod("findRoute", ServerLevel.class,
                BlockPos.class, BlockPos.class, double.class, double.class);
        Method routeLegLookups = cacheType.getDeclaredMethod("routeLegLookups");
        Method routeLegCacheHits = cacheType.getDeclaredMethod("routeLegCacheHits");
        Method routeSearches = cacheType.getDeclaredMethod("routeSearches");
        findCachedRoute.setAccessible(true);
        routeLegLookups.setAccessible(true);
        routeLegCacheHits.setAccessible(true);
        routeSearches.setAccessible(true);
        Object routeCache = constructor.newInstance(true);
        BlockPos routeStart = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos routeEnd = helper.absolutePos(new BlockPos(5, 1, 2));
        var directRoute = LivingBridgeRouteSearch.findRoute(level, routeStart, routeEnd, 0.6D, 1.8D,
                LivingBridgeRouteSearch.MAX_NODE_BUDGET);
        var firstCachedRoute = findCachedRoute.invoke(routeCache, level, routeStart, routeEnd, 0.6D, 1.8D);
        var repeatedCachedRoute = findCachedRoute.invoke(routeCache, level, routeStart, routeEnd, 0.6D, 1.8D);
        require(!directRoute.isEmpty() && directRoute.equals(firstCachedRoute)
                        && directRoute.equals(repeatedCachedRoute),
                "scan-local route memo changed the selected route");
        require((int) routeLegLookups.invoke(routeCache) == 2
                        && (int) routeLegCacheHits.invoke(routeCache) == 1
                        && (int) routeSearches.invoke(routeCache) == 1,
                "two identical directed route requests did not share one search");
        var reverseCachedRoute = findCachedRoute.invoke(routeCache, level, routeEnd, routeStart, 0.6D, 1.8D);
        var directReverseRoute = LivingBridgeRouteSearch.findRoute(level, routeEnd, routeStart, 0.6D, 1.8D,
                LivingBridgeRouteSearch.MAX_NODE_BUDGET);
        require(directReverseRoute.equals(reverseCachedRoute) && (int) routeSearches.invoke(routeCache) == 2,
                "route memo incorrectly shared a result across directed endpoints");

        Object nextScanRouteCache = constructor.newInstance(true);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        var changedTerrainDirectRoute = LivingBridgeRouteSearch.findRoute(level, routeStart, routeEnd,
                0.6D, 1.8D, LivingBridgeRouteSearch.MAX_NODE_BUDGET);
        var changedTerrainCachedRoute = findCachedRoute.invoke(nextScanRouteCache, level,
                routeStart, routeEnd, 0.6D, 1.8D);
        var changedTerrainRepeatedRoute = findCachedRoute.invoke(nextScanRouteCache, level,
                routeStart, routeEnd, 0.6D, 1.8D);
        require(!changedTerrainDirectRoute.isEmpty() && changedTerrainDirectRoute.equals(changedTerrainCachedRoute)
                        && changedTerrainDirectRoute.equals(changedTerrainRepeatedRoute)
                        && !directRoute.equals(changedTerrainCachedRoute),
                "a new scan reused a route after the terrain changed");
        require((int) routeLegCacheHits.invoke(nextScanRouteCache) == 1
                        && (int) routeSearches.invoke(nextScanRouteCache) == 1,
                "changed-terrain scan did not limit reuse to its own repeated request");

        Object boundedCache = constructor.newInstance(true);
        int inserted = 0;
        BlockPos overflowCandidate = null;
        for (int dx = -12; dx <= 12 && inserted < 520; dx++) {
            for (int dz = -12; dz <= 12 && inserted < 520; dz++) {
                overflowCandidate = pivot.offset(dx, 0, dz);
                isLandingSurface.invoke(boundedCache, level, pivot, overflowCandidate);
                inserted++;
            }
        }
        require((int) size.invoke(boundedCache) == 512, "candidate cache did not fill to its configured bound");
        int hitsBeforeOverflow = (int) hits.invoke(boundedCache);
        boolean directOverflow = invokeBoolean("isSafeLanding", new Class<?>[]{ServerLevel.class, BlockPos.class},
                level, overflowCandidate)
                || invokeBoolean("isInvasionEndpoint", new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class},
                level, pivot, overflowCandidate);
        boolean cachedOverflow = (boolean) isLandingSurface.invoke(boundedCache, level, pivot, overflowCandidate);
        require(directOverflow == cachedOverflow, "post-cap direct evaluation changed the result");
        require((int) size.invoke(boundedCache) == 512, "candidate cache exceeded its bound");
        require((int) hits.invoke(boundedCache) == hitsBeforeOverflow,
                "a post-cap candidate was cached instead of being evaluated directly");

        Object unloadedCache = constructor.newInstance(true);
        BlockPos unloadedPivot = new BlockPos(16000, 64, 16000);
        BlockPos unloadedCandidate = unloadedPivot.offset(3, 0, 0);
        boolean unloadedResult = (boolean) isLandingSurface.invoke(unloadedCache, level, unloadedPivot, unloadedCandidate);
        require(!unloadedResult && !enabled.getBoolean(unloadedCache) && (int) size.invoke(unloadedCache) == 0,
                "cache was enabled or retained a verdict for an unloaded neighborhood");
        helper.succeed();
    }

    private static boolean invokeBoolean(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return (boolean) method.invoke(null, arguments);
    }

    private static BlockPos invokeBlockPos(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return (BlockPos) method.invoke(null, arguments);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
