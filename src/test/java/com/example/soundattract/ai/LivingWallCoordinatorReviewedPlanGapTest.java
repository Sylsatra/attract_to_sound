package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LivingWallCoordinatorReviewedPlanGapTest {
    @Test
    void dropResolvesDownwardWithinConfiguredLimit() {
        assertTrue(LivingWallCoordinator.supportedDropForTest(new BlockPos(0, 20, 0), new BlockPos(3, 12, 0), 9.0D));
    }

    @Test
    void dropRejectsUnsupportedDirectionAndOutOfLimit() {
        assertFalse(LivingWallCoordinator.supportedDropForTest(new BlockPos(0, 20, 0), new BlockPos(3, 21, 0), 9.0D));
        assertFalse(LivingWallCoordinator.supportedDropForTest(new BlockPos(0, 20, 0), new BlockPos(3, 10, 0), 9.0D));
    }

    @Test
    void jumpAcceptsClearUnsupportedEndpoint() {
        assertTrue(LivingWallCoordinator.clearJumpEndpointForTest(true, true, true, true, true));
        assertFalse(LivingWallCoordinator.clearJumpEndpointForTest(true, true, true, false, true));
    }

    @Test
    void workerRouteSuccessDoesNotImplyStructuralExecutability() {
        LivingWallCoordinator.RouteEvidence route = new LivingWallCoordinator.RouteEvidence(
                UUID.randomUUID(), new LivingWallCoordinator.IntakeWindow(new BlockPos(1, 64, 0)),
                new BlockPos(8, 64, 0), List.of(new BlockPos(1, 64, 0)), true, 1L, "route");
        assertFalse(LivingWallCoordinator.canonicalAdmissionAccessSatisfiedForTest(route, null, new BlockPos(8, 64, 0)));
    }
}
