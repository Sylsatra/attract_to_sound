package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BridgeWorkTimeoutTest {
    @Test
    void progressWindowUsesInclusiveBoundsAndWavePenalty() {
        assertEquals(80L, LivingWallCoordinator.bridgeProgressWindowTicks(0.0D, 1));
        assertEquals(80L, LivingWallCoordinator.bridgeProgressWindowTicks(0.0D, 4));
        assertEquals(600L, LivingWallCoordinator.bridgeProgressWindowTicks(1000.0D, 1));
        assertTrue(LivingWallCoordinator.bridgeProgressWindowTicks(20.0D, 3)
                > LivingWallCoordinator.bridgeProgressWindowTicks(20.0D, 1));
    }

    @Test
    void startupFailureBeginsOnlyAfterThreeTicksWithoutControllerStart() {
        assertTrue(LivingWallCoordinator.bridgeControllerMissedStartup(103L, 100L, Long.MIN_VALUE));
        assertTrue(!LivingWallCoordinator.bridgeControllerMissedStartup(102L, 100L, Long.MIN_VALUE));
        assertTrue(!LivingWallCoordinator.bridgeControllerMissedStartup(103L, 100L, 101L));
    }

    @Test
    void oneRejectedSubmissionDoesNotReleaseUntilHardFailuresAndNoProgressAgree() {
        assertTrue(!LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(1, true));
        assertTrue(!LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(3, false));
        assertTrue(LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(3, true));
    }
}
