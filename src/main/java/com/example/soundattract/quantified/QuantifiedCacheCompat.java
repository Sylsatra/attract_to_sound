package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class QuantifiedCacheCompat {
    private static final AtomicBoolean INIT_ATTEMPTED = new AtomicBoolean(false);
    private static volatile Object cacheManager;
    private static volatile long lastAppliedLimitMb = Long.MIN_VALUE;
    private static volatile long lastCleanupTick = Long.MIN_VALUE;

    private QuantifiedCacheCompat() {
    }

    public static boolean isUsable() {
        if (!ModList.get().isLoaded("quantified")) return false;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return false;
            if (!SoundAttractConfig.COMMON.enableQuantifiedCacheIntegration.get()) return false;
        } catch (Throwable ignored) {
        }
        if (!ensureInit()) return false;

        configureCacheManager();
        if (isMemoryPressureHigh()) {
            return false;
        }
        return true;
    }

    public static <T> T getCached(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize) {
        return getCachedInternal(cacheName, key, loader, ttlTicks, maxSize, false);
    }

    public static <T> T getCachedDisk(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize) {
        return getCachedInternal(cacheName, key, loader, ttlTicks, maxSize, true);
    }

    private static <T> T getCachedInternal(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize, boolean useDisk) {
        if (loader == null) return null;
        if (!isUsable()) {
            return loader.get();
        }

        long clampedTtlTicks = Math.max(1L, ttlTicks);
        Duration ttl = Duration.ofMillis(clampedTtlTicks * estimateMillisPerTick());
        long clampedMax = Math.max(0L, maxSize);

        T value = QuantifiedOptionalBridge.tryGetCached(SoundAttractMod.MOD_ID, cacheName, key, loader, ttl, clampedMax, useDisk);
        if (value != null) {
            return value;
        }
        return loader.get();
    }

    private static boolean ensureInit() {
        if (cacheManager != null) return true;
        if (!INIT_ATTEMPTED.compareAndSet(false, true)) {
            return cacheManager != null;
        }
        cacheManager = QuantifiedOptionalBridge.tryFetchCacheManager(SoundAttractMod.MOD_ID);
        return cacheManager != null;
    }

    private static void configureCacheManager() {
        Object manager = cacheManager;
        if (manager == null) return;

        long limitMb = 0L;
        try {
            limitMb = SoundAttractConfig.COMMON.quantifiedCacheMemoryLimitMB.get();
        } catch (Throwable ignored) {
        }
        limitMb = Math.max(0L, limitMb);

        if (limitMb != lastAppliedLimitMb) {
            if (QuantifiedOptionalBridge.trySetCacheMemoryLimit(manager, limitMb)) {
                lastAppliedLimitMb = limitMb;
            }
        }

        boolean triggerCleanup = true;
        try {
            triggerCleanup = SoundAttractConfig.COMMON.triggerQuantifiedCacheCleanupOnMemoryPressure.get();
        } catch (Throwable ignored) {
        }
        if (!triggerCleanup) return;

        Boolean memoryPressureHigh = QuantifiedOptionalBridge.tryIsCacheMemoryPressureHigh(manager);
        if (!Boolean.TRUE.equals(memoryPressureHigh)) return;
        long tick = System.currentTimeMillis() / 50L;
        if ((tick - lastCleanupTick) < 20L) {
            return;
        }
        lastCleanupTick = tick;
        QuantifiedOptionalBridge.tryTriggerCacheCleanup(manager);
    }

    private static boolean isMemoryPressureHigh() {
        boolean bypass = true;
        try {
            bypass = SoundAttractConfig.COMMON.disableQuantifiedCacheOnMemoryPressure.get();
        } catch (Throwable ignored) {
        }
        if (!bypass) return false;

        Object manager = cacheManager;
        if (manager == null) return false;
        return Boolean.TRUE.equals(QuantifiedOptionalBridge.tryIsCacheMemoryPressureHigh(manager));
    }

    private static long estimateMillisPerTick() {
        long fallback = 50L;
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return fallback;
            float avg = 50.0f;
            if (Float.isNaN(avg) || avg <= 0.0f) return fallback;
            long ms = (long) avg;
            if (ms < 1L) return fallback;
            return Math.max(20L, Math.min(250L, ms));
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
