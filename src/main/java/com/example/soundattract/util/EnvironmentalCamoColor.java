package com.example.soundattract.util;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnvironmentalCamoColor {
    private record CacheEntry(Optional<Integer> color, long gameTime) {}

    private static final Cache<String, CacheEntry> LOCAL_CACHE = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private static volatile boolean viewerBackdropHealthy = true;

    private EnvironmentalCamoColor() {}

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOCAL_CACHE.invalidateAll();
        viewerBackdropHealthy = true;
    }

    public static EnvironmentalCamoSettings.SamplingMode parseMode(String raw) {
        return EnvironmentalCamoSettings.parseMode(raw);
    }

    public static boolean shouldUseViewerBackdrop(double tps, boolean currentlyUsingViewer, double minTps, double recoveryTps) {
        return EnvironmentalCamoSettings.shouldUseViewerBackdrop(tps, currentlyUsingViewer, minTps, recoveryTps);
    }

    public static int parseHexColor(String raw, int fallback) {
        return EnvironmentalCamoSettings.parseHexColor(raw, fallback);
    }

    public static Optional<Integer> getEnvironmentalColor(LivingEntity target, Mob looker, Level level, Supplier<Optional<Integer>> averageFallback) {
        if (target == null || level == null) {
            return fallback(averageFallback);
        }

        EnvironmentalCamoSettings.SamplingMode mode = parseMode(SoundAttractConfig.COMMON.environmentalCamouflageSamplingMode.get());
        if (mode == EnvironmentalCamoSettings.SamplingMode.AVERAGE_AREA) {
            return fallback(averageFallback);
        }

        double tps = estimateTps();
        viewerBackdropHealthy = shouldUseViewerBackdrop(
                tps,
                viewerBackdropHealthy,
                SoundAttractConfig.COMMON.envBackdropMinTps.get(),
                SoundAttractConfig.COMMON.envBackdropRecoveryTps.get());

        if (!viewerBackdropHealthy) {
            logFallback("low_tps", tps);
            return mode == EnvironmentalCamoSettings.SamplingMode.HYBRID ? fallback(averageFallback) : Optional.empty();
        }

        Optional<Integer> backdrop = cachedBackdropColor(target, looker, level);
        if (backdrop.isPresent()) {
            return backdrop;
        }

        return mode == EnvironmentalCamoSettings.SamplingMode.HYBRID ? fallback(averageFallback) : Optional.empty();
    }

    private static Optional<Integer> cachedBackdropColor(LivingEntity target, Mob looker, Level level) {
        String key = cacheKey(target, looker, level);
        long ttl = Math.max(1L, SoundAttractConfig.COMMON.envBackdropCacheTtlTicks.get());

        if (QuantifiedCacheCompat.isUsable()) {
            return QuantifiedCacheCompat.getCached(
                    "soundattract_env_backdrop_color",
                    key,
                    () -> computeBackdropColor(target, looker, level),
                    ttl,
                    8192L
            );
        }

        long now = level.getGameTime();
        CacheEntry existing = LOCAL_CACHE.getIfPresent(key);
        if (existing != null && (now - existing.gameTime()) <= ttl) {
            return existing.color();
        }

        Optional<Integer> computed = computeBackdropColor(target, looker, level);
        LOCAL_CACHE.put(key, new CacheEntry(computed, now));
        if (LOCAL_CACHE.asMap().size() > 8192) {
            LOCAL_CACHE.cleanUp();
        }
        return computed;
    }

    private static Optional<Integer> computeBackdropColor(LivingEntity target, Mob looker, Level level) {
        if (target == null || looker == null || level == null || target.level() != level || looker.level() != level) {
            return Optional.empty();
        }

        Vec3 eye = looker.getEyePosition();
        List<Vec3> samples = samplePoints(target, eye);
        int limit = Math.max(1, Math.min(SoundAttractConfig.COMMON.envBackdropSampleRays.get(), samples.size()));
        int maxDistance = Math.max(1, SoundAttractConfig.COMMON.envBackdropMaxDistance.get());
        List<Integer> colors = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            sampleRayColor(level, eye, samples.get(i), maxDistance).ifPresent(colors::add);
        }

        int minSamples = Math.max(1, SoundAttractConfig.COMMON.envBackdropMinSamples.get());
        if (colors.size() < minSamples) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
                SoundAttractMod.LOGGER.info("[EnvBackdrop] Found {} backdrop samples, need {}.", colors.size(), minSamples);
            }
            return Optional.empty();
        }

        Optional<Integer> average = average(colors);
        if (average.isPresent() && DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
            SoundAttractMod.LOGGER.info("[EnvBackdrop] Backdrop color: 0x{} from {} samples.",
                    String.format("%06X", average.get()), colors.size());
        }
        return average;
    }

    private static Optional<Integer> sampleRayColor(Level level, Vec3 eye, Vec3 targetSample, int maxDistance) {
        Vec3 dir = targetSample.subtract(eye);
        if (dir.lengthSqr() < 1.0e-8) return Optional.empty();
        dir = dir.normalize();

        BlockPos lastPos = null;
        for (int i = 1; i <= maxDistance; i++) {
            Vec3 probe = targetSample.add(dir.scale(i));
            BlockPos pos = BlockPos.containing(probe);
            lastPos = pos;
            if (!level.isLoaded(pos)) {
                return Optional.empty();
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                try {
                    int color = state.getMapColor(level, pos).col;
                    return color == 0 ? Optional.empty() : Optional.of(color & 0xFFFFFF);
                } catch (Throwable ignored) {
                    return Optional.empty();
                }
            }
        }

        if (SoundAttractConfig.COMMON.envBackdropUseSkyColor.get() && lastPos != null && level.isLoaded(lastPos) && level.canSeeSky(lastPos)) {
            return Optional.of(skyColor(level));
        }
        return Optional.empty();
    }

    private static List<Vec3> samplePoints(LivingEntity target, Vec3 eye) {
        Vec3 base = target.position();
        double height = Math.max(0.5D, target.getBbHeight());
        double width = Math.max(0.3D, target.getBbWidth());
        Vec3 torso = base.add(0.0D, height * 0.55D, 0.0D);
        Vec3 head = base.add(0.0D, height * 0.90D, 0.0D);
        Vec3 feet = base.add(0.0D, Math.min(0.25D, height * 0.20D), 0.0D);

        Vec3 view = torso.subtract(eye);
        Vec3 right = view.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0e-8) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        double side = width * 0.45D;

        List<Vec3> samples = new ArrayList<>(5);
        samples.add(torso);
        samples.add(head);
        samples.add(feet);
        samples.add(torso.add(right.scale(side)));
        samples.add(torso.add(right.scale(-side)));
        return samples;
    }

    private static Optional<Integer> average(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) return Optional.empty();
        long r = 0L;
        long g = 0L;
        long b = 0L;
        for (int color : colors) {
            r += (color >> 16) & 0xFF;
            g += (color >> 8) & 0xFF;
            b += color & 0xFF;
        }
        int size = colors.size();
        return Optional.of(((int) (r / size) << 16) | ((int) (g / size) << 8) | (int) (b / size));
    }

    private static Optional<Integer> fallback(Supplier<Optional<Integer>> averageFallback) {
        return averageFallback == null ? Optional.empty() : averageFallback.get();
    }

    private static String cacheKey(LivingEntity target, Mob looker, Level level) {
        BlockPos targetPos = target.blockPosition();
        int cell = Math.max(1, SoundAttractConfig.COMMON.envBackdropViewerCellSize.get());
        int viewerX = looker == null ? 0 : (int) Math.floor(looker.getX() / (double) cell);
        int viewerY = looker == null ? 0 : (int) Math.floor(looker.getY() / (double) cell);
        int viewerZ = looker == null ? 0 : (int) Math.floor(looker.getZ() / (double) cell);
        return new StringBuilder(160)
                .append(level.dimension().location()).append('|')
                .append(target.getUUID()).append('|')
                .append(targetPos.getX()).append(',').append(targetPos.getY()).append(',').append(targetPos.getZ()).append('|')
                .append(viewerX).append(',').append(viewerY).append(',').append(viewerZ).append('|')
                .append(target.getPose().name()).append('|')
                .append(viewerBackdropHealthy ? "viewer" : "fallback")
                .toString();
    }

    private static int skyColor(Level level) {
        if (level.isRaining() || level.isThundering()) {
            return parseHexColor(SoundAttractConfig.COMMON.envBackdropRainSkyColor.get(), 0x596772);
        }
        long dayTime = level.getDayTime() % 24000L;
        boolean isDay = dayTime >= 0L && dayTime < 12000L;
        return isDay
                ? parseHexColor(SoundAttractConfig.COMMON.envBackdropDaySkyColor.get(), 0x77ADFF)
                : parseHexColor(SoundAttractConfig.COMMON.envBackdropNightSkyColor.get(), 0x0B1026);
    }

    private static double estimateTps() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return 20.0D;
            float averageTickTime = server.getAverageTickTime();
            if (!Float.isFinite(averageTickTime) || averageTickTime <= 0.0F) return 20.0D;
            return Math.max(1.0D, Math.min(20.0D, 1000.0D / averageTickTime));
        } catch (Throwable ignored) {
            return 20.0D;
        }
    }

    private static void logFallback(String reason, double tps) {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
            SoundAttractMod.LOGGER.info("[EnvBackdrop] Using average-area fallback: {} at {} TPS.", reason, String.format("%.2f", tps));
        }
    }
}