package com.example.soundattract.util;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, bounded, TTL-based cache for per-mob scent query results.
 * Avoids redundant chunk scans when AttractionGoal evaluates many mobs per tick.
 */
public final class ScentQueryCache {

    public record Result(long tickStored, boolean hasFresh, double strongestStrength) {
        public static final Result EMPTY = new Result(Long.MIN_VALUE, false, 0.0);
    }

    private static final ConcurrentHashMap<UUID, Result> CACHE = new ConcurrentHashMap<>();

    private ScentQueryCache() {}

    public static Result getCached(UUID mobId, long nowTick, int ttl) {
        if (mobId == null) return null;
        Result r = CACHE.get(mobId);
        if (r == null) return null;
        if (nowTick - r.tickStored() > ttl) return null;
        return r;
    }

    public static void put(UUID mobId, Result result) {
        if (mobId == null || result == null) return;
        CACHE.put(mobId, result);
    }

    public static void invalidate(UUID mobId) {
        if (mobId == null) return;
        CACHE.remove(mobId);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    /**
     * Periodic cleanup. Removes expired entries; if still over maxEntries, drops oldest.
     */
    public static void cleanup(long nowTick, int ttl, int maxEntries) {
        try {
            Iterator<Map.Entry<UUID, Result>> it = CACHE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Result> e = it.next();
                Result r = e.getValue();
                if (r == null || nowTick - r.tickStored() > ttl) {
                    it.remove();
                }
            }
            int over = CACHE.size() - maxEntries;
            if (over > 0) {
                CACHE.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(
                        (a, b) -> Long.compare(a.tickStored(), b.tickStored())))
                    .limit(over)
                    .forEach(e -> CACHE.remove(e.getKey()));
            }
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[ScentQueryCache] cleanup failed: {}", t.toString());
            }
        }
    }
}
