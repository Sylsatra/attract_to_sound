package com.example.soundattract.spatial;

import com.example.soundattract.spatial.SpatialContracts.IndexedEntitySnapshot;
import com.example.soundattract.spatial.SpatialContracts.TileKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class SpatialEntityIndex {
    public static final int TILE_SIZE = 64;

    private final Map<ResourceLocation, DimensionIndex> dimensions = new HashMap<>();
    private long globalEpoch;

    public synchronized long epoch() {
        return globalEpoch;
    }

    public synchronized IndexedEntitySnapshot upsert(UUID entityUuid, long incarnation, ResourceLocation dimension, Vec3 position,
            boolean eligible, boolean alive, double health, UUID leaderUuid, UUID groupOwnerUuid, long entityEpoch) {
        TileKey tileKey = tileFor(dimension, position, globalEpoch);
        IndexedEntitySnapshot snapshot = new IndexedEntitySnapshot(entityUuid, incarnation, dimension, tileKey, position,
                eligible, alive, health, leaderUuid, groupOwnerUuid, entityEpoch);
        dimensions.computeIfAbsent(dimension, key -> new DimensionIndex()).upsert(snapshot);
        return snapshot;
    }

    public synchronized void remove(ResourceLocation dimension, UUID entityUuid) {
        DimensionIndex index = dimensions.get(dimension);
        if (index != null) {
            index.remove(entityUuid);
        }
    }

    public synchronized void unloadChunk(ResourceLocation dimension, int tileX, int tileZ) {
        DimensionIndex index = dimensions.get(dimension);
        if (index != null) {
            index.unloadChunk(tileX, tileZ);
        }
    }

    public synchronized void unloadLevel(ResourceLocation dimension) {
        dimensions.remove(dimension);
        globalEpoch++;
    }

    public synchronized void invalidateConfig() {
        dimensions.clear();
        globalEpoch++;
    }

    public synchronized IndexedEntitySnapshot get(ResourceLocation dimension, UUID entityUuid) {
        DimensionIndex index = dimensions.get(dimension);
        return index == null ? null : index.byId.get(entityUuid);
    }

    public synchronized List<IndexedEntitySnapshot> queryRadius(ResourceLocation dimension, Vec3 center, double radius) {
        if (dimension == null || center == null || radius < 0.0D) {
            return List.of();
        }
        DimensionIndex index = dimensions.get(dimension);
        if (index == null) {
            return List.of();
        }
        int minTileX = tileCoord(center.x - radius);
        int maxTileX = tileCoord(center.x + radius);
        int minTileZ = tileCoord(center.z - radius);
        int maxTileZ = tileCoord(center.z + radius);
        Set<IndexedEntitySnapshot> out = new LinkedHashSet<>();
        double radiusSq = radius * radius;
        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                List<IndexedEntitySnapshot> bucket = index.byTile.get(tileCoordKey(tileX, tileZ));
                if (bucket == null) {
                    continue;
                }
                for (IndexedEntitySnapshot snapshot : bucket) {
                    if (snapshot == null) {
                        continue;
                    }
                    double dx = snapshot.position().x - center.x;
                    double dz = snapshot.position().z - center.z;
                    if (dx * dx + dz * dz <= radiusSq) {
                        out.add(snapshot);
                    }
                }
            }
        }
        ArrayList<IndexedEntitySnapshot> result = new ArrayList<>(out);
        result.sort(Comparator.comparing((IndexedEntitySnapshot s) -> s.entityUuid().toString())
                .thenComparingLong(IndexedEntitySnapshot::incarnation)
                .thenComparingLong(IndexedEntitySnapshot::entityEpoch));
        return Collections.unmodifiableList(result);
    }

    public synchronized List<IndexedEntitySnapshot> queryHalo(ResourceLocation dimension, Vec3 center, double innerRadius, double outerRadius) {
        List<IndexedEntitySnapshot> all = queryRadius(dimension, center, outerRadius);
        if (all.isEmpty()) {
            return all;
        }
        double innerSq = innerRadius * innerRadius;
        ArrayList<IndexedEntitySnapshot> filtered = new ArrayList<>();
        for (IndexedEntitySnapshot snapshot : all) {
            double dx = snapshot.position().x - center.x;
            double dz = snapshot.position().z - center.z;
            if (dx * dx + dz * dz > innerSq) {
                filtered.add(snapshot);
            }
        }
        filtered.sort(Comparator.comparing((IndexedEntitySnapshot s) -> s.entityUuid().toString())
                .thenComparingLong(IndexedEntitySnapshot::incarnation)
                .thenComparingLong(IndexedEntitySnapshot::entityEpoch));
        return Collections.unmodifiableList(filtered);
    }

    public synchronized long estimatedBytes() {
        long count = 0L;
        for (DimensionIndex index : dimensions.values()) {
            count += index.byId.size();
        }
        return count * 256L;
    }

    public static TileKey tileFor(ResourceLocation dimension, Vec3 position, long epoch) {
        return new TileKey(dimension, tileCoord(position.x), tileCoord(position.z), epoch);
    }

    public static int tileCoord(double coordinate) {
        return (int) Math.floor(coordinate / TILE_SIZE);
    }

    private static TileCoord tileCoordKey(int tileX, int tileZ) {
        return new TileCoord(tileX, tileZ);
    }

    private static final class DimensionIndex {
        private final Map<UUID, IndexedEntitySnapshot> byId = new HashMap<>();
        private final Map<TileCoord, List<IndexedEntitySnapshot>> byTile = new HashMap<>();

        private void upsert(IndexedEntitySnapshot snapshot) {
            IndexedEntitySnapshot previous = byId.put(snapshot.entityUuid(), snapshot);
            if (previous != null) {
                List<IndexedEntitySnapshot> oldBucket = byTile.get(tileCoordKey(previous.tileKey().x(), previous.tileKey().z()));
                if (oldBucket != null) {
                    oldBucket.removeIf(item -> item != null && item.entityUuid().equals(snapshot.entityUuid()));
                }
            }
            byTile.computeIfAbsent(tileCoordKey(snapshot.tileKey().x(), snapshot.tileKey().z()), key -> new ArrayList<>()).add(snapshot);
        }

        private void remove(UUID entityUuid) {
            IndexedEntitySnapshot removed = byId.remove(entityUuid);
            if (removed == null) {
                return;
            }
            List<IndexedEntitySnapshot> bucket = byTile.get(tileCoordKey(removed.tileKey().x(), removed.tileKey().z()));
            if (bucket != null) {
                bucket.removeIf(item -> item != null && item.entityUuid().equals(entityUuid));
                if (bucket.isEmpty()) {
                    byTile.remove(tileCoordKey(removed.tileKey().x(), removed.tileKey().z()));
                }
            }
        }

        private void unloadChunk(int tileX, int tileZ) {
            byTile.entrySet().removeIf(entry -> entry.getKey().x() == tileX && entry.getKey().z() == tileZ);
            byId.entrySet().removeIf(entry -> entry.getValue().tileKey().x() == tileX && entry.getValue().tileKey().z() == tileZ);
        }
    }

    private record TileCoord(int x, int z) {
    }
}