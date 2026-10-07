package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LivingBridgeRouteSearchTest {
    @Test
    void findsDeterministicLateralDetourWithExactEndpoints() {
        BlockPos start = new BlockPos(0, 0, 0);
        BlockPos end = new BlockPos(2, 0, 0);
        TestWorld world = new TestWorld(0, 2, 0, 1, 0, 1);
        world.blocked.add(new BlockPos(1, 0, 0));

        List<BlockPos> first = LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 64);
        List<BlockPos> second = LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 64);

        assertEquals(first, second);
        assertEquals(start, first.get(0));
        assertEquals(end, first.get(first.size() - 1));
        assertTrue(first.contains(new BlockPos(1, 0, 1)));
        assertTrue(isConnected(first));
        assertEquals(0.6D, world.lastBodyWidth);
        assertEquals(1.8D, world.lastBodyHeight);
    }

    @Test
    void supportsVerticalDetour() {
        BlockPos start = new BlockPos(0, 0, 0);
        BlockPos end = new BlockPos(2, 0, 0);
        TestWorld world = new TestWorld(0, 2, 0, 1, 0, 0);
        world.blocked.add(new BlockPos(1, 0, 0));

        List<BlockPos> route = LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 64);

        assertEquals(start, route.get(0));
        assertEquals(end, route.get(route.size() - 1));
        assertTrue(route.contains(new BlockPos(1, 1, 0)));
        assertTrue(isConnected(route));
    }

    @Test
    void rejectsLavaUnloadedEndpointsAndInvalidBudgets() {
        BlockPos start = new BlockPos(0, 0, 0);
        BlockPos end = new BlockPos(2, 0, 0);
        TestWorld world = new TestWorld(0, 2, 0, 0, 0, 0);

        world.lava.add(end);
        assertTrue(LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 64).isEmpty());

        world.lava.clear();
        world.unloaded.add(end);
        assertTrue(LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 64).isEmpty());

        world.unloaded.clear();
        assertTrue(LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 0).isEmpty());
        assertTrue(LivingBridgeRouteSearch.findRoute(world, start, end, 16.1D, 1.8D, 64).isEmpty());
        assertTrue(LivingBridgeRouteSearch.findRoute(world, start, end, Double.NaN, 1.8D, 64).isEmpty());
    }

    @Test
    void respectsNodeBudgetAndReturnsImmutablePath() {
        BlockPos start = new BlockPos(0, 0, 0);
        BlockPos end = new BlockPos(2, 0, 0);
        TestWorld world = new TestWorld(0, 2, 0, 0, 0, 0);
        world.blocked.add(new BlockPos(1, 0, 0));

        assertTrue(LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 2).isEmpty());

        world.blocked.clear();
        List<BlockPos> route = LivingBridgeRouteSearch.findRoute(world, start, end, 0.6D, 1.8D, 64);
        assertFalse(route.isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> route.add(new BlockPos(3, 0, 0)));
    }

    private static boolean isConnected(List<BlockPos> route) {
        for (int index = 1; index < route.size(); index++) {
            BlockPos previous = route.get(index - 1);
            BlockPos current = route.get(index);
            int distance = Math.abs(previous.getX() - current.getX())
                    + Math.abs(previous.getY() - current.getY())
                    + Math.abs(previous.getZ() - current.getZ());
            if (distance != 1) return false;
        }
        return !route.isEmpty();
    }

    private static final class TestWorld implements LivingBridgeRouteSearch.WorldAccess {
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final Set<BlockPos> blocked = new HashSet<>();
        private final Set<BlockPos> lava = new HashSet<>();
        private final Set<BlockPos> unloaded = new HashSet<>();
        private double lastBodyWidth;
        private double lastBodyHeight;

        private TestWorld(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        @Override
        public boolean isLoaded(BlockPos position) {
            return position.getX() >= minX && position.getX() <= maxX
                    && position.getY() >= minY && position.getY() <= maxY
                    && position.getZ() >= minZ && position.getZ() <= maxZ
                    && !unloaded.contains(position);
        }

        @Override
        public boolean isLava(BlockPos position) {
            return lava.contains(position);
        }

        @Override
        public boolean isBodyClear(BlockPos feet, double bodyWidth, double bodyHeight) {
            lastBodyWidth = bodyWidth;
            lastBodyHeight = bodyHeight;
            return !blocked.contains(feet) && !lava.contains(feet);
        }

        @Override
        public boolean isTransitionClear(BlockPos from, BlockPos to, double bodyWidth, double bodyHeight) {
            return !blocked.contains(from) && !blocked.contains(to)
                    && !lava.contains(from) && !lava.contains(to);
        }
    }
}
