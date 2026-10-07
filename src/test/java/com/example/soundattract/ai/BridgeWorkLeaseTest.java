package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BridgeWorkLeaseTest {
    @Test
    void leaseStateContractAllowsOnlyFrozenTransitions() {
        assertTrue(LivingWallCoordinator.canTransitionWorkerLeaseState(
                LivingWallCoordinator.WorkerLeaseState.IDLE,
                LivingWallCoordinator.WorkerLeaseState.HOLDING));
        assertTrue(LivingWallCoordinator.canTransitionWorkerLeaseState(
                LivingWallCoordinator.WorkerLeaseState.HOLDING,
                LivingWallCoordinator.WorkerLeaseState.MOVING_TO_ASSEMBLY));
        assertTrue(LivingWallCoordinator.canTransitionWorkerLeaseState(
                LivingWallCoordinator.WorkerLeaseState.SUCTIONING,
                LivingWallCoordinator.WorkerLeaseState.BUILDING));
        assertTrue(LivingWallCoordinator.canTransitionWorkerLeaseState(
                LivingWallCoordinator.WorkerLeaseState.REASSIGNING,
                LivingWallCoordinator.WorkerLeaseState.RELEASED));
        assertFalse(LivingWallCoordinator.canTransitionWorkerLeaseState(
                LivingWallCoordinator.WorkerLeaseState.RELEASED,
                LivingWallCoordinator.WorkerLeaseState.HOLDING));
        assertFalse(LivingWallCoordinator.canTransitionWorkerLeaseState(
                LivingWallCoordinator.WorkerLeaseState.ASSEMBLED,
                LivingWallCoordinator.WorkerLeaseState.BUILDING));
    }
}
