package com.example.soundattract.quantified;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class QuantifiedCacheCompat {
    private static final AtomicBoolean INIT_ATTEMPTED = new AtomicBoolean(false);
    private static final long USABLE_CHECK_INTERVAL_MILLIS = 1000L;
    private static final long MAX_LOCAL_TTL_MILLIS = TimeUnit.DAYS.toMillis(7L);
    private static final long MAX_QUANTIFIED_CACHE_ENTRIES = 65_536L;
    private static volatile Object cacheManager;
    private static final AtomicLong LAST_APPLIED_LIMIT_MB = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong LAST_CLEANUP_TICK = new AtomicLong(Long.MIN_VALUE);
    private static volatile long pressureCheckedAt = Long.MIN_VALUE;
    private static volatile boolean cachedMemoryPressure;
    private static volatile boolean cachedIsUsable;
    private static final AtomicLong LAST_USABLE_CHECK_MILLIS = new AtomicLong(0L);
    private static final Cache<LocalKey, LocalValue> FRONT_CACHE = Caffeine.newBuilder()
            .maximumSize(16384)
            .expireAfterAccess(java.time.Duration.ofMinutes(10))
            .build();

    private record LocalKey(String cacheName, String key, boolean useDisk) {}
    private record LocalValue(Object value, long expiresAtNanos) {}
    private static final Object NULL_VALUE = new Object();

    private QuantifiedCacheCompat() {
    }

    public static boolean isUsable() {
        if (!FabricLoader.getInstance().isModLoaded("quantified")) return false;
        long now = System.currentTimeMillis();
        long lastCheck = LAST_USABLE_CHECK_MILLIS.get();
        if (now - lastCheck < USABLE_CHECK_INTERVAL_MILLIS) return cachedIsUsable;
        if (LAST_USABLE_CHECK_MILLIS.compareAndSet(lastCheck, now)) {
            cachedIsUsable = recomputeIsUsable();
        }
        return cachedIsUsable;
    }

    private static boolean recomputeIsUsable() {
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
        if (cacheName == null || key == null) return loader.get();
        long clampedTtlTicks = Math.max(1L, ttlTicks);
        long ttlMillis = boundedTtlMillis(clampedTtlTicks, estimateMillisPerTick());
        Duration ttl = Duration.ofMillis(ttlMillis);
        long clampedMax = Math.min(MAX_QUANTIFIED_CACHE_ENTRIES, Math.max(64L, maxSize));
        LocalKey localKey = new LocalKey(cacheName, key, useDisk);
        long now = System.nanoTime();
        LocalValue cached = FRONT_CACHE.getIfPresent(localKey);
        if (cached != null) {
            if (cached.expiresAtNanos() > now) return unpack(cached);
            FRONT_CACHE.invalidate(localKey);
        }

        LocalValue loaded = FRONT_CACHE.get(localKey, ignored -> {
            T value = null;
            if (isUsable()) {
                value = QuantifiedOptionalBridge.tryGetCached(
                        Soundattract.MOD_ID, cacheName, key, loader, ttl, clampedMax, useDisk);
            }
            if (value == null) value = loader.get();
            return new LocalValue(value == null ? NULL_VALUE : value,
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ttlMillis));
        });
        return unpack(loaded);
    }

    private static long boundedTtlMillis(long ttlTicks, long tickMillis) {
        long safeTicks = Math.max(1L, ttlTicks);
        long safeMillis = Math.max(1L, tickMillis);
        if (safeTicks > MAX_LOCAL_TTL_MILLIS / safeMillis) return MAX_LOCAL_TTL_MILLIS;
        return Math.max(1L, safeTicks * safeMillis);
    }

    @SuppressWarnings("unchecked")
    private static <T> T unpack(LocalValue value) {
        return value.value() == NULL_VALUE ? null : (T) value.value();
    }

    private static synchronized void configureCacheManager() {
        Object manager = cacheManager;
        if (manager == null) return;

        long limitMb = 0L;
        try {
            limitMb = SoundAttractConfig.COMMON.quantifiedCacheMemoryLimitMB.get();
        } catch (Throwable ignored) {
        }
        limitMb = Math.max(0L, limitMb);

        if (limitMb != LAST_APPLIED_LIMIT_MB.get()) {
            if (QuantifiedOptionalBridge.trySetCacheMemoryLimit(manager, limitMb)) {
                LAST_APPLIED_LIMIT_MB.set(limitMb);
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
        long previousCleanupTick = LAST_CLEANUP_TICK.get();
        if ((tick - previousCleanupTick) < 20L || !LAST_CLEANUP_TICK.compareAndSet(previousCleanupTick, tick)) return;
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
            cachedMemoryPressure = isMemoryPressureHigh();
            pressureCheckedAt = now;
        }
        return cachedMemoryPressure;
    }

    private static boolean ensureInit() {
        if (cacheManager != null) return true;
        if (!INIT_ATTEMPTED.compareAndSet(false, true)) {
            return cacheManager != null;
        }
        cacheManager = QuantifiedOptionalBridge.tryFetchCacheManager(Soundattract.MOD_ID);
        return cacheManager != null;
    }

    private static MinecraftServer currentServer = null;

    static {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            cacheManager = null;
            INIT_ATTEMPTED.set(false);
            LAST_APPLIED_LIMIT_MB.set(Long.MIN_VALUE);
            LAST_CLEANUP_TICK.set(Long.MIN_VALUE);
            pressureCheckedAt = Long.MIN_VALUE;
            cachedMemoryPressure = false;
            cachedIsUsable = false;
            LAST_USABLE_CHECK_MILLIS.set(0L);
            FRONT_CACHE.invalidateAll();
        });
    }

    private static long estimateMillisPerTick() {
        long fallback = 50L;
        try {
            MinecraftServer server = currentServer;
            if (server == null) return fallback;
            float avg = server.getAverageTickTimeNanos() / 1_000_000.0f;
            if (Float.isNaN(avg) || avg <= 0.0f) return fallback;
            long ms = (long) avg;
            if (ms < 1L) return fallback;
            return Math.max(20L, Math.min(250L, ms));
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}

