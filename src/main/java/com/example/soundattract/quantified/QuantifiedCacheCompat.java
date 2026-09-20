package com.example.soundattract.quantified;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class QuantifiedCacheCompat {
    private static final AtomicBoolean INIT_ATTEMPTED = new AtomicBoolean(false);
    private static volatile Object cacheManager;
    private static volatile long lastAppliedLimitMb = Long.MIN_VALUE;
    private static volatile long lastCleanupTick = Long.MIN_VALUE;
    private static volatile long pressureCheckedAt = Long.MIN_VALUE;
    private static volatile boolean cachedMemoryPressure;
    private static final Cache<String, LocalValue> FRONT_CACHE = Caffeine.newBuilder()
            .maximumSize(16384)
            .expireAfterAccess(java.time.Duration.ofMinutes(10))
            .build();

    private record LocalValue(Object value, long expiresAtNanos) {}
    private static final Object NULL_VALUE = new Object();

    private QuantifiedCacheCompat() {
    }

    public static boolean isUsable() {
        if (!FabricLoader.getInstance().isModLoaded("quantified")) return false;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return false;
            if (!SoundAttractConfig.COMMON.enableQuantifiedCacheIntegration.get()) return false;
        } catch (Throwable ignored) {
        }
        if (!ensureInit()) return false;

        configureCacheManager();
        if (isMemoryPressureHighCached()) {
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
        long clampedTtlTicks = Math.max(1L, ttlTicks);
        String localKey = cacheName + "\u0000" + key + "\u0000" + useDisk;
        long expiresAt = System.nanoTime() + Duration.ofMillis(clampedTtlTicks * estimateMillisPerTick()).toNanos();
        LocalValue local = FRONT_CACHE.getIfPresent(localKey);
        if (local != null && local.expiresAtNanos() > System.nanoTime()) {
            if (local.value() == NULL_VALUE) return null;
            @SuppressWarnings("unchecked") T value = (T) local.value();
            return value;
        }
        if (local != null) FRONT_CACHE.invalidate(localKey);
        if (!isUsable()) {
            T value = loader.get();
            FRONT_CACHE.put(localKey, new LocalValue(value == null ? NULL_VALUE : value, expiresAt));
            return value;
        }

        Duration ttl = Duration.ofMillis(clampedTtlTicks * estimateMillisPerTick());
        long clampedMax = Math.max(0L, maxSize);

        T value = QuantifiedOptionalBridge.tryGetCached(Soundattract.MOD_ID, cacheName, key, loader, ttl, clampedMax, useDisk);
        if (value == null) value = loader.get();
        FRONT_CACHE.put(localKey, new LocalValue(value == null ? NULL_VALUE : value, expiresAt));
        return value;
    }

    private static boolean ensureInit() {
        if (cacheManager != null) return true;
        if (!INIT_ATTEMPTED.compareAndSet(false, true)) {
            return cacheManager != null;
        }
        cacheManager = QuantifiedOptionalBridge.tryFetchCacheManager(Soundattract.MOD_ID);
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

    private static boolean isMemoryPressureHighCached() {
        boolean bypass = true;
        try {
            bypass = SoundAttractConfig.COMMON.disableQuantifiedCacheOnMemoryPressure.get();
        } catch (Throwable ignored) {
        }
        if (!bypass) return false;

        Object manager = cacheManager;
        if (manager == null) return false;
        long now = System.currentTimeMillis() / 50L;
        if (now - pressureCheckedAt >= 20L) {
            cachedMemoryPressure = Boolean.TRUE.equals(QuantifiedOptionalBridge.tryIsCacheMemoryPressureHigh(manager));
            pressureCheckedAt = now;
        }
        return cachedMemoryPressure;
    }

    private static MinecraftServer currentServer = null;
    
    static {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            cacheManager = null;
            INIT_ATTEMPTED.set(false);
            lastAppliedLimitMb = Long.MIN_VALUE;
            lastCleanupTick = Long.MIN_VALUE;
            pressureCheckedAt = Long.MIN_VALUE;
            cachedMemoryPressure = false;
            FRONT_CACHE.invalidateAll();
        });
    }
    
    private static long estimateMillisPerTick() {
        long fallback = 50L;
        try {
            MinecraftServer server = currentServer;
            if (server == null) return fallback;
            float avg = server.getAverageTickTime();
            if (Float.isNaN(avg) || avg <= 0.0f) return fallback;
            long ms = (long) avg;
            if (ms < 1L) return fallback;
            return Math.max(20L, Math.min(250L, ms));
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
