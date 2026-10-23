package com.example.soundattract.util;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class EnvironmentalCamoColor {
    private static volatile boolean viewerBackdropHealthy = true;

    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> viewerBackdropHealthy = true);
    }

    private EnvironmentalCamoColor() {
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

    public static Optional<Integer> getEnvironmentalColor(
            LivingEntity target,
            Mob looker,
            Level level,
            Supplier<Optional<Integer>> averageFallback) {
        if (target == null || level == null) {
            return fallback(averageFallback);
        }

        EnvironmentalCamoSettings.SamplingMode mode = parseMode(
                SoundAttractConfig.COMMON.environmentalCamouflageSamplingMode.get());
        if (mode == EnvironmentalCamoSettings.SamplingMode.AVERAGE_AREA) {
            return fallback(averageFallback);
        }

        double tps = estimateTps(level);
        viewerBackdropHealthy = shouldUseViewerBackdrop(
                tps,
                viewerBackdropHealthy,
                SoundAttractConfig.COMMON.envBackdropMinTps.get(),
                SoundAttractConfig.COMMON.envBackdropRecoveryTps.get());

        if (!viewerBackdropHealthy) {
            logFallback("low_tps", tps);
            return mode == EnvironmentalCamoSettings.SamplingMode.HYBRID
                    ? fallback(averageFallback)
                    : Optional.empty();
        }

        Optional<Integer> backdrop = cachedBackdropColor(target, looker, level);
        if (backdrop.isPresent()) {
            return backdrop;
        }

        return mode == EnvironmentalCamoSettings.SamplingMode.HYBRID
                ? fallback(averageFallback)
                : Optional.empty();
    }

    private static Optional<Integer> cachedBackdropColor(LivingEntity target, Mob looker, Level level) {
        String key = cacheKey(target, looker, level);
        long ttl = Math.max(1L, SoundAttractConfig.COMMON.envBackdropCacheTtlTicks.get());
        return QuantifiedCacheCompat.getCached(
                "soundattract_env_backdrop_color",
                key,
                () -> computeBackdropColor(target, looker, level),
                ttl,
                8192L);
    }

    private static Optional<Integer> computeBackdropColor(LivingEntity target, Mob looker, Level level) {
        if (target == null || looker == null || level == null || target.level() != level || looker.level() != level) {
            return Optional.empty();
        }

        Vec3 eye = looker.getEyePosition();
        List<Vec3> samples = samplePoints(target, eye);
        int limit = Math.max(1, Math.min(SoundAttractConfig.COMMON.envBackdropSampleRays.get(), samples.size()));
        int maxDistance = Math.max(1, SoundAttractConfig.COMMON.envBackdropMaxDistance.get());
        List<Integer> colors = new ArrayList<>(limit);

        for (int i = 0; i < limit; i++) {
            sampleRayColor(level, eye, samples.get(i), maxDistance).ifPresent(colors::add);
        }

        int minSamples = Math.max(1, SoundAttractConfig.COMMON.envBackdropMinSamples.get());
        if (colors.size() < minSamples) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[EnvBackdrop] Found {} backdrop samples, need {}.", colors.size(), minSamples);
            }
            return Optional.empty();
        }

        Optional<Integer> average = average(colors);
        if (average.isPresent() && SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[EnvBackdrop] Backdrop color: 0x{} from {} samples.",
                    String.format("%06X", average.get()), colors.size());
        }
        return average;
    }

    private static Optional<Integer> sampleRayColor(Level level, Vec3 eye, Vec3 targetSample, int maxDistance) {
        Vec3 direction = targetSample.subtract(eye);
        if (direction.lengthSqr() < 1.0e-8D) {
            return Optional.empty();
        }
        direction = direction.normalize();

        BlockPos lastPos = null;
        for (int i = 1; i <= maxDistance; i++) {
            Vec3 probe = targetSample.add(direction.scale(i));
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

        if (SoundAttractConfig.COMMON.envBackdropUseSkyColor.get()
                && lastPos != null
                && level.isLoaded(lastPos)
                && level.canSeeSky(lastPos)) {
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
        if (right.lengthSqr() < 1.0e-8D) {
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
        if (colors == null || colors.isEmpty()) {
            return Optional.empty();
        }
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        for (int color : colors) {
            red += (color >> 16) & 0xFF;
            green += (color >> 8) & 0xFF;
            blue += color & 0xFF;
        }
        int size = colors.size();
        return Optional.of(((int) (red / size) << 16)
                | ((int) (green / size) << 8)
                | (int) (blue / size));
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
                .append(level.dimension().identifier()).append('|')
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

    private static double estimateTps(Level level) {
        try {
            MinecraftServer server = level instanceof ServerLevel serverLevel
                    ? serverLevel.getServer()
                    : Soundattract.getServer();
            if (server == null) {
                return 20.0D;
            }
            float averageTickTime = server.getAverageTickTimeNanos() / 1_000_000.0f;
            if (!Float.isFinite(averageTickTime) || averageTickTime <= 0.0F) {
                return 20.0D;
            }
            return Math.max(1.0D, Math.min(20.0D, 1000.0D / averageTickTime));
        } catch (Throwable ignored) {
            return 20.0D;
        }
    }

    private static void logFallback(String reason, double tps) {
        if (SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[EnvBackdrop] Using average-area fallback: {} at {} TPS.",
                    reason, String.format("%.2f", tps));
        }
    }
}

