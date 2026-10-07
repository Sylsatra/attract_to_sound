package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuantifiedCacheCompat {
    private static final AtomicBoolean INIT_ATTEMPTED = new AtomicBoolean(false);
    private static final Cache<LocalKey, LocalValue> LIVE_STATE_CACHE = Caffeine.newBuilder()
            .maximumSize(16384)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private static final Object NULL_VALUE = new Object();
    private static volatile Object cacheManager;
    private static final AtomicLong LAST_APPLIED_LIMIT_MB = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong LAST_CLEANUP_TICK = new AtomicLong(Long.MIN_VALUE);
    private static volatile boolean cachedIsUsable = false;
    private static final AtomicLong LAST_USABLE_CHECK_MILLIS = new AtomicLong(0L);
    private static final long USABLE_CHECK_INTERVAL_MILLIS = 1000L;

    private record LocalKey(String cacheName, String key) {
    }

    private record LocalValue(Object value, long expiresAtNanos) {
    }

    private QuantifiedCacheCompat() {
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        cacheManager = null;
        INIT_ATTEMPTED.set(false);
        LAST_APPLIED_LIMIT_MB.set(Long.MIN_VALUE);
        LAST_CLEANUP_TICK.set(Long.MIN_VALUE);
        cachedIsUsable = false;
        LAST_USABLE_CHECK_MILLIS.set(0L);
        LIVE_STATE_CACHE.invalidateAll();
    }

    public static boolean isUsable() {
        if (!ModList.get().isLoaded("quantified")) return false;
        long now = System.currentTimeMillis();
        long lastCheck = LAST_USABLE_CHECK_MILLIS.get();
        if (now - lastCheck < USABLE_CHECK_INTERVAL_MILLIS) {
            return cachedIsUsable;
        }
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
        if (cacheName == null || key == null) {
            return loader.get();
        }

        long clampedTtlTicks = Math.max(1L, ttlTicks);
        long tickMillis = estimateMillisPerTick();
        long ttlMillis = boundedTtlMillis(clampedTtlTicks, tickMillis);
        Duration ttl = Duration.ofMillis(ttlMillis);
        long clampedMax = Math.min(65536L, Math.max(64L, maxSize));
        LocalKey localKey = new LocalKey(cacheName, key);
        long now = System.nanoTime();
        LocalValue cached = LIVE_STATE_CACHE.getIfPresent(localKey);
        if (cached != null) {
            if (cached.expiresAtNanos() > now) {
                @SuppressWarnings("unchecked")
                T value = cached.value() == NULL_VALUE ? null : (T) cached.value();
                return value;
            }
            LIVE_STATE_CACHE.invalidate(localKey);
        }

        LocalValue loaded = LIVE_STATE_CACHE.get(localKey, ignored -> {
            T value = null;
            if (isUsable()) {
                value = QuantifiedOptionalBridge.tryGetCached(
                        SoundAttractMod.MOD_ID,
                        cacheName,
                        key,
                        loader,
                        ttl,
                        clampedMax,
                        useDisk);
            }
            if (value == null) {
                value = loader.get();
            }
            return new LocalValue(value == null ? NULL_VALUE : value,
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ttlMillis));
        });
        @SuppressWarnings("unchecked")
        T value = loaded.value() == NULL_VALUE ? null : (T) loaded.value();
        return value;
    }

    private static boolean ensureInit() {
        if (cacheManager != null) return true;
        if (!INIT_ATTEMPTED.compareAndSet(false, true)) {
            return cacheManager != null;
        }
        cacheManager = QuantifiedOptionalBridge.tryFetchCacheManager(SoundAttractMod.MOD_ID);
        return cacheManager != null;
    }

    private static long boundedTtlMillis(long ttlTicks, long tickMillis) {
        long maximum = TimeUnit.DAYS.toMillis(7);
        long safeTicks = Math.max(1L, ttlTicks);
        long safeMillis = Math.max(1L, tickMillis);
        if (safeTicks > maximum / safeMillis) {
            return maximum;
        }
        return Math.max(1L, safeTicks * safeMillis);
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
        if ((tick - previousCleanupTick) < 20L
                || !LAST_CLEANUP_TICK.compareAndSet(previousCleanupTick, tick)) {
            return;
        }
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