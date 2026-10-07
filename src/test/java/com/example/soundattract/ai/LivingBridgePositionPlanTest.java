package com.example.soundattract.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LivingBridgePositionPlanTest {
    private static final UUID WORKER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private LivingBridgePositionPlan plan() {
        return new LivingBridgePositionPlan("minecraft:overworld", 9L, 100L, 140L,
                new BlockPos(0, 64, 0), new BlockPos(16, 62, 8), new Vec3(1, 0, 0),
                "JUMP_BRIDGE", 3, 8, 4L, 7L, List.of(WORKER), 2L);
    }

    @Test
    void windowExpiryIsExclusiveAndGenerationIsIndependentOfTimeBucket() {
        LivingBridgePositionPlan plan = plan();
        assertTrue(plan.isLive(100L));
        assertTrue(plan.isLive(139L));
        assertFalse(plan.isLive(140L));
        assertEquals(9L, plan.windowGeneration());
    }

    @Test
    void targetDriftWithinBucketDoesNotReplaceLockedDestination() {
        LivingBridgePositionPlan plan = plan();
        assertTrue(plan.acceptsDestination(new BlockPos(19, 63, 11), true));
        assertEquals(new BlockPos(16, 62, 8), plan.lockedDestination());
        assertFalse(plan.acceptsDestination(new BlockPos(20, 62, 8), true));
        assertFalse(plan.acceptsDestination(new BlockPos(19, 63, 11), false));
    }

    @Test
    void workerTokenCapturesCurrentPositionWithoutChangingWindowDestination() {
        var token = plan().workerToken(WORKER, new BlockPos(3, 64, 2), new BlockPos(3, 63, 2), true, 55L);
        assertEquals(new BlockPos(3, 64, 2), token.position());
        assertEquals(new BlockPos(16, 62, 8), token.lockedDestination());
        assertEquals(9L, token.windowGeneration());
    }

    @Test
    void terrainEpochRejectsStaleWindow() {
        LivingBridgePositionPlan plan = plan();
        assertTrue(plan.hasCurrentTerrain(4L));
        assertFalse(plan.hasCurrentTerrain(5L));
    }

    @Test
    void destinationBucketsUseFloorDivisionAcrossNegativeCoordinates() {
        LivingBridgePositionPlan plan = new LivingBridgePositionPlan("minecraft:overworld", 10L,
                200L, 240L, new BlockPos(0, 64, 0), new BlockPos(-1, 62, -5),
                new Vec3(1, 0, 0), "JUMP_BRIDGE", 3, 8, 4L, 7L,
                List.of(WORKER), 3L);

        assertTrue(plan.destinationBucketContains(new BlockPos(-4, 60, -8)));
        assertFalse(plan.destinationBucketContains(new BlockPos(0, 62, -5)));
        assertFalse(plan.acceptsDestination(new BlockPos(-4, 60, -8), false));
    }

    @Test
    void rosterIdentityIsCanonicalAndWorkerTokenIsAValueSnapshot() {
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LivingBridgePositionPlan plan = new LivingBridgePositionPlan("minecraft:overworld", 11L,
                300L, 340L, new BlockPos(0, 64, 0), new BlockPos(16, 62, 8),
                new Vec3(1, 0, 0), "JUMP_BRIDGE", 3, 8, 4L, 7L,
                List.of(second, WORKER, WORKER), 4L);

        assertEquals(List.of(WORKER, WORKER, second), plan.rosterIdentity());
        assertTrue(plan.acceptsRoster(List.of(second, WORKER, WORKER)));
        assertFalse(plan.acceptsRoster(List.of(WORKER, second)));

        var token = plan.workerToken(WORKER, new BlockPos(3, 64, 2),
                new BlockPos(3, 63, 2), true, 55L);
        assertEquals(plan.rosterIdentity(), token.rosterIdentity());
        assertEquals(4L, token.requestRevision());
    }
}
