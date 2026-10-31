package com.example.soundattract.integration.spore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Spore Integration: Deluge of Sound (Biomass Sound Pressure System).
 * 
 * This system tracks 'Sound Pressure' (accumulated weight) in regions of the biomass.
 * High sound pressure triggers a 'Hive Alert', summoning reinforcements and alerting Vigil entities.
 * 
 * Safety: Uses ConcurrentHashMap for thread safety and periodic cleanup for memory management.
 * Non-mandatory: Logic is guarded by ModList checks and Spore-specific implementation is 
 * delegated to BiomassSoundHandler to avoid NoClassDefFoundError.
 */
public class BiomassSoundSystem {
    private static final double ALERT_THRESHOLD = 500.0;
    
    private static final Map<BlockPos, Double> pressureMap = new ConcurrentHashMap<>();
    private static int cleanupTimer = 0;

    /**
     * Called by BiomassSoundHandler only if Spore is loaded.
     */
    public static void processTick(ServerLevel level) {
        cleanupTimer++;
        if (cleanupTimer >= 100) { 
            cleanupTimer = 0;
            pressureMap.replaceAll((pos, weight) -> weight * 0.8);
            pressureMap.values().removeIf(weight -> weight < 1.0);
        }
    }

    /**
     * Records sound weight in a specific area and checks for Hive Alerts.
     */
    public static void recordBiomassSound(ServerLevel level, BlockPos pos, double weight) {
        if (!ModList.get().isLoaded("spore")) return;

        BlockPos region = new BlockPos((pos.getX() >> 4) << 4, (pos.getY() >> 4) << 4, (pos.getZ() >> 4) << 4);
        
        double currentPressure = pressureMap.merge(region, weight, Double::sum);

        if (currentPressure > ALERT_THRESHOLD) {
            triggerHiveAlert(level, pos);
            pressureMap.put(region, 0.0); 
        }
    }

    private static void triggerHiveAlert(ServerLevel level, BlockPos pos) {
        BiomassSoundHandler.triggerHiveAlert(level, pos);
    }
}