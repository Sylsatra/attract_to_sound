package com.example.soundattract.integration.spore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

public class BiomassSoundSystem {
    private static final double ALERT_THRESHOLD = 500.0;
    private static final int MAX_PRESSURE_REGIONS = 8192;

    private record PressureKey(ResourceLocation dimension, BlockPos region) {}

    private static final Cache<PressureKey, Double> pressureMap = Caffeine.newBuilder()
            .maximumSize(MAX_PRESSURE_REGIONS)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private static int cleanupTimer = 0;


    public static void processTick(ServerLevel level) {
        if (level == null) {
            return;
        }
        cleanupTimer++;
        if (cleanupTimer >= 100) {
            cleanupTimer = 0;
            pressureMap.asMap().replaceAll((pos, weight) -> weight * 0.8);
            pressureMap.asMap().values().removeIf(weight -> weight < 1.0);
        }
    }


    public static void recordBiomassSound(ServerLevel level, BlockPos pos, double weight) {
        if (level == null || pos == null || !Double.isFinite(weight)
                || !ModList.get().isLoaded("spore")) return;

        BlockPos region = new BlockPos((pos.getX() >> 4) << 4, (pos.getY() >> 4) << 4, (pos.getZ() >> 4) << 4);
        PressureKey key = new PressureKey(level.dimension().location(), region);
        double currentPressure = pressureMap.asMap().merge(key, weight, Double::sum);

        if (currentPressure > ALERT_THRESHOLD) {
            triggerHiveAlert(level, pos);
            pressureMap.put(key, 0.0);
        }
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        ResourceLocation dimension = level.dimension().location();
        pressureMap.asMap().keySet().removeIf(key -> key != null && dimension.equals(key.dimension()));
    }

    public static void clear() {
        pressureMap.invalidateAll();
        cleanupTimer = 0;
    }

    private static void triggerHiveAlert(ServerLevel level, BlockPos pos) {
        BiomassSoundHandler.triggerHiveAlert(level, pos);
    }
}