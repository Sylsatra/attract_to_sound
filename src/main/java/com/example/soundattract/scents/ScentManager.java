package com.example.soundattract.scents;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.example.soundattract.config.SoundAttractConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class ScentManager {
    public static final Capability<ScentManager> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {});
    private static final int MAX_NODES_PER_CHUNK = 512;
    private static final int MAX_PERSISTED_CHUNKS = 4096;
    private static final int CLEANUP_CHUNKS_PER_PASS = 64;

    private final Level level;
    private int scentCleanupCursor;
    private int transientCleanupCursor;

    private final Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> scentMap = Caffeine.newBuilder()
            .maximumSize(MAX_PERSISTED_CHUNKS)
            .<ChunkPos, ConcurrentLinkedQueue<ScentNode>>build()
            .asMap();
    private final Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> transientScentMap = Caffeine.newBuilder()
            .maximumSize(MAX_PERSISTED_CHUNKS)
            .<ChunkPos, ConcurrentLinkedQueue<ScentNode>>build()
            .asMap();
    private final Cache<UUID, Set<ChunkPos>> windChunksByOwner = Caffeine.newBuilder()
            .maximumSize(4096)
            .removalListener((UUID owner, Set<ChunkPos> chunks, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                if (owner == null || chunks == null) {
                    return;
                }
                for (ChunkPos chunkPos : chunks) {
                    ConcurrentLinkedQueue<ScentNode> queue = transientScentMap.get(chunkPos);
                    if (queue != null) {
                        queue.removeIf(node -> owner.equals(node.getOwnerUUID())
                                && node.getSourceType() == ScentSourceType.PLAYER_WIND);
                        if (queue.isEmpty()) {
                            transientScentMap.remove(chunkPos, queue);
                        }
                    }
                }
            })
            .build();

    public ScentManager(Level level) {
        this.level = level;
    }

    public void addScentNode(ScentNode node) {
        if (!isUsableNode(node)) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(node.getBlockPos());
        ConcurrentLinkedQueue<ScentNode> queue = scentMap.computeIfAbsent(chunkPos, k -> new ConcurrentLinkedQueue<>());
        queue.add(node);
        while (queue.size() > MAX_NODES_PER_CHUNK) {
            queue.poll();
        }
    }

    public synchronized void replaceWindNodes(UUID owner, List<ScentNode> nodes) {
        if (owner == null) {
            return;
        }
        Set<ChunkPos> previousChunks = windChunksByOwner.asMap().remove(owner);
        if (previousChunks != null) {
            for (ChunkPos chunkPos : previousChunks) {
                ConcurrentLinkedQueue<ScentNode> queue = transientScentMap.get(chunkPos);
                if (queue == null) {
                    continue;
                }
                queue.removeIf(node -> owner.equals(node.getOwnerUUID())
                        && node.getSourceType() == ScentSourceType.PLAYER_WIND);
                if (queue.isEmpty()) {
                    transientScentMap.remove(chunkPos, queue);
                }
            }
        }
        if (nodes == null) {
            return;
        }
        for (ScentNode node : nodes) {
            if (!isUsableNode(node)) {
                continue;
            }
            ChunkPos chunkPos = new ChunkPos(node.getBlockPos());
            ConcurrentLinkedQueue<ScentNode> queue = transientScentMap.computeIfAbsent(chunkPos, key -> new ConcurrentLinkedQueue<>());
            queue.add(node);
            windChunksByOwner.asMap().computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(chunkPos);
            while (queue.size() > MAX_NODES_PER_CHUNK) {
                queue.poll();
            }
        }
    }

    public void clearWindNodes(UUID owner) {
        replaceWindNodes(owner, null);
    }


    public List<ScentNode> getNodesInArea(ChunkPos centerChunk) {
        List<ScentNode> result = new ArrayList<>();
        if (centerChunk == null) {
            return result;
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                ChunkPos pos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);

                if (level.isLoaded(pos.getWorldPosition())) {
                    ConcurrentLinkedQueue<ScentNode> nodes = scentMap.get(pos);
                    if (nodes != null) {
                        result.addAll(nodes);
                    }
                    ConcurrentLinkedQueue<ScentNode> transientNodes = transientScentMap.get(pos);
                    if (transientNodes != null) {
                        result.addAll(transientNodes);
                    }
                }
            }
        }
        return result;
    }


    public void cleanUpChunk(ChunkPos chunkPos, long currentTime, long maxDuration) {
        if (chunkPos == null) {
            return;
        }
        cleanQueue(scentMap, chunkPos, currentTime, maxDuration);
        cleanQueue(transientScentMap, chunkPos, currentTime, maxDuration);
    }

    public void cleanupExpired(long currentTime, long maxDuration) {
        scentCleanupCursor = cleanupMap(scentMap, scentCleanupCursor, currentTime, maxDuration);
        transientCleanupCursor = cleanupMap(transientScentMap, transientCleanupCursor, currentTime, maxDuration);
    }

    private int cleanupMap(Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> map,
                           int cursor, long currentTime, long maxDuration) {
        List<ChunkPos> keys = new ArrayList<>(map.keySet());
        if (keys.isEmpty()) {
            return 0;
        }
        int start = Math.floorMod(cursor, keys.size());
        int count = Math.min(CLEANUP_CHUNKS_PER_PASS, keys.size());
        for (int i = 0; i < count; i++) {
            ChunkPos chunkPos = keys.get((start + i) % keys.size());
            cleanQueue(map, chunkPos, currentTime, maxDuration);
        }
        return (start + count) % Math.max(1, keys.size());
    }

    private static void cleanQueue(Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> map,
                                   ChunkPos chunkPos, long currentTime, long maxDuration) {
        ConcurrentLinkedQueue<ScentNode> nodes = map.get(chunkPos);
        if (nodes == null) return;
        nodes.removeIf(node -> node == null || currentTime - node.getTimestamp() > maxDuration);
        if (nodes.isEmpty()) {
            map.remove(chunkPos, nodes);
        }
    }

    private static boolean isUsableNode(ScentNode node) {
        if (node == null || node.getPosition() == null || !Float.isFinite(node.getStrength())) {
            return false;
        }
        Vec3 position = node.getPosition();
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }

    public synchronized void clear() {
        scentMap.clear();
        transientScentMap.clear();
        windChunksByOwner.invalidateAll();
        scentCleanupCursor = 0;
        transientCleanupCursor = 0;
    }


    public List<ScentNode> getAllNodes() {
        List<ScentNode> result = new ArrayList<>();

        for (ConcurrentLinkedQueue<ScentNode> queue : new ArrayList<>(scentMap.values())) {
            result.addAll(queue);
        }
        return result;
    }

    public List<ScentNode> getNodesNearPlayers(List<ServerPlayer> players, double renderDistance) {
        if (players == null || players.isEmpty()) {
            return new ArrayList<>();
        }
        double safeDistance = Double.isFinite(renderDistance) ? Math.max(0.0, renderDistance) : 0.0;
        int chunkRadius = Math.max(0, (int) Math.ceil(safeDistance / 16.0));
        Set<ChunkPos> visitedChunks = new HashSet<>();
        List<ScentNode> result = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player == null || player.level() != level) {
                continue;
            }
            ChunkPos center = new ChunkPos(player.blockPosition());
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                    if (!visitedChunks.add(chunk)) {
                        continue;
                    }
                    ConcurrentLinkedQueue<ScentNode> nodes = scentMap.get(chunk);
                    if (nodes != null) {
                        result.addAll(nodes);
                    }
                    ConcurrentLinkedQueue<ScentNode> transientNodes = transientScentMap.get(chunk);
                    if (transientNodes != null) {
                        result.addAll(transientNodes);
                    }
                }
            }
        }
        return result;
    }


    public synchronized CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag chunksList = new ListTag();
        long currentTime = level.getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();

        for (Map.Entry<ChunkPos, ConcurrentLinkedQueue<ScentNode>> entry : scentMap.entrySet()) {
            ChunkPos pos = entry.getKey();
            ConcurrentLinkedQueue<ScentNode> queue = entry.getValue();

            if (queue.isEmpty()) continue;

            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putLong("ChunkKey", pos.toLong());

            ListTag nodesTag = new ListTag();
            for (ScentNode node : queue) {

                if ((currentTime - node.getTimestamp()) <= maxDuration) {
                    nodesTag.add(node.save());
                }
            }

            if (!nodesTag.isEmpty()) {
                chunkTag.put("Nodes", nodesTag);
                chunksList.add(chunkTag);
            }
        }
        tag.put("ScentChunks", chunksList);
        return tag;
    }

    public synchronized void deserializeNBT(CompoundTag tag) {
        scentMap.clear();
        transientScentMap.clear();
        windChunksByOwner.invalidateAll();
        if (tag == null) {
            return;
        }
        ListTag chunksList = tag.getList("ScentChunks", 10);

        int chunkLimit = Math.min(chunksList.size(), MAX_PERSISTED_CHUNKS);
        for (int i = 0; i < chunkLimit; i++) {
            CompoundTag chunkTag = chunksList.getCompound(i);
            long chunkKey = chunkTag.getLong("ChunkKey");
            ChunkPos pos = new ChunkPos(chunkKey);

            ListTag nodesTag = chunkTag.getList("Nodes", 10);
            ConcurrentLinkedQueue<ScentNode> queue = new ConcurrentLinkedQueue<>();

            int limit = Math.min(nodesTag.size(), MAX_NODES_PER_CHUNK);
            for (int j = 0; j < limit; j++) {
                ScentNode node = ScentNode.load(nodesTag.getCompound(j));
                if (isUsableNode(node)) {
                    queue.add(node);
                }
            }

            if (!queue.isEmpty()) {
                scentMap.put(pos, queue);
            }
        }
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ScentManager backend;
        private final LazyOptional<ScentManager> optional;

        public Provider(Level level) {
            this.backend = new ScentManager(level);
            this.optional = LazyOptional.of(() -> backend);
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
            return INSTANCE.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return backend.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            backend.deserializeNBT(nbt);
        }
    }
}
