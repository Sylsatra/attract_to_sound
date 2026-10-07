package com.example.soundattract.pathfinding;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

public final class PathTaskScheduler {
    private static final int MAX_RUNTIME_GLOBAL_CACHE_SIZE = 65536;
    private static volatile Map<ResourceKey<Level>, TickBudget> budgets = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(64)
        .<ResourceKey<Level>, TickBudget>build()
        .asMap();

    public static void reinitializeCaches() {
        int max = Math.min(MAX_RUNTIME_GLOBAL_CACHE_SIZE,
                Math.max(100, SoundAttractConfig.COMMON.globalCacheMaxSize.get()));
        int mins = Math.max(1, SoundAttractConfig.COMMON.globalCacheExpireMins.get());

        Map<ResourceKey<Level>, TickBudget> oldObj = budgets;
        budgets = Caffeine.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).<ResourceKey<Level>, TickBudget>build().asMap();
        budgets.putAll(oldObj);

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[PathTaskScheduler] Initialized Caffeine memory caches (max {}, {} mins)", max, mins);
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