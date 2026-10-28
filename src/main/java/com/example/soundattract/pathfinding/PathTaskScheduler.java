package com.example.soundattract.pathfinding;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.example.soundattract.config.SoundAttractConfig;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

public final class PathTaskScheduler {
    private static Map<ResourceKey<Level>, TickBudget> budgets = new ConcurrentHashMap<>();

    public static void reinitializeCaches() {
        int max = SoundAttractConfig.COMMON.globalCacheMaxSize.get();
        int mins = SoundAttractConfig.COMMON.globalCacheExpireMins.get();

        Map<ResourceKey<Level>, TickBudget> oldObj = budgets;
        budgets = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<ResourceKey<Level>, TickBudget>build().asMap();
        budgets.putAll(oldObj);

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            com.example.soundattract.Soundattract.LOGGER.info("[PathTaskScheduler] Initialized Guava internal memory caches (max {}, {} mins)", max, mins);
        }
    }

    private PathTaskScheduler() {}

    private static final class TickBudget {
        long lastTick;
        int attempts;
    }

    public static boolean tryConsumePathAttempt(Level lvl) {
        if (lvl == null) return true;
        long now = lvl.getGameTime();
        ResourceKey<Level> dim = lvl.dimension();
        TickBudget b = budgets.computeIfAbsent(dim, k -> new TickBudget());
        if (b.lastTick != now) {
            b.lastTick = now;
            b.attempts = 0;
        }
        int max = 10;
        try { max = Math.max(0, SoundAttractConfig.COMMON.maxPathAttemptsPerTick.get()); } catch (Throwable ignored) {}
        if (b.attempts >= max) {
            PFStats.pathAttemptsRejected++;
            return false;
        }
        b.attempts++;
        PFStats.pathAttemptsConsumed++;
        return true;
    }
}
