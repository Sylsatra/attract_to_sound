package com.example.soundattract.util;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

/**
 * Thread-safe, bounded, TTL-based cache for per-mob scent query results.
 * Avoids redundant chunk scans when AttractionGoal evaluates many mobs per tick.
 */
public final class ScentQueryCache {

    public record Result(long tickStored, boolean hasFresh, double strongestStrength) {
        public static final Result EMPTY = new Result(Long.MIN_VALUE, false, 0.0);
    }

    private record Key(UUID mobId, Identifier dimension) {}

    private static final ConcurrentHashMap<Key, Result> CACHE = new ConcurrentHashMap<>();

    private ScentQueryCache() {}

    public static Result getCached(UUID mobId, Identifier dimension, long nowTick, int ttl) {
        if (mobId == null || dimension == null) return null;
        Key key = new Key(mobId, dimension);
        Result r = CACHE.get(key);
        if (r == null) return null;
        long age = nowTick - r.tickStored();
        if (r.tickStored() == Long.MIN_VALUE || age < 0L || age > ttl) {
            CACHE.remove(key, r);
            return null;
        }
        return r;
    }

    public static void put(UUID mobId, Identifier dimension, Result result) {
        if (mobId == null || dimension == null || result == null || result.tickStored() == Long.MIN_VALUE) return;
        CACHE.put(new Key(mobId, dimension), result);
    }

    public static void invalidate(UUID mobId, Identifier dimension) {
        if (mobId == null || dimension == null) return;
        CACHE.remove(new Key(mobId, dimension));
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    /** Periodic cleanup removes expired dimension entries and enforces the global capacity. */
    public static void cleanup(Identifier dimension, long nowTick, int ttl, int maxEntries) {
        if (dimension == null) return;
        Iterator<Map.Entry<Key, Result>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Result> e = it.next();
            Result r = e.getValue();
            if (e.getKey().dimension().equals(dimension)
                    && (r == null || r.tickStored() == Long.MIN_VALUE
                    || nowTick < r.tickStored() || nowTick - r.tickStored() > ttl)) {
                it.remove();
            }
        }
        int over = CACHE.size() - Math.max(1, maxEntries);
        if (over > 0) {
            Iterator<Key> keys = CACHE.keySet().iterator();
            while (over > 0 && keys.hasNext()) {
                keys.next();
                keys.remove();
                over--;
            }
        }
    }
}
