package com.example.soundattract.ai;

import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class LivingBridgeRouteSearch {
    static final int DEFAULT_NODE_BUDGET = 2048;
    static final int MAX_NODE_BUDGET = 4096;
    private static final double MAX_BODY_DIMENSION = 16.0D;
    private static final int[][] NEIGHBOR_OFFSETS = {
            {1, 0, 0},
            {-1, 0, 0},
            {0, 0, 1},
            {0, 0, -1},
            {0, 1, 0},
            {0, -1, 0}
    };

    private LivingBridgeRouteSearch() {
    }

    static List<BlockPos> findRoute(ServerLevel level, BlockPos start, BlockPos end,
                                    double bodyWidth, double bodyHeight) {
        return findRoute(level, start, end, bodyWidth, bodyHeight, DEFAULT_NODE_BUDGET);
    }

    static List<BlockPos> findRoute(ServerLevel level, BlockPos start, BlockPos end,
                                    double bodyWidth, double bodyHeight, int nodeBudget) {
        if (level == null) return List.of();
        SnapshotWorldAccess snapshot = SnapshotWorldAccess.capture(level, start, end, bodyWidth, bodyHeight);
        if (snapshot != null) {
            return findRoute(snapshot, start, end, bodyWidth, bodyHeight, nodeBudget);
        }
        return findRoute(new ServerWorld(level), start, end, bodyWidth, bodyHeight, nodeBudget);
    }

    static CompletableFuture<List<BlockPos>> submitRouteSearch(ServerLevel level, BlockPos start, BlockPos end,
                                                               double bodyWidth, double bodyHeight, int nodeBudget) {
        if (level == null || start == null || end == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        SnapshotWorldAccess snapshot = SnapshotWorldAccess.capture(level, start, end, bodyWidth, bodyHeight);
        if (snapshot == null) {
            return CompletableFuture.completedFuture(findRoute(new ServerWorld(level), start, end, bodyWidth, bodyHeight, nodeBudget));
        }
        CompletableFuture<List<BlockPos>> future = QuantifiedOptionalBridge.trySubmitTask(
                "soundattract",
                "soundattract_living_bridge_route_search",
                () -> findRoute(snapshot, start, end, bodyWidth, bodyHeight, nodeBudget),
                true,
                true,
                Duration.ofMillis(150L),
                "living_bridge_route:" + start.asLong() + ":" + end.asLong());
        if (future != null) {
            return future;
        }
        return CompletableFuture.completedFuture(findRoute(snapshot, start, end, bodyWidth, bodyHeight, nodeBudget));
    }

    static List<BlockPos> findRoute(WorldAccess world, BlockPos start, BlockPos end,
                                    double bodyWidth, double bodyHeight, int nodeBudget) {
        if (world == null || start == null || end == null
                || !Double.isFinite(bodyWidth) || !Double.isFinite(bodyHeight)
                || bodyWidth <= 0.0D || bodyHeight <= 0.0D
                || bodyWidth > MAX_BODY_DIMENSION || bodyHeight > MAX_BODY_DIMENSION || nodeBudget <= 0) {
            return List.of();
        }
        int budget = Math.min(nodeBudget, MAX_NODE_BUDGET);
        BlockPos source = start.immutable();
        BlockPos target = end.immutable();
        if (!isClear(world, source, bodyWidth, bodyHeight)
                || !isClear(world, target, bodyWidth, bodyHeight)) {
            return List.of();
        }
        if (source.equals(target)) return List.of(source);

        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator
                .comparingLong(LivingBridgeRouteSearch::priority)
                .thenComparingInt(SearchNode::heuristic)
                .thenComparingInt(SearchNode::cost)
                .thenComparingLong(SearchNode::sequence));
        Map<BlockPos, Integer> bestCosts = new HashMap<>();
        Map<BlockPos, BlockPos> predecessors = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        long sequence = 0L;
        int sourceHeuristic = heuristic(source, target);
        open.add(new SearchNode(source, 0, sourceHeuristic, sequence++));
        bestCosts.put(source, 0);
        int expanded = 0;

        while (!open.isEmpty() && expanded < budget) {
            SearchNode current = open.poll();
            Integer knownCost = bestCosts.get(current.position());
            if (knownCost == null || knownCost != current.cost() || !closed.add(current.position())) {
                continue;
            }
            expanded++;
            if (current.position().equals(target)) {
                return reconstruct(predecessors, source, target);
            }
            for (int[] offset : NEIGHBOR_OFFSETS) {
                BlockPos neighbor = offset(current.position(), offset);
                if (neighbor == null || closed.contains(neighbor)
                        || !isClear(world, neighbor, bodyWidth, bodyHeight)
                        || !world.isTransitionClear(current.position(), neighbor, bodyWidth, bodyHeight)) {
                    continue;
                }
                int nextCost = current.cost() + 1;
                Integer priorCost = bestCosts.get(neighbor);
                if (priorCost != null && priorCost <= nextCost) continue;
                bestCosts.put(neighbor, nextCost);
                predecessors.put(neighbor, current.position());
                open.add(new SearchNode(neighbor, nextCost, heuristic(neighbor, target), sequence++));
            }
        }
        return List.of();
    }

    interface WorldAccess {
        boolean isLoaded(BlockPos position);

        boolean isLava(BlockPos position);

        boolean isBodyClear(BlockPos feet, double bodyWidth, double bodyHeight);

        boolean isTransitionClear(BlockPos from, BlockPos to, double bodyWidth, double bodyHeight);
    }

    private static boolean isClear(WorldAccess world, BlockPos position, double bodyWidth, double bodyHeight) {
        return world.isLoaded(position) && !world.isLava(position)
                && world.isBodyClear(position, bodyWidth, bodyHeight);
    }

    private static long priority(SearchNode node) {
        return (long) node.cost() + node.heuristic();
    }

    private static int heuristic(BlockPos from, BlockPos to) {
        long distance = Math.abs((long) from.getX() - to.getX())
                + Math.abs((long) from.getY() - to.getY())
                + Math.abs((long) from.getZ() - to.getZ());
        return distance >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) distance;
    }

    private static BlockPos offset(BlockPos position, int[] offset) {
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        if ((offset[0] > 0 && x == Integer.MAX_VALUE) || (offset[0] < 0 && x == Integer.MIN_VALUE)
                || (offset[1] > 0 && y == Integer.MAX_VALUE) || (offset[1] < 0 && y == Integer.MIN_VALUE)
                || (offset[2] > 0 && z == Integer.MAX_VALUE) || (offset[2] < 0 && z == Integer.MIN_VALUE)) {
            return null;
        }
        return new BlockPos(x + offset[0], y + offset[1], z + offset[2]);
    }

    private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> predecessors,
                                              BlockPos source, BlockPos target) {
        ArrayDeque<BlockPos> reversed = new ArrayDeque<>();
        BlockPos current = target;
        while (current != null) {
            reversed.addFirst(current.immutable());
            if (current.equals(source)) return List.copyOf(reversed);
            current = predecessors.get(current);
        }
        return List.of();
    }

    private static AABB bodyBox(BlockPos feet, double bodyWidth, double bodyHeight) {
        double halfWidth = bodyWidth * 0.5D;
        double centerX = feet.getX() + 0.5D;
        double centerZ = feet.getZ() + 0.5D;
        return new AABB(centerX - halfWidth, feet.getY(), centerZ - halfWidth,
                centerX + halfWidth, feet.getY() + bodyHeight, centerZ + halfWidth);
    }

    private static boolean clearVolume(ServerLevel level, AABB volume) {
        int minX = (int) Math.floor(volume.minX);
        int minY = (int) Math.floor(volume.minY);
        int minZ = (int) Math.floor(volume.minZ);
        int maxX = (int) Math.ceil(volume.maxX) - 1;
        int maxY = (int) Math.ceil(volume.maxY) - 1;
        int maxZ = (int) Math.ceil(volume.maxZ) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (!level.isLoaded(cell)) return false;
                    if (level.getFluidState(cell).is(FluidTags.LAVA)) return false;
                    VoxelShape shape = level.getBlockState(cell).getCollisionShape(level, cell, CollisionContext.empty());
                    for (AABB part : shape.toAabbs()) {
                        if (part.move(cell).intersects(volume)) return false;
                    }
                }
            }
        }
        return true;
    }

    private record SearchNode(BlockPos position, int cost, int heuristic, long sequence) {
    }

    private static final class ServerWorld implements WorldAccess {
        private final ServerLevel level;

        private ServerWorld(ServerLevel level) {
            this.level = level;
        }

        @Override
        public boolean isLoaded(BlockPos position) {
            return position.getY() >= level.getMinBuildHeight()
                    && position.getY() < level.getMaxBuildHeight()
                    && level.isLoaded(position);
        }

        @Override
        public boolean isLava(BlockPos position) {
            return isLoaded(position) && level.getFluidState(position).is(FluidTags.LAVA);
        }

        @Override
        public boolean isBodyClear(BlockPos feet, double bodyWidth, double bodyHeight) {
            return clearVolume(level, bodyBox(feet, bodyWidth, bodyHeight));
        }

        @Override
        public boolean isTransitionClear(BlockPos from, BlockPos to, double bodyWidth, double bodyHeight) {
            AABB start = bodyBox(from, bodyWidth, bodyHeight);
            return clearVolume(level, start.minmax(bodyBox(to, bodyWidth, bodyHeight)));
        }
    }

    static final class SnapshotWorldAccess implements WorldAccess {
        private static final byte CELL_EMPTY = 0;
        private static final byte CELL_SOLID = 1;
        private static final byte CELL_LAVA = 2;
        private static final byte CELL_UNLOADED = 3;
        private static final byte CELL_PARTIAL = 4;

        private final int minX, minY, minZ;
        private final int sizeX, sizeY, sizeZ;
        private final int sizeYZ;
        private final byte[] cells;
        private final Map<BlockPos, List<AABB>> partialShapes;

        SnapshotWorldAccess(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                            int sizeYZ, byte[] cells, Map<BlockPos, List<AABB>> partialShapes) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.sizeYZ = sizeYZ;
            this.cells = cells;
            this.partialShapes = partialShapes;
        }

        static SnapshotWorldAccess capture(ServerLevel level, BlockPos start, BlockPos end,
                                           double bodyWidth, double bodyHeight) {
            if (level == null || start == null || end == null) return null;
            int marginHoriz = (int) Math.ceil(bodyWidth) + 2;
            int marginVert = (int) Math.ceil(bodyHeight) + 2;
            int minX = Math.min(start.getX(), end.getX()) - marginHoriz;
            int maxX = Math.max(start.getX(), end.getX()) + marginHoriz;
            int minY = Math.max(level.getMinBuildHeight(), Math.min(start.getY(), end.getY()) - marginVert);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, Math.max(start.getY(), end.getY()) + marginVert);
            int minZ = Math.min(start.getZ(), end.getZ()) - marginHoriz;
            int maxZ = Math.max(start.getZ(), end.getZ()) + marginHoriz;

            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            int total = sizeX * sizeY * sizeZ;
            if (total <= 0 || total > 131072) {
                return null;
            }
            byte[] cells = new byte[total];
            Map<BlockPos, List<AABB>> partials = new HashMap<>();
            int sizeYZ = sizeY * sizeZ;

            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        mpos.set(x, y, z);
                        int idx = (x - minX) * sizeYZ + (y - minY) * sizeZ + (z - minZ);
                        if (!level.isLoaded(mpos)) {
                            cells[idx] = CELL_UNLOADED;
                            continue;
                        }
                        if (level.getFluidState(mpos).is(FluidTags.LAVA)) {
                            cells[idx] = CELL_LAVA;
                            continue;
                        }
                        BlockState state = level.getBlockState(mpos);
                        if (state.isAir()) {
                            cells[idx] = CELL_EMPTY;
                            continue;
                        }
                        VoxelShape shape = state.getCollisionShape(level, mpos, CollisionContext.empty());
                        if (shape.isEmpty()) {
                            cells[idx] = CELL_EMPTY;
                        } else {
                            List<AABB> aabbs = shape.toAabbs();
                            if (aabbs.size() == 1) {
                                AABB box = aabbs.get(0);
                                if (box.minX <= 0.0D && box.minY <= 0.0D && box.minZ <= 0.0D
                                        && box.maxX >= 1.0D && box.maxY >= 1.0D && box.maxZ >= 1.0D) {
                                    cells[idx] = CELL_SOLID;
                                    continue;
                                }
                            }
                            cells[idx] = CELL_PARTIAL;
                            partials.put(mpos.immutable(), aabbs);
                        }
                    }
                }
            }
            return new SnapshotWorldAccess(minX, minY, minZ, sizeX, sizeY, sizeZ, sizeYZ, cells, partials);
        }

        private boolean contains(int x, int y, int z) {
            return x >= minX && x < minX + sizeX && y >= minY && y < minY + sizeY && z >= minZ && z < minZ + sizeZ;
        }

        private byte getCell(int x, int y, int z) {
            if (!contains(x, y, z)) return CELL_UNLOADED;
            return cells[(x - minX) * sizeYZ + (y - minY) * sizeZ + (z - minZ)];
        }

        @Override
        public boolean isLoaded(BlockPos position) {
            return getCell(position.getX(), position.getY(), position.getZ()) != CELL_UNLOADED;
        }

        @Override
        public boolean isLava(BlockPos position) {
            return getCell(position.getX(), position.getY(), position.getZ()) == CELL_LAVA;
        }

        @Override
        public boolean isBodyClear(BlockPos feet, double bodyWidth, double bodyHeight) {
            return clearVolume(bodyBox(feet, bodyWidth, bodyHeight));
        }

        @Override
        public boolean isTransitionClear(BlockPos from, BlockPos to, double bodyWidth, double bodyHeight) {
            AABB start = bodyBox(from, bodyWidth, bodyHeight);
            return clearVolume(start.minmax(bodyBox(to, bodyWidth, bodyHeight)));
        }

        private boolean clearVolume(AABB volume) {
            int vMinX = (int) Math.floor(volume.minX);
            int vMinY = (int) Math.floor(volume.minY);
            int vMinZ = (int) Math.floor(volume.minZ);
            int vMaxX = (int) Math.ceil(volume.maxX) - 1;
            int vMaxY = (int) Math.ceil(volume.maxY) - 1;
            int vMaxZ = (int) Math.ceil(volume.maxZ) - 1;
            for (int x = vMinX; x <= vMaxX; x++) {
                for (int y = vMinY; y <= vMaxY; y++) {
                    for (int z = vMinZ; z <= vMaxZ; z++) {
                        byte cell = getCell(x, y, z);
                        if (cell == CELL_UNLOADED || cell == CELL_LAVA) {
                            return false;
                        }
                        if (cell == CELL_SOLID) {
                            if (x < volume.maxX && x + 1 > volume.minX
                                    && y < volume.maxY && y + 1 > volume.minY
                                    && z < volume.maxZ && z + 1 > volume.minZ) {
                                return false;
                            }
                        } else if (cell == CELL_PARTIAL) {
                            List<AABB> parts = partialShapes.get(new BlockPos(x, y, z));
                            if (parts != null) {
                                for (AABB part : parts) {
                                    if (part.move(x, y, z).intersects(volume)) {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }
    }
}