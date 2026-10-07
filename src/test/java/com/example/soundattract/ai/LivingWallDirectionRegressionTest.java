package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LivingWallDirectionRegressionTest {
    @Test
    void diagonalConstructionUsesResolvedEgressRatherThanWallContact() {
        for (int mirror : new int[] {-1, 1}) {
            BlockPos pivot = new BlockPos(8 * mirror, -47, -16);
            BlockPos wall = new BlockPos(-2 * mirror, -47, -12);
            BlockPos endpoint = new BlockPos(-mirror, -47, -11);
            BlockPos player = new BlockPos(9 * mirror, -59, -13);
            CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                    new ResourceLocation("minecraft", "overworld"), "WALL_LEDGE", pivot, pivot,
                    endpoint, endpoint, endpoint, wall, List.of(), List.of(), List.of(1L));
            Vec3 expected = new Vec3(-9 * mirror, 0, 5).normalize();
            assertEquals(expected.x, corridor.traversalForward().x, 1.0E-9D);
            assertEquals(expected.z, corridor.traversalForward().z, 1.0E-9D);
            assertTrue(LivingWallCoordinator.hasWallProgress(pivot, endpoint, wall));
            assertTrue(horizontalDistanceSquared(endpoint, player) > horizontalDistanceSquared(pivot, player));
            assertFalse(LivingWallCoordinator.hasWallProgress(pivot, pivot.offset(mirror, 0, -1), wall));
        }
    }

    @Test
    void mirroredFractionalRaysStepSymmetrically() {
        for (double component : new double[] {0.1D, 0.3713906764D, 0.9284766909D}) {
            assertEquals(1, LivingWallCoordinator.rayComponentSign(component));
            assertEquals(-1, LivingWallCoordinator.rayComponentSign(-component));
        }
        assertEquals(0, LivingWallCoordinator.rayComponentSign(0.0D));
        assertEquals(0, LivingWallCoordinator.rayComponentSign(1.0E-8D));
    }

    @Test
    void sidewaysAndZeroLengthPlansDoNotMakeWallProgress() {
        BlockPos pivot = new BlockPos(0, 64, 0);
        assertFalse(LivingWallCoordinator.hasWallProgress(pivot, new BlockPos(0, 64, 5), new BlockPos(8, 64, 0)));
        assertFalse(LivingWallCoordinator.hasWallProgress(pivot, pivot, new BlockPos(8, 64, 0)));
        assertFalse(LivingWallCoordinator.hasWallProgress(pivot, new BlockPos(8, 64, 0), pivot));
    }

    private double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        return Math.pow(first.getX() - second.getX(), 2) + Math.pow(first.getZ() - second.getZ(), 2);
    }
}
