package com.example.soundattract.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.util.concurrent.ConcurrentHashMap;
import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundOverride;
import com.example.soundattract.data.DataDrivenTags;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import com.example.soundattract.worker.WorkerScheduler.SoundCandidate;
import com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest;
import com.example.soundattract.worker.WorkerScheduler.SoundScoreResult;
import com.example.soundattract.worker.WorkSchedulerManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;

public class SoundTracker {

    private static final AtomicLong SOUND_SEQUENCE = new AtomicLong(0L);

    public static long getSoundSequence() {
        return SOUND_SEQUENCE.get();
    }

    /** Returns the current record count for diagnostics and bounded-tracker verification. */
    public static int getTrackedSoundCount() {
        readLock.lock();
        try {
            return RECENT_SOUNDS.size();
        } finally {
            readLock.unlock();
        }
    }

    /** Returns an immutable snapshot for diagnostics and safe tracker-policy verification. */
    public static List<SoundRecord> getTrackedSoundsSnapshot() {
        readLock.lock();
        try {
            return List.copyOf(RECENT_SOUNDS);
        } finally {
            readLock.unlock();
        }
    }

    public static class SoundRecord {

        public final SoundEvent sound;
        public final String soundId;
        public final BlockPos pos;
        public int ticksRemaining;
        public final String dimensionKey;
        public final double range;
        public final double weight;

        public SoundRecord(SoundEvent sound, String soundId, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.soundId = soundId;
            this.pos = pos;
            this.ticksRemaining = lifetime;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }

        public boolean isPlayerProduced() {
            if (this instanceof VirtualSoundRecord || (soundId != null && soundId.contains(":virtual#"))) {
                return true;
            }


            return false;
        }

        public SoundRecord(SoundEvent sound, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this(sound, sound != null && sound.location() != null ? sound.location().toString() : null, pos, lifetime, dimensionKey, range, weight);
        }
    }

    public static String buildIntegrationSoundId(Identifier baseId, String metadata) {
        if (baseId == null) {
            return metadata == null ? null : metadata.trim();
        }
        if (metadata == null || metadata.isBlank()) {
            return baseId.toString();
        }
        String trimmed = metadata.trim();
        StringBuilder sanitized = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch)) {
                sanitized.append('_');
                continue;
            }
            if (ch == ';' || ch == '|' || ch == ',' || ch == '#') {
                sanitized.append('/');
                continue;
            }
            sanitized.append(Character.toLowerCase(ch));
        }
        return baseId + "#" + sanitized;
    }

    public static Identifier extractBaseSoundLocation(String soundId) {
        if (soundId == null || soundId.isBlank()) {
            return null;
        }
        int idx = soundId.indexOf('#');
        String base = idx >= 0 ? soundId.substring(0, idx) : soundId;
        return Identifier.tryParse(base);
    }

    public static String extractIntegrationMetadata(String soundId) {
        if (soundId == null) {
            return null;
        }
        int idx = soundId.indexOf('#');
        if (idx < 0 || idx + 1 >= soundId.length()) {
            return null;
        }
        return soundId.substring(idx + 1);
    }

    public static class VirtualSoundRecord extends SoundRecord {

        public final UUID sourcePlayer;
        public final String animationClass;

        public VirtualSoundRecord(String soundId, BlockPos pos, int lifetime, String dimensionKey, double range, double weight, UUID sourcePlayer, String animationClass) {
            super(null, soundId, pos, lifetime, dimensionKey, range, weight);
            this.sourcePlayer = sourcePlayer;
            this.animationClass = animationClass;
        }
    }

    private static final class SoundSnapshot {
        final SoundEvent sound;
        final String soundId;
        final BlockPos pos;
        final int ticksRemaining;
        final String dimensionKey;
        final double range;
        final double weight;

        SoundSnapshot(SoundEvent sound, String soundId, BlockPos pos, int ticksRemaining, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.soundId = soundId;
            this.pos = pos;
            this.ticksRemaining = ticksRemaining;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }
    }

    private record MuffledResult(double range, double weight) {}

    private static final List<SoundRecord> RECENT_SOUNDS = new ArrayList<>();

    private record GridKey3D(int x, int y, int z) {

    }
    private record AsyncKey(UUID mobId, String dimensionKey) {}

    private static final Map<String, Map<GridKey3D, Map<String, SoundRecord>>> SPATIAL_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<String, SoundRecord> LARGE_RANGE_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<String, SoundRecord> SOUND_RECORDS_BY_ID = new ConcurrentHashMap<>();
    private static final java.util.Set<String> DEDUP_SET_A = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> DEDUP_SET_B = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static java.util.Set<String> DEDUP_THIS_TICK = DEDUP_SET_A;
    private static java.util.Set<String> DEDUP_LAST_TICK = DEDUP_SET_B;

    private static final int MAX_RUNTIME_SOUNDS_TRACKED = 65536;

    private static String recordKey(String dimensionKey, String soundId) {
        return String.valueOf(dimensionKey) + "|" + String.valueOf(soundId);
    }

    private static String recordKey(SoundRecord record) {
        return record == null ? "null|null" : recordKey(record.dimensionKey, record.soundId);
    }

    private static String storageKey(SoundRecord record) {
        return recordKey(record) + "|" + record.pos.asLong();
    }

    private static int maxTrackedSounds() {
        try {
            return Math.min(MAX_RUNTIME_SOUNDS_TRACKED,
                    Math.max(1, SoundAttractConfig.COMMON.maxSoundsTracked.get()));
        } catch (Throwable ignored) {
            return 4096;
        }
    }

    private static int dedupSetLimit() {
        try {
            return Math.max(1024, Math.min(16384, SoundAttractConfig.COMMON.maxSoundsTracked.get() * 4));
        } catch (Throwable ignored) {
            return 4096;
        }
    }

    private static void capDedupSet(java.util.Set<String> set) {
        int excess = set.size() - dedupSetLimit();
        if (excess <= 0) return;
        Iterator<String> iterator = set.iterator();
        while (excess-- > 0 && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static GridKey3D gridKey(BlockPos pos) {
        int x = pos.getX() >> 4;
        int y = pos.getY() >> 4;
        int z = pos.getZ() >> 4;
        return new GridKey3D(x, y, z);
    }

    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Lock readLock = lock.readLock();
    private static final Lock writeLock = lock.writeLock();
    private static final double LARGE_SOUND_RANGE_THRESHOLD = 64.0;
    private static final int MAX_SYNC_CANDIDATES = 24;

    private static void addRecordToCollections(SoundRecord r) {
        String key = storageKey(r);
        if (r.range > LARGE_SOUND_RANGE_THRESHOLD) {
            LARGE_RANGE_SOUNDS.put(key, r);
        } else {
            SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(gridKey(r.pos), k -> new ConcurrentHashMap<>()).put(key, r);
        }
        SOUND_RECORDS_BY_ID.put(recordKey(r), r);
    }

    private static void removeRecordFromCollections(SoundRecord r) {
        String key = storageKey(r);
        if (r.range > LARGE_SOUND_RANGE_THRESHOLD) {
            LARGE_RANGE_SOUNDS.remove(key, r);
        } else {
            Map<GridKey3D, Map<String, SoundRecord>> dimMap = SPATIAL_SOUNDS.get(r.dimensionKey);
            if (dimMap != null) {
                Map<String, SoundRecord> gridCell = dimMap.get(gridKey(r.pos));
                if (gridCell != null) {
                    gridCell.remove(key, r);
                    if (gridCell.isEmpty()) {
                        dimMap.remove(gridKey(r.pos), gridCell);
                    }
                }
                if (dimMap.isEmpty()) {
                    SPATIAL_SOUNDS.remove(r.dimensionKey, dimMap);
                }
            }
        }
        SOUND_RECORDS_BY_ID.remove(recordKey(r), r);
    }

    public static List<SoundRecord> getNearbySounds(Level level, String dim, BlockPos pos, java.util.Collection<net.minecraft.world.level.ChunkPos> additionalChunks) {
        List<SoundRecord> result = new ArrayList<>();
        Map<GridKey3D, Map<String, SoundRecord>> dimMap = SPATIAL_SOUNDS.get(dim);
        if (dimMap != null) {
            GridKey3D centerKey = gridKey(pos);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        GridKey3D neighborKey = new GridKey3D(
                                centerKey.x() + dx,
                                centerKey.y() + dy,
                                centerKey.z() + dz);
                        Map<String, SoundRecord> list = dimMap.get(neighborKey);
                        if (list != null) {
                            result.addAll(list.values());
                        }
                    }
                }
            }
        }
        for (SoundRecord r : LARGE_RANGE_SOUNDS.values()) {
            if (r.dimensionKey.equals(dim)) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * Simplified helper for integration goals to find sounds within a specific range.
     */
    public static List<SoundRecord> getNearbySounds(Level level, BlockPos pos, double range) {
        if (level == null || pos == null) return java.util.Collections.emptyList();
        
        List<SoundRecord> all = getNearbySounds(level, level.dimension().identifier().toString(), pos, null);
        double rangeSq = range * range;
        
        return all.stream()
                .filter(r -> r.pos.distSqr(pos) <= rangeSq)
                .toList();
    }

    public static void addSound(SoundEvent se,
            BlockPos pos,
            String dimensionKey,
            double range,
            double weight,
            int lifetime,
            String explicitSoundId) {

        if (pos == null || dimensionKey == null) {
            return;
        }

        String soundIdToUse = explicitSoundId;
        if (soundIdToUse == null && se != null && se.location() != null) {
            soundIdToUse = se.location().toString();
        }

        if (soundIdToUse == null || soundIdToUse.isBlank()) {
            return;
        }

        Identifier loc = extractBaseSoundLocation(soundIdToUse);

        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
                && (loc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc))) {
            return;
        }
        writeLock.lock();
        try {
            String dedupKey = null;
            if (soundIdToUse != null && pos != null && dimensionKey != null) {
                dedupKey = dimensionKey + "|" + soundIdToUse + "|" + pos.asLong();
                if (DEDUP_THIS_TICK.contains(dedupKey) || DEDUP_LAST_TICK.contains(dedupKey)) {
                    return;
                }
                DEDUP_THIS_TICK.add(dedupKey);
                capDedupSet(DEDUP_THIS_TICK);
            }
            for (Iterator<SoundRecord> it = RECENT_SOUNDS.iterator(); it.hasNext();) {
                SoundRecord existing = it.next();
                if (existing.dimensionKey.equals(dimensionKey)
                        && existing.pos.equals(pos)
                        && Objects.equals(existing.soundId, soundIdToUse)) {
                    if (existing.weight >= weight) {
                        return;
                    }
                    it.remove();
                    removeRecordFromCollections(existing);
                    break;
                }
            }

            int cap = maxTrackedSounds();
            if (RECENT_SOUNDS.size() >= cap) {
                SoundRecord worstRecord = null;
                int worstRecordIndex = -1;
                double minMetric = Double.MAX_VALUE;
                for (int i = 0; i < RECENT_SOUNDS.size(); i++) {
                    SoundRecord current = RECENT_SOUNDS.get(i);
                    double currentMetric = current.weight + (current.range / 1000.0);
                    if (currentMetric < minMetric) {
                        minMetric = currentMetric;
                        worstRecord = current;
                        worstRecordIndex = i;
                    }
                }
                double newMetric = weight + (range / 1000.0);
                if (worstRecord != null && newMetric > minMetric) {
                    RECENT_SOUNDS.remove(worstRecordIndex);
                    removeRecordFromCollections(worstRecord);
                } else {
                    return;
                }
            }
            SoundRecord record = new SoundRecord(se, soundIdToUse, pos, lifetime, dimensionKey, range, weight);
            RECENT_SOUNDS.add(record);
            addRecordToCollections(record);
            SOUND_SEQUENCE.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
    }

    public static void tick() {
        writeLock.lock();
        try {
            Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
            while (iter.hasNext()) {
                SoundRecord r = iter.next();
                r.ticksRemaining--;
                if (r.ticksRemaining <= 0) {
                    iter.remove();
                    removeRecordFromCollections(r);
                }
            }
            advanceDedupWindow();
        } finally {
            writeLock.unlock();
        }
    }

    /** Rotates the production duplicate-suppression window without expiring unrelated records. */
    static void advanceDedupWindow() {
        writeLock.lock();
        try {
            java.util.Set<String> previous = DEDUP_THIS_TICK;
            DEDUP_THIS_TICK = DEDUP_LAST_TICK;
            DEDUP_LAST_TICK = previous;
            DEDUP_THIS_TICK.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public static void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }

    public static void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        addSound(se, pos, dimensionKey, 16.0, 1.0, lifetime);
    }

    public static void addVirtualSound(BlockPos pos, String dimensionKey, double range, double weight, int lifetime, UUID sourcePlayer, String animationClass) {
        if (pos == null || dimensionKey == null) {
            return;
        }
        writeLock.lock();
        try {
            RECENT_SOUNDS.removeIf(r -> {
                boolean shouldRemove = r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight < weight;
                if (shouldRemove) {
                    removeRecordFromCollections(r);
                }
                return shouldRemove;
            });

            boolean strongerOrEqualExists = RECENT_SOUNDS.stream().anyMatch(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight >= weight);

            if (!strongerOrEqualExists) {
                if (RECENT_SOUNDS.size() >= maxTrackedSounds()) {
                    SoundRecord weakest = RECENT_SOUNDS.stream()
                            .min(java.util.Comparator.comparingDouble(r -> r.weight + r.range / 1000.0))
                            .orElse(null);
                    if (weakest == null || weight + range / 1000.0 <= weakest.weight + weakest.range / 1000.0) return;
                    RECENT_SOUNDS.remove(weakest);
                    removeRecordFromCollections(weakest);
                }
                String virtualId = "soundattract:virtual#"
                    + (sourcePlayer != null ? sourcePlayer.toString() : "unknown")
                    + "/" + (animationClass != null ? animationClass : "unknown")
                    + "/" + pos.asLong();
                VirtualSoundRecord record = new VirtualSoundRecord(virtualId, pos, lifetime, dimensionKey, range, weight, sourcePlayer, animationClass);
                RECENT_SOUNDS.add(record);
                addRecordToCollections(record);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public static void removeSoundAt(BlockPos pos, String dimensionKey) {
        writeLock.lock();
        try {
            Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
            while (iter.hasNext()) {
                SoundRecord r = iter.next();
                if (r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey)) {
                    iter.remove();
                    removeRecordFromCollections(r);
                    break;
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    private static class RaycastCacheKey {

        public final BlockPos mobPos;
        public final BlockPos soundPos;
        public final String soundId;
        public final String dimensionKey;

        public RaycastCacheKey(BlockPos mobPos, BlockPos soundPos, String soundId, String dimensionKey) {
            this.mobPos = mobPos;
            this.soundPos = soundPos;
            this.soundId = soundId;
            this.dimensionKey = dimensionKey;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RaycastCacheKey other)) {
                return false;
            }
            return java.util.Objects.equals(mobPos, other.mobPos)
                && java.util.Objects.equals(soundPos, other.soundPos)
                && java.util.Objects.equals(soundId, other.soundId)
                && java.util.Objects.equals(dimensionKey, other.dimensionKey);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(mobPos, soundPos, soundId, dimensionKey);
        }
    }
    private static final class RaycastEntry {
        final double[] result;
        final long gameTime;
        RaycastEntry(double[] result, long gameTime) {
            this.result = result;
            this.gameTime = gameTime;
        }
    }
    private static final Map<RaycastCacheKey, RaycastEntry> RAYCAST_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final double NO_MUFFLING_RANGE = 1.0;
    private static final double NO_MUFFLING_WEIGHT = 1.0;

    private static final java.util.concurrent.ConcurrentHashMap<AsyncKey, String> ASYNC_BEST_SOUND_BY_MOB = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<AsyncKey, Double> ASYNC_BEST_SOUND_RANGE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<AsyncKey, Double> ASYNC_BEST_SOUND_WEIGHT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<AsyncKey, Long> ASYNC_RESULT_GAME_TIME = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<AsyncKey, Long> LAST_SUBMIT_GAME_TIME = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<AsyncKey, Integer> LAST_CANDIDATE_HASH = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<AsyncKey, Map<String, MuffledResult>> ASYNC_MUFFLED_BY_MOB = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_ASYNC_CACHE_ENTRIES = 8192;

    public static void clearCaches() {
        RAYCAST_CACHE.clear();
        ASYNC_BEST_SOUND_BY_MOB.clear();
        ASYNC_BEST_SOUND_RANGE.clear();
        ASYNC_BEST_SOUND_WEIGHT.clear();
        ASYNC_RESULT_GAME_TIME.clear();
        LAST_SUBMIT_GAME_TIME.clear();
        LAST_CANDIDATE_HASH.clear();
        ASYNC_MUFFLED_BY_MOB.clear();
    }

    public static void onLevelUnload(net.minecraft.server.level.ServerLevel level) {
        if (level == null) return;
        String dimension = level.dimension().identifier().toString();
        writeLock.lock();
        try {
            RECENT_SOUNDS.removeIf(record -> {
                if (!dimension.equals(record.dimensionKey)) return false;
                removeRecordFromCollections(record);
                return true;
            });
            DEDUP_SET_A.removeIf(key -> key.startsWith(dimension + "|"));
            DEDUP_SET_B.removeIf(key -> key.startsWith(dimension + "|"));
            ASYNC_BEST_SOUND_BY_MOB.keySet().removeIf(key -> dimension.equals(key.dimensionKey()));
            ASYNC_BEST_SOUND_RANGE.keySet().removeIf(key -> dimension.equals(key.dimensionKey()));
            ASYNC_BEST_SOUND_WEIGHT.keySet().removeIf(key -> dimension.equals(key.dimensionKey()));
            ASYNC_RESULT_GAME_TIME.keySet().removeIf(key -> dimension.equals(key.dimensionKey()));
            LAST_SUBMIT_GAME_TIME.keySet().removeIf(key -> dimension.equals(key.dimensionKey()));
            LAST_CANDIDATE_HASH.keySet().removeIf(key -> dimension.equals(key.dimensionKey()));
            ASYNC_MUFFLED_BY_MOB.keySet().removeIf(key -> dimension.equals(key.dimensionKey()));
            RAYCAST_CACHE.keySet().removeIf(key -> dimension.equals(key.dimensionKey));
            trimAsyncCaches();
        } finally {
            writeLock.unlock();
        }
    }

    public static void onServerStopped() {
        writeLock.lock();
        try {
            RECENT_SOUNDS.clear();
            SPATIAL_SOUNDS.clear();
            LARGE_RANGE_SOUNDS.clear();
            SOUND_RECORDS_BY_ID.clear();
            DEDUP_SET_A.clear();
            DEDUP_SET_B.clear();
            SOUND_SEQUENCE.set(0L);
            clearCaches();
        } finally {
            writeLock.unlock();
        }
    }

    public static void onMobUnload(UUID mobId, net.minecraft.world.level.Level level) {
        if (mobId == null || level == null) return;
        AsyncKey key = new AsyncKey(mobId, level.dimension().identifier().toString());
        ASYNC_BEST_SOUND_BY_MOB.remove(key);
        ASYNC_BEST_SOUND_RANGE.remove(key);
        ASYNC_BEST_SOUND_WEIGHT.remove(key);
        ASYNC_RESULT_GAME_TIME.remove(key);
        LAST_SUBMIT_GAME_TIME.remove(key);
        LAST_CANDIDATE_HASH.remove(key);
        ASYNC_MUFFLED_BY_MOB.remove(key);
    }

    private static void trimAsyncCaches() {
        Set<AsyncKey> keys = new HashSet<>();
        keys.addAll(ASYNC_RESULT_GAME_TIME.keySet());
        keys.addAll(ASYNC_BEST_SOUND_BY_MOB.keySet());
        keys.addAll(ASYNC_BEST_SOUND_RANGE.keySet());
        keys.addAll(ASYNC_BEST_SOUND_WEIGHT.keySet());
        keys.addAll(LAST_SUBMIT_GAME_TIME.keySet());
        keys.addAll(LAST_CANDIDATE_HASH.keySet());
        keys.addAll(ASYNC_MUFFLED_BY_MOB.keySet());
        int excess = keys.size() - MAX_ASYNC_CACHE_ENTRIES;
        if (excess <= 0) return;
        Iterator<AsyncKey> iterator = keys.iterator();
        while (excess-- > 0 && iterator.hasNext()) {
            AsyncKey key = iterator.next();
            ASYNC_RESULT_GAME_TIME.remove(key);
            ASYNC_BEST_SOUND_BY_MOB.remove(key);
            ASYNC_BEST_SOUND_RANGE.remove(key);
            ASYNC_BEST_SOUND_WEIGHT.remove(key);
            LAST_SUBMIT_GAME_TIME.remove(key);
            LAST_CANDIDATE_HASH.remove(key);
            ASYNC_MUFFLED_BY_MOB.remove(key);
        }
    }

    private static void drainAsyncSoundScores(Level level) {
        try {
            List<SoundScoreResult> results = WorkSchedulerManager.get().drainSoundScoreResults();
            if (results == null || results.isEmpty()) return;
            for (SoundScoreResult r : results) {
                if (r == null || r.mobUuid() == null) continue;
                if (r.soundId() != null && r.dimensionKey() != null) {
                    long resultGameTime = gameTimeForDimension(level, r.dimensionKey());
                    if (resultGameTime == Long.MIN_VALUE) continue;
                    AsyncKey key = new AsyncKey(r.mobUuid(), r.dimensionKey());
                    ASYNC_BEST_SOUND_BY_MOB.put(key, r.soundId());
                    ASYNC_RESULT_GAME_TIME.put(key, resultGameTime);
                    Map<String, MuffledResult> muffled = ASYNC_MUFFLED_BY_MOB.get(key);
                    if (muffled != null) {
                        MuffledResult result = muffled.get(r.soundId());
                        if (result != null) {
                            ASYNC_BEST_SOUND_RANGE.put(key, result.range());
                            ASYNC_BEST_SOUND_WEIGHT.put(key, result.weight());
                        }
                    }
                    trimAsyncCaches();
                }
            }
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.error("[SoundTracker] drainAsyncSoundScores failed", t);
            }
        }
    }

    private static long gameTimeForDimension(Level fallback, String dimensionKey) {
        if (dimensionKey == null) return Long.MIN_VALUE;
        if (fallback != null && dimensionKey.equals(fallback.dimension().identifier().toString())) {
            return fallback.getGameTime();
        }
        if (!(fallback instanceof net.minecraft.server.level.ServerLevel serverLevel)) return Long.MIN_VALUE;
        Identifier location = Identifier.tryParse(dimensionKey);
        if (location == null) return Long.MIN_VALUE;
        net.minecraft.server.level.ServerLevel target = serverLevel.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, location));
        return target == null ? Long.MIN_VALUE : target.getGameTime();
    }

    public static double[] applyBlockMuffling(Level level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId, boolean skipBiomassMuffle) {
        if (level == null || src == null || dst == null) {
            return new double[]{origRange, origWeight};
        }
        if (!SoundAttractConfig.COMMON.enableBlockMuffling.get()) {
            return new double[]{origRange, origWeight};
        }

        boolean useCache = SoundAttractConfig.COMMON.enableRaycastCache.get();
        long raycastTtl = SoundAttractConfig.COMMON.raycastCacheTtlTicks.get();
        int raycastMax = SoundAttractConfig.COMMON.raycastCacheMaxEntries.get();
        String dimensionKey = level.dimension().identifier().toString();

        if (useCache) {
            String key = new StringBuilder(128)
                .append(dimensionKey).append('|')
                .append(soundId).append('|')
                .append(dst.getX()).append(',').append(dst.getY()).append(',').append(dst.getZ()).append('|')
                .append(src.getX()).append(',').append(src.getY()).append(',').append(src.getZ())
                .toString();

            return QuantifiedCacheCompat.getCached(
                "soundattract_raycast_muffling",
                key,
                () -> computeBlockMuffling(level, src, dst, origRange, origWeight, soundId, skipBiomassMuffle),
                raycastTtl,
                raycastMax
            );
        }

        RaycastCacheKey cacheKey = new RaycastCacheKey(
            dst,
            src,
            soundId,
            dimensionKey
        );
        if (useCache) {
            RaycastEntry existing = RAYCAST_CACHE.get(cacheKey);
            if (existing != null) {
                long age = level.getGameTime() - existing.gameTime;
                if (age <= raycastTtl) {
                    return existing.result;
                } else {
                    RAYCAST_CACHE.remove(cacheKey);
                }
            }
        }

        double[] finalResult = computeBlockMuffling(level, src, dst, origRange, origWeight, soundId, skipBiomassMuffle);
        if (useCache) {
            RAYCAST_CACHE.put(cacheKey, new RaycastEntry(finalResult, level.getGameTime()));
            if (RAYCAST_CACHE.size() > raycastMax) {
                long now = level.getGameTime();
                Iterator<Map.Entry<RaycastCacheKey, RaycastEntry>> it = RAYCAST_CACHE.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<RaycastCacheKey, RaycastEntry> e = it.next();
                    if ((now - e.getValue().gameTime) > raycastTtl) {
                        it.remove();
                    }
                }
                if (RAYCAST_CACHE.size() > raycastMax) {
                    int toRemove = RAYCAST_CACHE.size() - raycastMax;
                    Iterator<Map.Entry<RaycastCacheKey, RaycastEntry>> it2 = RAYCAST_CACHE.entrySet().iterator();
                    for (int i = 0; i < toRemove && it2.hasNext(); i++) {
                        it2.next();
                        it2.remove();
                    }
                }
            }
        }
        return finalResult;
    }

    private static double[] computeBlockMuffling(Level level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId, boolean skipBiomassMuffle) {
        if (!com.example.soundattract.los.OptimizedLOS.tryConsumeMufflingBudget()) {
            return new double[]{origRange, origWeight};
        }

        double[] result = com.example.soundattract.los.OptimizedLOS.computeMufflingDda(
                level, src, dst, origRange, origWeight, skipBiomassMuffle);

        int blocksHit = 0;
        if (result[0] < origRange || result[1] < origWeight) {
            blocksHit = 1;
        }
        if (SoundAttractConfig.COMMON.debugLogging.get() && blocksHit > 0) {
            Soundattract.LOGGER.debug("Muffling (DDA) for sound {} from {} to {}: Range: {} -> {}, Weight: {} -> {}",
                    soundId, src, dst, origRange, result[0], origWeight, result[1]);
        }

        return result;
    }

    public static boolean isCustomWool(BlockState state, Block block, Level level, BlockPos pos) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean inConfig = id != null && SoundAttractConfig.CUSTOM_WOOL_BLOCKS_CACHE.contains(id);

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        boolean inTag = false;
        if (enableDataDriven) {
            try {
                inTag = state.is(DataDrivenTags.MUFFLING_WOOL);
            } catch (Exception ignored) {}
        }

        if (!enableDataDriven) {
            if (inConfig) return true;
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                if (inTag) return true;
                if (inConfig) return true;
            } else {
                if (inConfig) return true;
                if (inTag) return true;
            }
        }

        try {
            return state.is(BlockTags.WOOL);
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("Exception checking BlockTags.WOOL for block {} at {}. Defaulting to false.", BuiltInRegistries.BLOCK.getKey(block), pos, e);
            }
            return false;
        }
    }

    public static boolean isCustomSolid(BlockState state, Block block, Level level, BlockPos pos) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean inConfig = id != null && SoundAttractConfig.CUSTOM_SOLID_BLOCKS_CACHE.contains(id);

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        boolean inTag = false;
        if (enableDataDriven) {
            try {
                inTag = state.is(DataDrivenTags.MUFFLING_SOLID);
            } catch (Exception ignored) {}
        }

        if (!enableDataDriven) {
            if (inConfig) return true;
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                if (inTag) return true;
                if (inConfig) return true;
            } else {
                if (inConfig) return true;
                if (inTag) return true;
            }
        }

        try {
            return state.isSolidRender();
        } catch (NullPointerException npe) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("NPE in state.isSolidRender() for block {} at {}. Defaulting to solid.", BuiltInRegistries.BLOCK.getKey(block), pos, npe);
            }
            return true;
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.error("Error in state.isSolidRender() for block {} at {}. Defaulting to solid.", BuiltInRegistries.BLOCK.getKey(block), pos, e);
            }
            return true;
        }
    }

    public static boolean isCustomNonSolid(BlockState state, Block block, Level level, BlockPos pos) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean inConfig = id != null && SoundAttractConfig.CUSTOM_NON_SOLID_BLOCKS_CACHE.contains(id);

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        boolean inTag = false;
        if (enableDataDriven) {
            try {
                inTag = state.is(DataDrivenTags.MUFFLING_NON_SOLID);
            } catch (Exception ignored) {}
        }

        if (!enableDataDriven) {
            if (inConfig) return true;
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                if (inTag) return true;
                if (inConfig) return true;
            } else {
                if (inConfig) return true;
                if (inTag) return true;
            }
        }

        boolean isNormallySolid;
        try {
            isNormallySolid = state.isSolidRender();
        } catch (NullPointerException npe) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("NPE in state.isSolidRender() for block {} at {}. Defaulting to solid (meaning not 'non-solid').", BuiltInRegistries.BLOCK.getKey(block), pos, npe);
            }
            isNormallySolid = true;
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.error("Error in state.isSolidRender() for block {} at {}. Defaulting to solid.", BuiltInRegistries.BLOCK.getKey(block), pos, e);
            }
            isNormallySolid = true;
        }
        return !isNormallySolid;
    }

    public static boolean isCustomThin(BlockState state, Block block, Level level, BlockPos pos) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean inConfig = id != null && SoundAttractConfig.CUSTOM_THIN_BLOCKS_CACHE.contains(id);

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        boolean inTag = false;
        if (enableDataDriven) {
            try {
                inTag = state.is(DataDrivenTags.MUFFLING_THIN);
            } catch (Exception ignored) {}
        }

        if (!enableDataDriven) {
            if (inConfig) return true;
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                if (inTag) return true;
                if (inConfig) return true;
            } else {
                if (inConfig) return true;
                if (inTag) return true;
            }
        }

        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") || path.contains("fence")
                || path.contains("trapdoor") || path.contains("door") || path.contains("ladder") || path.contains("scaffolding")
                || path.contains("rail");
    }

    public static boolean isCustomLiquid(BlockState state, Block block, Level level, BlockPos pos) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        boolean inConfig = id != null && SoundAttractConfig.CUSTOM_LIQUID_BLOCKS_CACHE.contains(id);

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        boolean inTag = false;
        if (enableDataDriven) {
            try {
                inTag = state.is(DataDrivenTags.MUFFLING_LIQUID);
            } catch (Exception ignored) {}
        }

        if (!enableDataDriven) {
            return inConfig;
        }

        String priority = SoundAttractConfig.COMMON.datapackPriority.get();
        boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
        if (datapackOverConfig) {
            return inTag || inConfig;
        } else {
            return inConfig || inTag;
        }
    }

    public static SoundRecord findNearestSound(Mob mob, Level level, BlockPos mobPos, Vec3 mobEyePos) {
        return findNearestSound(mob, level, mobPos, mobEyePos, null);
    }

    public static SoundRecord findNearestSound(Mob mob, Level level, BlockPos mobPos, Vec3 eyePos, String currentTargetSoundId) {
        return findNearestSound(mob, level, mobPos, eyePos, currentTargetSoundId, null, false);
    }

    public static SoundRecord findNearestSound(Mob mob, Level level, BlockPos mobPos, Vec3 eyePos, String currentTargetSoundId, java.util.Collection<net.minecraft.world.level.ChunkPos> additionalChunks, boolean skipBiomassMuffle) {
        if (mob == null || level == null || mobPos == null) {
            return null;
        }
        List<SoundSnapshot> snapshotSounds;
        readLock.lock();
        try {
            try {
                String dimensionKey = level.dimension().identifier().toString();
                AsyncKey asyncKey = new AsyncKey(mob.getUUID(), dimensionKey);
                String asyncBestId = ASYNC_BEST_SOUND_BY_MOB.get(asyncKey);
                Long when = ASYNC_RESULT_GAME_TIME.get(asyncKey);
                long asyncTtl = SoundAttractConfig.COMMON.asyncResultTtlTicks.get();

                if (asyncBestId != null && when != null && (level.getGameTime() - when) <= asyncTtl) {
                    SoundRecord asyncPick = SOUND_RECORDS_BY_ID.get(recordKey(dimensionKey, asyncBestId));
                    if (asyncPick != null && asyncPick.pos != null) {
                        Double asyncRange = ASYNC_BEST_SOUND_RANGE.get(asyncKey);
                        Double asyncWeight = ASYNC_BEST_SOUND_WEIGHT.get(asyncKey);
                        double effectiveRange = asyncRange != null ? asyncRange.doubleValue() : asyncPick.range;
                        double distanceSq = asyncPick.pos.distSqr(mobPos);
                        if (distanceSq <= effectiveRange * effectiveRange) {
                            double effectiveWeight = asyncWeight != null ? asyncWeight.doubleValue() : asyncPick.weight;
                            return new SoundRecord(asyncPick.sound, asyncPick.soundId, asyncPick.pos, asyncPick.ticksRemaining, asyncPick.dimensionKey, effectiveRange, effectiveWeight);
                        }
                    }
                } else if (when != null && (level.getGameTime() - when) > asyncTtl) {
                    ASYNC_BEST_SOUND_BY_MOB.remove(asyncKey);
                    ASYNC_BEST_SOUND_RANGE.remove(asyncKey);
                    ASYNC_BEST_SOUND_WEIGHT.remove(asyncKey);
                    ASYNC_MUFFLED_BY_MOB.remove(asyncKey);
                }
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.error("[findNearest] reading async result failed, falling back to sync", t);
                }
            }

            String dimensionKey = level.dimension().identifier().toString();
            List<SoundRecord> nearbySounds = getNearbySounds(level, dimensionKey, mobPos, additionalChunks);
            if (nearbySounds.isEmpty()) {
                return null;
            }
            snapshotSounds = new ArrayList<>(nearbySounds.size());
            for (SoundRecord r : nearbySounds) {
                if (r == null || r.pos == null) continue;
                snapshotSounds.add(new SoundSnapshot(r.sound, r.soundId, r.pos, r.ticksRemaining, r.dimensionKey, r.range, r.weight));
            }
        } finally {
            readLock.unlock();
        }

        drainAsyncSoundScores(level);
        if (snapshotSounds.isEmpty()) {
            return null;
        }
        com.example.soundattract.config.MobProfile2 profile = SoundAttractConfig.getMatchingProfile(mob);
        SoundSnapshot bestSound = null;
        double highestWeight = -1.0;
        double closestDistSqr = Double.MAX_VALUE;
        double bestSoundEffectiveRange = 0;
        double bestSoundEffectiveWeight = 0;
        double noveltyBonusValue = SoundAttractConfig.COMMON.soundNoveltyBonusWeight.get();
        int noveltyTicks = SoundAttractConfig.COMMON.soundNoveltyTimeTicks.get();
        int maxLifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

        Map<String, SoundSnapshot> candidateRecordById = new HashMap<>(Math.min(snapshotSounds.size(), 128));

        SoundSnapshot[] shortlist = new SoundSnapshot[MAX_SYNC_CANDIDATES];
        double[] shortlistRange = new double[MAX_SYNC_CANDIDATES];
        double[] shortlistWeight = new double[MAX_SYNC_CANDIDATES];
        double[] shortlistNovelty = new double[MAX_SYNC_CANDIDATES];
        double[] shortlistScore = new double[MAX_SYNC_CANDIDATES];
        double[] shortlistDistSqr = new double[MAX_SYNC_CANDIDATES];
        int shortlistCount = 0;

        for (SoundSnapshot r : snapshotSounds) {
            String soundIdStr = r.soundId;
            Identifier rl = extractBaseSoundLocation(soundIdStr);
            String integrationMeta = extractIntegrationMetadata(soundIdStr);
            if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && (rl == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(rl))) {
                continue;
            }
            double effectiveInitialRange = r.range;
            double effectiveInitialWeight = r.weight;
            if (profile != null && soundIdStr != null) {
                Optional<SoundOverride> ov = profile.getSoundOverride(rl);
                if (ov.isPresent()) {
                    effectiveInitialRange = ov.get().getRange();
                    effectiveInitialWeight = ov.get().getWeight();
                }
            }
            double distSqr = mobPos.distSqr(r.pos);
            double rangeSqrQuick = effectiveInitialRange * effectiveInitialRange;
            if (distSqr > rangeSqrQuick) {
                continue;
            }

            double noveltyBonus = 0.0;
            if (r.ticksRemaining > (maxLifetime - noveltyTicks)) {
                noveltyBonus = noveltyBonusValue;
            }
            double approxScore = effectiveInitialWeight + noveltyBonus;

            if (shortlistCount < MAX_SYNC_CANDIDATES) {
                shortlist[shortlistCount] = r;
                shortlistRange[shortlistCount] = effectiveInitialRange;
                shortlistWeight[shortlistCount] = effectiveInitialWeight;
                shortlistNovelty[shortlistCount] = noveltyBonus;
                shortlistScore[shortlistCount] = approxScore;
                shortlistDistSqr[shortlistCount] = distSqr;
                shortlistCount++;
            } else {
                int worstIdx = 0;
                double worstScore = shortlistScore[0];
                double worstDist = shortlistDistSqr[0];
                for (int i = 1; i < shortlistCount; i++) {
                    double sc = shortlistScore[i];
                    if (sc < worstScore || (Math.abs(sc - worstScore) < 0.001 && shortlistDistSqr[i] > worstDist)) {
                        worstIdx = i;
                        worstScore = sc;
                        worstDist = shortlistDistSqr[i];
                    }
                }
                if (approxScore > worstScore || (Math.abs(approxScore - worstScore) < 0.001 && distSqr < worstDist)) {
                    shortlist[worstIdx] = r;
                    shortlistRange[worstIdx] = effectiveInitialRange;
                    shortlistWeight[worstIdx] = effectiveInitialWeight;
                    shortlistNovelty[worstIdx] = noveltyBonus;
                    shortlistScore[worstIdx] = approxScore;
                    shortlistDistSqr[worstIdx] = distSqr;
                }
            }

            if (soundIdStr != null) {
                SoundSnapshot newRec = new SoundSnapshot(r.sound, r.soundId, r.pos, r.ticksRemaining, r.dimensionKey, effectiveInitialRange, effectiveInitialWeight);
                SoundSnapshot prev = candidateRecordById.get(soundIdStr);
                if (prev == null) {
                    candidateRecordById.put(soundIdStr, newRec);
                } else {
                    boolean better = newRec.weight > prev.weight;
                    if (!better && Math.abs(newRec.weight - prev.weight) < 1e-6) {
                        double newDistSq = newRec.pos.distSqr(mobPos);
                        double prevDistSq = prev.pos.distSqr(mobPos);
                        better = newDistSq < prevDistSq;
                    }
                    if (better) {
                        candidateRecordById.put(soundIdStr, newRec);
                    }
                }
            }
        }

            final int MAX_ASYNC_CANDIDATES = 64;
            java.util.List<SoundSnapshot> asyncFiltered = new java.util.ArrayList<>();
            if (!candidateRecordById.isEmpty()) {
                java.util.List<SoundSnapshot> uniques = new java.util.ArrayList<>(candidateRecordById.values());
                uniques.sort((a, b) -> {
                    double aNovelty = (a.ticksRemaining > (maxLifetime - noveltyTicks)) ? noveltyBonusValue : 0.0;
                    double bNovelty = (b.ticksRemaining > (maxLifetime - noveltyTicks)) ? noveltyBonusValue : 0.0;
                    double aScore = a.weight + aNovelty;
                    double bScore = b.weight + bNovelty;
                    int cmp = Double.compare(bScore, aScore);
                    if (cmp != 0) return cmp;
                    double aDist = mobPos.distSqr(a.pos);
                    double bDist = mobPos.distSqr(b.pos);
                    return Double.compare(aDist, bDist);
                });
                long now = level.getGameTime();
                int limit = Math.min(MAX_ASYNC_CANDIDATES, uniques.size());
                for (int i = 0; i < limit; i++) {
                    SoundSnapshot rec = uniques.get(i);
                    asyncFiltered.add(rec);
                }
            }

            Map<String, MuffledResult> muffledBySoundId = new HashMap<>();
            Map<String, Long> muffledPosBySoundId = new HashMap<>();
            java.util.List<SoundCandidate> asyncScoringCandidates = new java.util.ArrayList<>(asyncFiltered.size());
            if (!asyncFiltered.isEmpty()) {
                long now = level.getGameTime();
                for (SoundSnapshot rec : asyncFiltered) {
                    String soundIdStr = rec.soundId;
                    double[] muffled = applyBlockMuffling(level, rec.pos, mobPos, rec.range, rec.weight,
                        soundIdStr != null ? soundIdStr : "unknown", skipBiomassMuffle);
                    double muffledRange = muffled[0];
                    double muffledWeight = muffled[1];
                    if (soundIdStr != null) {
                        muffledBySoundId.put(soundIdStr, new MuffledResult(muffledRange, muffledWeight));
                        muffledPosBySoundId.put(soundIdStr, rec.pos.asLong());
                    }
                    long ageTicks = Math.max(0L, maxLifetime - rec.ticksRemaining);
                    long occurredAt = Math.max(0L, now - ageTicks);
                    asyncScoringCandidates.add(new SoundCandidate(
                        soundIdStr,
                        rec.pos.getX() + 0.5, rec.pos.getY() + 0.5, rec.pos.getZ() + 0.5,
                        occurredAt,
                        muffledRange,
                        muffledWeight,
                        1.0
                    ));
                }
            }

            int[] order = new int[shortlistCount];
            for (int i = 0; i < shortlistCount; i++) order[i] = i;
            for (int i = 0; i < shortlistCount - 1; i++) {
                int best = i;
                for (int j = i + 1; j < shortlistCount; j++) {
                    double bestScore = shortlistScore[order[best]];
                    double candidateScore = shortlistScore[order[j]];
                    if (candidateScore > bestScore) {
                        best = j;
                    } else if (Math.abs(candidateScore - bestScore) < 0.001) {
                        double bestDist = shortlistDistSqr[order[best]];
                        double candidateDist = shortlistDistSqr[order[j]];
                        if (candidateDist < bestDist) {
                            best = j;
                        }
                    }
                }
                int tmp = order[i];
                order[i] = order[best];
                order[best] = tmp;
            }

            for (int i = 0; i < shortlistCount; i++) {
                int idx = order[i];
                SoundSnapshot r = shortlist[idx];
                double initialRange = shortlistRange[idx];
                double initialWeight = shortlistWeight[idx];
                double noveltyBonus = shortlistNovelty[idx];

                if (highestWeight >= 0 && (initialWeight + noveltyBonus) <= (highestWeight - 1e-6)) {
                    continue;
                }

                String soundIdStr = r.soundId;
                double muffledRange;
                double muffledWeight;
                MuffledResult cachedMuffled = soundIdStr != null ? muffledBySoundId.get(soundIdStr) : null;
                Long cachedPos = soundIdStr != null ? muffledPosBySoundId.get(soundIdStr) : null;
                if (cachedMuffled != null && cachedPos != null && cachedPos.longValue() == r.pos.asLong()) {
                    muffledRange = cachedMuffled.range();
                    muffledWeight = cachedMuffled.weight();
                } else {
                    double[] muffled = applyBlockMuffling(level, r.pos, mobPos, initialRange, initialWeight, soundIdStr != null ? soundIdStr : "unknown", skipBiomassMuffle);
                    muffledRange = muffled[0];
                    muffledWeight = muffled[1];
                }
                double distSqr = mobPos.distSqr(r.pos);
                double rangeSqr = muffledRange * muffledRange;
                if (distSqr > rangeSqr) {
                    continue;
                }
                double finalComparisonWeight = muffledWeight + noveltyBonus;
                if (finalComparisonWeight > highestWeight || (Math.abs(finalComparisonWeight - highestWeight) < 0.001 && distSqr < closestDistSqr)) {
                    highestWeight = finalComparisonWeight;
                    closestDistSqr = distSqr;
                    bestSound = r;
                    bestSoundEffectiveRange = muffledRange;
                    bestSoundEffectiveWeight = finalComparisonWeight;
                }
            }

            java.util.List<SoundCandidate> toSubmit = asyncScoringCandidates;
            int candidateHash = 1;
            for (SoundCandidate c : toSubmit) {
                int h = 17;
                h = 31 * h + (c.soundId == null ? 0 : c.soundId.hashCode());
                h = 31 * h + (int) Math.round(c.x * 10);
                h = 31 * h + (int) Math.round(c.z * 10);
                h = 31 * h + (int) Math.round(c.range * 100);
                h = 31 * h + (int) Math.round(c.weight * 100);
                candidateHash = 31 * candidateHash + h;
            }
            if (currentTargetSoundId != null) {
                candidateHash = 31 * candidateHash + currentTargetSoundId.hashCode();
            }

            try {
                if (!asyncScoringCandidates.isEmpty()) {
                    long now = level.getGameTime();
                    AsyncKey asyncKey = new AsyncKey(mob.getUUID(), level.dimension().identifier().toString());
                    Long lastSubmit = LAST_SUBMIT_GAME_TIME.get(asyncKey);
                    Integer lastHash = LAST_CANDIDATE_HASH.get(asyncKey);
                    Long lastAsyncWhen = ASYNC_RESULT_GAME_TIME.get(asyncKey);
                    long asyncTtl = SoundAttractConfig.COMMON.asyncResultTtlTicks.get();
                    long submitCooldown = SoundAttractConfig.COMMON.soundScoringSubmitCooldownTicks.get();
                    boolean hasFreshAsync = lastAsyncWhen != null && (now - lastAsyncWhen) <= asyncTtl;
                    boolean withinCooldown = lastSubmit != null && (now - lastSubmit) < submitCooldown;
                    boolean unchanged = lastHash != null && lastHash.intValue() == candidateHash;
                    if (!withinCooldown || !hasFreshAsync || !unchanged) {
                    double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
                    List<SoundScoreRequest> batch = java.util.Collections.singletonList(
                        new SoundScoreRequest(
                            mob.getUUID(),
                            mobPos.getX() + 0.5, mobPos.getY() + 0.5, mobPos.getZ() + 0.5,
                            level.getGameTime(),
                            currentTargetSoundId,
                            level.dimension().identifier().toString(),
                            toSubmit,
                            switchRatio,
                            noveltyBonusValue,
                            noveltyTicks
                        )
                    );
                    if (!muffledBySoundId.isEmpty()) {
                        ASYNC_MUFFLED_BY_MOB.put(asyncKey, muffledBySoundId);
                    } else {
                        ASYNC_MUFFLED_BY_MOB.remove(asyncKey);
                    }
                    WorkSchedulerManager.get().submitSoundScore(batch);
                        LAST_SUBMIT_GAME_TIME.put(asyncKey, now);
                        LAST_CANDIDATE_HASH.put(asyncKey, candidateHash);
                        trimAsyncCaches();
                    }
                }
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.error("[findNearest] submitSoundScore failed", t);
                }
            }

        if (bestSound != null) {
            if (bestSound.pos == null) {
                return null;
            }
            return new SoundRecord(bestSound.sound, bestSound.soundId, bestSound.pos, bestSound.ticksRemaining, bestSound.dimensionKey, bestSoundEffectiveRange, bestSoundEffectiveWeight);
        }
        return null;
    }
}
