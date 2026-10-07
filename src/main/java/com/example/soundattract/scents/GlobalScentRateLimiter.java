package com.example.soundattract.scents;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GlobalScentRateLimiter {
    private static final int MAX_TRACKED_SHOOTERS = 8192;

    private static Cache<UUID, Integer> perShooterCounter = buildCache(60);

    private static Cache<UUID, Integer> buildCache(int minutes) {
        return Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_SHOOTERS)
                .expireAfterWrite(minutes, TimeUnit.MINUTES)
                .build();
    }

    public static synchronized boolean tryConsume(UUID shooterUuid) {
        if (shooterUuid == null) return true;
        if (SoundAttractConfig.COMMON == null) return true;

        int limit = SoundAttractConfig.COMMON.arrowScentRateLimitPerShooterPerMinute.get();
        if (limit <= 0) return true;

        int current = perShooterCounter.get(shooterUuid, ignored -> 0);
        if (current >= limit) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                SoundAttractMod.LOGGER.info("[GlobalScentRateLimiter] Shooter {} hit rate limit ({}).", shooterUuid, limit);
            }
            return false;
        }
        perShooterCounter.put(shooterUuid, current + 1);
        return true;
    }

    public static void clear() {
        perShooterCounter.invalidateAll();
    }
}