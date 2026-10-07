package com.example.soundattract.util;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.UUID;
import java.time.Duration;
import net.minecraft.resources.ResourceLocation;

public final class ScentQueryCache {

    public record Result(long tickStored, boolean hasFresh, double strongestStrength) {
        public static final Result EMPTY = new Result(Long.MIN_VALUE, false, 0.0);
    }

    private record Key(UUID mobId, ResourceLocation dimension) {
    }

    private static final int MAX_ENTRIES = 65536;
    private static final Cache<Key, Result> CACHE = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();

    private ScentQueryCache() {}

    public static Result getCached(UUID mobId, ResourceLocation dimension, long nowTick, int ttl) {
        if (mobId == null || dimension == null) return null;
        Result r = CACHE.getIfPresent(new Key(mobId, dimension));
        if (r == null) return null;
        if (nowTick - r.tickStored() > ttl) return null;
        return r;
    }

    public static void put(UUID mobId, ResourceLocation dimension, Result result) {
        if (mobId == null || dimension == null || result == null) return;
        CACHE.put(new Key(mobId, dimension), result);
    }

    public static void invalidate(UUID mobId) {
        if (mobId == null) return;
        CACHE.asMap().keySet().removeIf(key -> mobId.equals(key.mobId()));
    }

    public static void clear() {
        CACHE.invalidateAll();
    }

    public static int size() {
        return CACHE.asMap().size();
    }


    public static void cleanup(long nowTick, int ttl, int maxEntries) {
        try {
            CACHE.asMap().entrySet().removeIf(e -> {
                Result r = e.getValue();
                return r == null || nowTick - r.tickStored() > ttl;
            });
            int boundedMaxEntries = Math.max(0, Math.min(MAX_ENTRIES, maxEntries));
            int over = CACHE.asMap().size() - boundedMaxEntries;
            if (over > 0) {
                java.util.Iterator<Key> iterator = CACHE.asMap().keySet().iterator();
                while (over-- > 0 && iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        } catch (Throwable t) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                SoundAttractMod.LOGGER.warn("[ScentQueryCache] cleanup failed: {}", t.toString());
            }
        }
    }
}