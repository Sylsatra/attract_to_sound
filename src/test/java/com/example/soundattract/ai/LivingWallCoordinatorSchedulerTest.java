package com.example.soundattract.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class LivingWallCoordinatorSchedulerTest {
    @Test
    void pressureBandsRemainBoundedAndNeverDisableAdmission() {
        assertEquals(0, LivingWallCoordinator.pressureTier(34.99D));
        assertEquals(1, LivingWallCoordinator.pressureTier(35.0D));
        assertEquals(2, LivingWallCoordinator.pressureTier(45.01D));
        assertEquals(3, LivingWallCoordinator.pressureTier(60.01D));
        assertEquals(20, LivingWallCoordinator.admissionScanInterval(0));
        assertEquals(40, LivingWallCoordinator.admissionScanInterval(1));
        assertEquals(80, LivingWallCoordinator.admissionScanInterval(2));
        assertEquals(120, LivingWallCoordinator.admissionScanInterval(3));
    }

    @Test
    void purePlannerIsDeterministicAndBounded() {
        ArrayList<LivingBridgeQuantifiedPlanner.MobToken> tokens = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            tokens.add(new LivingBridgeQuantifiedPlanner.MobToken(new UUID(0L, i + 1L), i % 20, 64, i / 20));
        }
        var result = LivingBridgeQuantifiedPlanner.cluster(tokens, 2.0D, 2.0D);
        assertTrue(result.size() <= LivingBridgeQuantifiedPlanner.MAX_CLUSTERS);
        assertTrue(result.stream().allMatch(cluster -> cluster.size() <= LivingBridgeQuantifiedPlanner.MAX_MEMBERS));
        ArrayList<LivingBridgeQuantifiedPlanner.MobToken> reversed = new ArrayList<>(tokens);
        java.util.Collections.reverse(reversed);
        assertEquals(result, LivingBridgeQuantifiedPlanner.cluster(reversed, 2.0D, 2.0D));
    }

    @Test
    void plannerRetainsRecruitmentCapacityWithoutExceedingItsBound() {
        for (int size : List.of(17, 20, 21, 48, 49)) {
            ArrayList<LivingBridgeQuantifiedPlanner.MobToken> tokens = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                tokens.add(new LivingBridgeQuantifiedPlanner.MobToken(new UUID(0L, i + 1L), 0, 64, 0));
            }

            var clusters = LivingBridgeQuantifiedPlanner.cluster(tokens, 0.5D, 1.0D);

            assertEquals(1, clusters.size(), "one connected cohort of " + size);
            assertEquals(Math.min(size, 48), clusters.get(0).size(), "retained cohort size " + size);
        }
    }

    @Test
    void movementBarrierRequiresFullMemberSlotBuilderAndChainState() {
        assertFalse(LivingWallCoordinator.fullChainMovementReady(15, 48, 48, 48, true));
        assertFalse(LivingWallCoordinator.fullChainMovementReady(48, 47, 48, 48, true));
        assertFalse(LivingWallCoordinator.fullChainMovementReady(48, 48, 47, 48, true));
        assertFalse(LivingWallCoordinator.fullChainMovementReady(48, 48, 48, 48, false));
        assertTrue(LivingWallCoordinator.fullChainMovementReady(48, 48, 48, 48, true));
    }
}
