package com.example.soundattract.scents;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GlobalScentRateLimiter {

    private static LoadingCache<UUID, Integer> perShooterCounter = buildCache(60);

    private static LoadingCache<UUID, Integer> buildCache(int minutes) {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(minutes, TimeUnit.MINUTES)
                .build(CacheLoader.from(k -> 0));
    }

    public static synchronized boolean tryConsume(UUID shooterUuid) {
        if (shooterUuid == null) return true;
        if (SoundAttractConfig.COMMON == null) return true;

        int limit = SoundAttractConfig.COMMON.arrowScentRateLimitPerShooterPerMinute.get();
        if (limit <= 0) return true;

        int current = perShooterCounter.getUnchecked(shooterUuid);
        if (current >= limit) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[GlobalScentRateLimiter] Shooter {} hit rate limit ({}).", shooterUuid, limit);
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
