package com.example.soundattract.scents;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.soundattract.config.SoundAttractConfig;

public class ScentManager {
    private static final Map<Level, ScentManager> INSTANCES = Collections.synchronizedMap(new WeakHashMap<>());

    public static Optional<ScentManager> get(Level level) {
        if (level == null) {
            return Optional.empty();
        }
        return Optional.of(INSTANCES.computeIfAbsent(level, ScentManager::new));
    }

    private final Level level;

    private final Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> scentMap = new ConcurrentHashMap<>();
    private final Map<UUID, List<ScentNode>> windNodes = new ConcurrentHashMap<>();

    public void replaceWindNodes(UUID owner, List<ScentNode> replacement) {
        clearWindNodes(owner);
        if (owner == null || replacement == null || replacement.isEmpty()) return;
        List<ScentNode> copy = List.copyOf(replacement);
        windNodes.put(owner, copy);
        copy.forEach(this::addScentNode);
    }

    public void clearWindNodes(UUID owner) {
        List<ScentNode> previous = windNodes.remove(owner);
        if (previous == null) return;
        for (ScentNode node : previous) {
            ChunkPos chunk = new ChunkPos(node.getBlockPos());
            ConcurrentLinkedQueue<ScentNode> nodes = scentMap.get(chunk);
            if (nodes != null) {
                nodes.remove(node);
                if (nodes.isEmpty()) scentMap.remove(chunk, nodes);
            }
        }
    }

    public ScentManager(Level level) {
        this.level = level;
    }

    public void addScentNode(ScentNode node) {
        ChunkPos chunkPos = new ChunkPos(node.getBlockPos());
        ConcurrentLinkedQueue<ScentNode> queue = scentMap.computeIfAbsent(chunkPos, k -> new ConcurrentLinkedQueue<>());
        queue.add(node);
    }

    /**
     * Gets all scent nodes in the specified chunk and its 8 neighbors (3x3 area).
     * Returns a flat list of nodes.
     */
    public List<ScentNode> getNodesInArea(ChunkPos centerChunk) {
        List<ScentNode> result = new ArrayList<>();
        
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                ChunkPos pos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);

                if (level.isLoaded(pos.getWorldPosition())) {
                    ConcurrentLinkedQueue<ScentNode> nodes = scentMap.get(pos);
                    if (nodes != null) {
                        result.addAll(nodes);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Cleans up expired nodes in the specified chunk.
     * Can be called periodically or lazily when accessing a chunk.
     */
    public void cleanUpChunk(ChunkPos chunkPos, long currentTime, long maxDuration) {
        ConcurrentLinkedQueue<ScentNode> nodes = scentMap.get(chunkPos);
        if (nodes == null) return;

        nodes.removeIf(node -> (currentTime - node.getTimestamp()) > maxDuration);
        
        if (nodes.isEmpty()) {
            scentMap.remove(chunkPos);
        }
    }

    public void clear() {
        scentMap.clear();
    }

    /**
     * Gets all scent nodes across all loaded chunks.
     * Used by the persistent scent particle system.
     */
    public List<ScentNode> getAllNodes() {
        List<ScentNode> result = new ArrayList<>();


        for (ConcurrentLinkedQueue<ScentNode> queue : new ArrayList<>(scentMap.values())) {
            result.addAll(queue);
        }
        return result;
    }
    






    public CompoundTag serializeNBT() {
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

    public void deserializeNBT(CompoundTag tag) {
        scentMap.clear();
        ListTag chunksList = tag.getListOrEmpty("ScentChunks");
        
        for (int i = 0; i < chunksList.size(); i++) {
            CompoundTag chunkTag = chunksList .getCompoundOrEmpty(i);
            long chunkKey = chunkTag.getLongOr("ChunkKey", 0L);
            ChunkPos pos = new ChunkPos(chunkKey);
            
            ListTag nodesTag = chunkTag.getListOrEmpty("Nodes");
            ConcurrentLinkedQueue<ScentNode> queue = new ConcurrentLinkedQueue<>();
            
            for (int j = 0; j < nodesTag.size(); j++) {
                queue.add(ScentNode.load(nodesTag .getCompoundOrEmpty(j)));
            }
            
            if (!queue.isEmpty()) {
                scentMap.put(pos, queue);
            }
        }
    }

}
