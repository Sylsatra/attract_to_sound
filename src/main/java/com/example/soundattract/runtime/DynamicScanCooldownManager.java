package com.example.soundattract.runtime;

import com.example.soundattract.config.SoundAttractConfig;

public class DynamicScanCooldownManager {
    public static int currentScanCooldownTicks = 20;

    private static final int MIN_COOLDOWN = 10; 
    private static final int MAX_COOLDOWN = 120;

    private static long lastCheckTime = 0;
    private static long lastTickCount = 0;

    private static double map(double value, double fromLow, double fromHigh, double toLow, double toHigh) {
        value = Math.max(fromLow, Math.min(value, fromHigh));
        return toLow + (value - fromLow) * (toHigh - toLow) / (fromHigh - fromLow);
    }


    public static void update(long totalTickCount, int mobCount) {

        double ticksPerMob = SoundAttractConfig.COMMON.cooldownTicksPerMob.get();
        
        double baseCooldown = MIN_COOLDOWN + (mobCount * ticksPerMob);

        double tpsMultiplier = 1.0; 
        long now = System.currentTimeMillis();
        
        if (lastCheckTime > 0) {
            long ticksElapsed = totalTickCount - lastTickCount;
            long timeElapsedMs = now - lastCheckTime;

            if (ticksElapsed > 0 && timeElapsedMs > 0) { 
                double tps = (double) ticksElapsed * 1000.0 / timeElapsedMs;
                
                double lowTpsThreshold = SoundAttractConfig.COMMON.minTpsForScanCooldown.get();
                double highTpsThreshold = SoundAttractConfig.COMMON.maxTpsForScanCooldown.get();

                double maxMultiplier = 2.0; 
                double minMultiplier = 0.8; 

                if (tps < highTpsThreshold) { 
                    tpsMultiplier = map(tps, lowTpsThreshold, highTpsThreshold, maxMultiplier, minMultiplier);
                } else {
                    tpsMultiplier = minMultiplier; 
                }
            }
        }
        
        lastCheckTime = now;
        lastTickCount = totalTickCount;

        double targetCooldown = baseCooldown * tpsMultiplier;
        
        int clampedTarget = (int) Math.round(Math.max(MIN_COOLDOWN, Math.min(targetCooldown, MAX_COOLDOWN)));

        if (currentScanCooldownTicks < clampedTarget) {
            currentScanCooldownTicks++;
        } else if (currentScanCooldownTicks > clampedTarget) {
            currentScanCooldownTicks--;
        }
    }

    public static boolean shouldScanThisTick(long mobId, long totalTickCount) {
        int effectiveCooldown = Math.max(1, currentScanCooldownTicks);
        return ((mobId + totalTickCount) % effectiveCooldown) == 0;
    }
}
