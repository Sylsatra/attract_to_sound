package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.soundattract.pathfinding.HazardClassifier;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class HazardPerformanceAndSafeGroundTest {

    @Test
    void snapshotWorldAccessSolvesRouteThroughEmptyCorridor() {
        int minX = 0, minY = 0, minZ = 0;
        int sizeX = 5, sizeY = 5, sizeZ = 5;
        int sizeYZ = sizeY * sizeZ;
        byte[] cells = new byte[sizeX * sizeY * sizeZ];

        BlockPos blocked = new BlockPos(2, 2, 2);
        cells[(blocked.getX() - minX) * sizeYZ + (blocked.getY() - minY) * sizeZ + (blocked.getZ() - minZ)] = 1;

        LivingBridgeRouteSearch.SnapshotWorldAccess world = new LivingBridgeRouteSearch.SnapshotWorldAccess(
                minX, minY, minZ, sizeX, sizeY, sizeZ, sizeYZ, cells, new HashMap<>());

        BlockPos start = new BlockPos(1, 2, 2);
        BlockPos end = new BlockPos(3, 2, 2);

        List<BlockPos> route = LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 128);
        assertFalse(route.isEmpty());
        assertEquals(start, route.get(0));
        assertEquals(end, route.get(route.size() - 1));
        assertFalse(route.contains(blocked));
    }

    @Test
    void snapshotWorldAccessRejectsLava() {
        int minX = 0, minY = 0, minZ = 0;
        int sizeX = 5, sizeY = 5, sizeZ = 5;
        int sizeYZ = sizeY * sizeZ;
        byte[] cells = new byte[sizeX * sizeY * sizeZ];

        BlockPos lavaPos = new BlockPos(2, 2, 2);
        cells[(lavaPos.getX() - minX) * sizeYZ + (lavaPos.getY() - minY) * sizeZ + (lavaPos.getZ() - minZ)] = 2;

        LivingBridgeRouteSearch.SnapshotWorldAccess world = new LivingBridgeRouteSearch.SnapshotWorldAccess(
                minX, minY, minZ, sizeX, sizeY, sizeZ, sizeYZ, cells, new HashMap<>());

        assertTrue(world.isLava(lavaPos));
        assertFalse(world.isLava(new BlockPos(1, 1, 1)));
    }

    @Test
    void submitRouteSearchNullLevelReturnsCleanEmptyFuture() {
        var future = LivingBridgeRouteSearch.submitRouteSearch(null, new BlockPos(0, 0, 0), new BlockPos(1, 1, 1), 0.6D, 1.8D, 64);
        assertTrue(future.isDone());
        assertTrue(future.join().isEmpty());
    }

    @Test
    void hazardClassifierClearResetsCacheWithoutError() {
        HazardClassifier.clear();
        assertFalse(HazardClassifier.isSafeGround(null, new BlockPos(0, 0, 0)));
        assertFalse(HazardClassifier.isDangerous(null, null, (BlockPos) null));
    }
}
