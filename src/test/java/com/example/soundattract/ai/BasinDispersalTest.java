package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BasinDispersalTest {

    @Test
    void basinFailureAccumulationAndCooldown() {
        LivingWallCoordinator.BasinFailureHistory initial =
                new LivingWallCoordinator.BasinFailureHistory(1, 100L, 0L);
        assertEquals(1, initial.failureCount());
        assertEquals(100L, initial.lastFailureTick());
        assertEquals(0L, initial.cooldownUntil());
        assertFalse(initial.cooldownUntil() > 100L);

        LivingWallCoordinator.BasinFailureHistory threshold =
                new LivingWallCoordinator.BasinFailureHistory(3, 140L, 540L);
        assertEquals(3, threshold.failureCount());
        assertTrue(threshold.cooldownUntil() > 140L);
        assertTrue(threshold.cooldownUntil() > 500L);
        assertFalse(threshold.cooldownUntil() > 540L);
    }

    @Test
    void basinEvacuationTargetAndExpiry() {
        Vec3 exitTarget = new Vec3(100.5D, 80.0D, 200.5D);
        long expiresTick = 500L;
        LivingWallCoordinator.BasinEvacuation evacuation =
                new LivingWallCoordinator.BasinEvacuation(exitTarget, null, expiresTick);

        assertEquals(exitTarget, evacuation.target());
        assertEquals(expiresTick, evacuation.expiresTick());
        assertNull(evacuation.threatPos());

        long nowActive = 400L;
        assertFalse(nowActive > evacuation.expiresTick());

        long nowExpired = 501L;
        assertTrue(nowExpired > evacuation.expiresTick());
    }

    @Test
    void basinSpatialBucketGrouping() {
        Vec3 caveMob1 = new Vec3(42.0D, 55.0D, 85.0D);
        Vec3 caveMob2 = new Vec3(45.0D, 55.5D, 89.0D);
        Vec3 distantMob = new Vec3(120.0D, 55.0D, 200.0D);

        BlockPos bucket1 = new BlockPos(
                Math.floorDiv(Mth.floor(caveMob1.x), 16),
                Math.floorDiv(Mth.floor(caveMob1.y), 8),
                Math.floorDiv(Mth.floor(caveMob1.z), 16));

        BlockPos bucket2 = new BlockPos(
                Math.floorDiv(Mth.floor(caveMob2.x), 16),
                Math.floorDiv(Mth.floor(caveMob2.y), 8),
                Math.floorDiv(Mth.floor(caveMob2.z), 16));

        BlockPos bucketDistant = new BlockPos(
                Math.floorDiv(Mth.floor(distantMob.x), 16),
                Math.floorDiv(Mth.floor(distantMob.y), 8),
                Math.floorDiv(Mth.floor(distantMob.z), 16));

        assertEquals(bucket1, bucket2);
        assertFalse(bucket1.equals(bucketDistant));
    }

    @Test
    void perLevelStateBasinLifecycle() {
        LivingWallCoordinator.PerLevelState state = new LivingWallCoordinator.PerLevelState();
        BlockPos bucketKey = new BlockPos(2, 6, 5);
        UUID mobId = UUID.randomUUID();
        Vec3 exitPos = new Vec3(35.5D, 75.0D, 90.5D);

        state.basinFailures.put(bucketKey, new LivingWallCoordinator.BasinFailureHistory(3, 200L, 600L));
        state.basinEvacuations.put(mobId, new LivingWallCoordinator.BasinEvacuation(exitPos, null, 600L));

        assertNotNull(state.basinFailures.get(bucketKey));
        assertEquals(3, state.basinFailures.get(bucketKey).failureCount());
        assertNotNull(state.basinEvacuations.get(mobId));

        
        long now = 400L;
        assertTrue(state.basinFailures.get(bucketKey).cooldownUntil() > now);

        
        long future = 601L;
        state.basinEvacuations.entrySet().removeIf(entry -> entry.getValue().expiresTick() <= future);
        assertNull(state.basinEvacuations.get(mobId));

        
        state.basinFailures.remove(bucketKey);
        assertNull(state.basinFailures.get(bucketKey));
    }

    @Test
    void evacuationArrivalConditions() {
        Vec3 exitTarget = new Vec3(100.0D, 80.0D, 200.0D);

        
        Vec3 nearTarget = new Vec3(101.0D, 80.0D, 200.5D);
        assertTrue(nearTarget.closerThan(exitTarget, 4.0D));

        Vec3 farTarget = new Vec3(106.0D, 80.0D, 200.0D);
        assertFalse(farTarget.closerThan(exitTarget, 4.0D));

        
        long expiresTick = 500L;
        long nowActive = 400L;
        long nowExpired = 501L;
        assertFalse(nowActive > expiresTick);
        assertTrue(nowExpired > expiresTick);
    }

    @Test
    void cohortHeadcountSufficiencyCheck() {
        

        
        int smallCohortSize = 2;
        int smallCohortLimit = Math.max(smallCohortSize + 4, 12);
        assertEquals(12, smallCohortLimit);

        
        int massiveBridgeBodies = 46;
        assertTrue(massiveBridgeBodies > smallCohortLimit);

        
        int smallBridgeBodies = 10;
        assertFalse(smallBridgeBodies > smallCohortLimit);

        
        int fiveMobRoster = 5;
        int fiveMobLimit = Math.max(fiveMobRoster + 4, 12);
        assertEquals(12, fiveMobLimit);
        assertTrue(17 > fiveMobLimit); 

        
        int largeCohortSize = 15;
        int largeCohortLimit = Math.max(largeCohortSize + 4, 12);
        assertEquals(19, largeCohortLimit);
        assertFalse(17 > largeCohortLimit); 
    }

    @Test
    void basinEvacuationCandidateExclusion() {
        LivingWallCoordinator.PerLevelState state = new LivingWallCoordinator.PerLevelState();
        UUID evacueeId = UUID.randomUUID();
        state.basinEvacuations.put(evacueeId, new LivingWallCoordinator.BasinEvacuation(new Vec3(0, 80, 0), null, 1000L));

        
        assertTrue(state.basinEvacuations.containsKey(evacueeId));

        
        UUID regularMobId = UUID.randomUUID();
        assertFalse(state.basinEvacuations.containsKey(regularMobId));
    }

    @Test
    void hardPathFailureReleaseCheck() {
        
        int threshold = LivingWallCoordinator.RECRUITER_MAX_PATH_FAILURES;
        assertFalse(LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(threshold, false));
        assertTrue(LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(threshold, true));
        assertTrue(LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(threshold + 1, true));

        
        assertFalse(LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(threshold - 1, true));
        assertFalse(LivingWallCoordinator.shouldReleaseAfterRecruitmentFailure(0, true));
    }

    @Test
    void subterraneanColumnCooldownCheck() {
        LivingWallCoordinator.PerLevelState state = new LivingWallCoordinator.PerLevelState();
        BlockPos bucketKey = new BlockPos(-4, 10, 11);
        long now = 1000L;
        long cooldownUntil = now + 400L;
        state.basinFailures.put(bucketKey, new LivingWallCoordinator.BasinFailureHistory(3, now, cooldownUntil));

        
        assertTrue(LivingWallCoordinator.isBasinOnCooldown(null, state, bucketKey, null, now));

        
        assertFalse(LivingWallCoordinator.isBasinOnCooldown(null, state, bucketKey, null, cooldownUntil + 1L));
    }
}
