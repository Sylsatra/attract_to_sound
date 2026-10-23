package com.example.soundattract.scents;

import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.soundattract.config.SoundAttractConfig;

public class ScentManager implements Component {
    private static final int MAX_NODES_PER_CHUNK = 512;
    private static final int CLEANUP_CHUNKS_PER_PASS = 64;

    public static ScentManager getForLevel(Level level) {
        if (level instanceof ServerLevel sl) {
            return com.example.soundattract.component.ModComponents.SCENT.get(sl);
        }
        return null;
    }

    public static void removeForLevel(Level level) {
    }

    private final Level level;

    private final Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> scentMap = new ConcurrentHashMap<>();
    private final Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> transientScentMap = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ChunkPos>> windChunksByOwner = new ConcurrentHashMap<>();
    private int scentCleanupCursor;
    private int transientCleanupCursor;

    public ScentManager(Level level) {
        this.level = level;
    }

    public void addScentNode(ScentNode node) {
        if (!isUsableNode(node)) return;
        ChunkPos chunkPos = new ChunkPos(node.getBlockPos());
        ConcurrentLinkedQueue<ScentNode> queue = scentMap.computeIfAbsent(chunkPos, k -> new ConcurrentLinkedQueue<>());
        queue.add(node);
        while (queue.size() > MAX_NODES_PER_CHUNK) queue.poll();
    }

    public synchronized void replaceWindNodes(UUID owner, List<ScentNode> nodes) {
        if (owner == null) return;
        Set<ChunkPos> previousChunks = windChunksByOwner.remove(owner);
        if (previousChunks != null) {
            for (ChunkPos chunkPos : previousChunks) {
                ConcurrentLinkedQueue<ScentNode> queue = transientScentMap.get(chunkPos);
                if (queue == null) continue;
                queue.removeIf(node -> owner.equals(node.getOwnerUUID()) && node.getSourceType() == ScentSourceType.PLAYER_WIND);
                if (queue.isEmpty()) transientScentMap.remove(chunkPos, queue);
            }
        }
        if (nodes == null) return;
        for (ScentNode node : nodes) {
            if (!isUsableNode(node) || !owner.equals(node.getOwnerUUID()) || node.getSourceType() != ScentSourceType.PLAYER_WIND) continue;
            ChunkPos chunkPos = new ChunkPos(node.getBlockPos());
            ConcurrentLinkedQueue<ScentNode> queue = transientScentMap.computeIfAbsent(chunkPos, ignored -> new ConcurrentLinkedQueue<>());
            queue.add(node);
            windChunksByOwner.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(chunkPos);
            while (queue.size() > MAX_NODES_PER_CHUNK) queue.poll();
        }
    }

    public void clearWindNodes(UUID owner) {
        replaceWindNodes(owner, null);
    }

    /** Removes persistent and transient parcels owned by one source. */
    public void removeOwnerNodes(UUID owner) {
        if (owner == null) return;
        removeOwnerNodesFromMap(scentMap, owner);
        removeOwnerNodesFromMap(transientScentMap, owner);
        windChunksByOwner.remove(owner);
    }

    private static void removeOwnerNodesFromMap(
            Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> map,
            UUID owner) {
        for (Map.Entry<ChunkPos, ConcurrentLinkedQueue<ScentNode>> entry : map.entrySet()) {
            ConcurrentLinkedQueue<ScentNode> queue = entry.getValue();
            queue.removeIf(node -> node != null && owner.equals(node.getOwnerUUID()));
            if (queue.isEmpty()) map.remove(entry.getKey(), queue);
        }
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
                    ConcurrentLinkedQueue<ScentNode> transientNodes = transientScentMap.get(pos);
                    if (transientNodes != null) result.addAll(transientNodes);
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
        if (chunkPos == null) return;
        cleanQueue(scentMap, chunkPos, currentTime, maxDuration);
        cleanQueue(transientScentMap, chunkPos, currentTime, maxDuration);
    }

    public void cleanupExpired(long currentTime, long maxDuration) {
        scentCleanupCursor = cleanupMap(scentMap, scentCleanupCursor, currentTime, maxDuration);
        transientCleanupCursor = cleanupMap(transientScentMap, transientCleanupCursor, currentTime, maxDuration);
    }

    private static int cleanupMap(Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> map, int cursor, long currentTime, long maxDuration) {
        List<ChunkPos> keys = new ArrayList<>(map.keySet());
        if (keys.isEmpty()) return 0;
        int start = Math.floorMod(cursor, keys.size());
        int count = Math.min(CLEANUP_CHUNKS_PER_PASS, keys.size());
        for (int i = 0; i < count; i++) cleanQueue(map, keys.get((start + i) % keys.size()), currentTime, maxDuration);
        return (start + count) % Math.max(1, keys.size());
    }

    private static void cleanQueue(Map<ChunkPos, ConcurrentLinkedQueue<ScentNode>> map, ChunkPos chunkPos, long currentTime, long maxDuration) {
        ConcurrentLinkedQueue<ScentNode> nodes = map.get(chunkPos);
        if (nodes == null) return;
        nodes.removeIf(node -> node == null || currentTime - node.getTimestamp() > maxDuration);
        if (nodes.isEmpty()) map.remove(chunkPos, nodes);
    }

    private static boolean isUsableNode(ScentNode node) {
        if (node == null || node.getPosition() == null || !Float.isFinite(node.getStrength())) return false;
        Vec3 position = node.getPosition();
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }

    public void clear() {
        scentMap.clear();
        transientScentMap.clear();
        windChunksByOwner.clear();
        scentCleanupCursor = 0;
        transientCleanupCursor = 0;
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

    public List<ScentNode> getNodesNearPlayers(List<net.minecraft.server.level.ServerPlayer> players, double renderDistance) {
        if (players == null || players.isEmpty()) return new ArrayList<>();
        double safeDistance = Double.isFinite(renderDistance) ? Math.max(0.0, renderDistance) : 0.0;
        int chunkRadius = Math.max(0, (int) Math.ceil(safeDistance / 16.0));
        Set<ChunkPos> visitedChunks = new HashSet<>();
        List<ScentNode> result = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : players) {
            if (player == null || player.level() != level) continue;
            ChunkPos center = new ChunkPos(player.blockPosition());
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                    if (!visitedChunks.add(chunk)) continue;
                    ConcurrentLinkedQueue<ScentNode> nodes = scentMap.get(chunk);
                    if (nodes != null) result.addAll(nodes);
                    ConcurrentLinkedQueue<ScentNode> transientNodes = transientScentMap.get(chunk);
                    if (transientNodes != null) result.addAll(transientNodes);
                }
            }
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
        transientScentMap.clear();
        windChunksByOwner.clear();
        ListTag chunksList = tag.getListOrEmpty("ScentChunks");
        
        for (int i = 0; i < chunksList.size(); i++) {
            CompoundTag chunkTag = chunksList.getCompoundOrEmpty(i);
            long chunkKey = chunkTag.getLongOr("ChunkKey", 0L);
            ChunkPos pos = new ChunkPos(chunkKey);
            
            ListTag nodesTag = chunkTag.getListOrEmpty("Nodes");
            ConcurrentLinkedQueue<ScentNode> queue = new ConcurrentLinkedQueue<>();
            
            for (int j = 0; j < nodesTag.size(); j++) {
                queue.add(ScentNode.load(nodesTag.getCompoundOrEmpty(j)));
            }
            
            if (!queue.isEmpty()) {
                scentMap.put(pos, queue);
            }
        }
    }

    @Override
    public void readData(ValueInput input) {
        CompoundTag tag = new CompoundTag();
        ListTag rows = new ListTag();
        input.read("ScentChunks", CompoundTag.CODEC.listOf()).orElseGet(java.util.List::of).forEach(rows::add);
        tag.put("ScentChunks", rows);
        deserializeNBT(tag);
    }

    @Override
    public void writeData(ValueOutput output) {
        output.store("ScentChunks", CompoundTag.CODEC.listOf(), serializeNBT().getListOrEmpty("ScentChunks").compoundStream().toList());
    }

}
