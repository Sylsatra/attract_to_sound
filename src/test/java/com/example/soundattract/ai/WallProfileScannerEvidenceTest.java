package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class WallProfileScannerEvidenceTest {
    @Test
    void classifiesRepresentativeCollisionShapes() {
        assertEquals(WallProfileScanner.CollisionKind.NONE, WallProfileScanner.classifyCollisionShape(Shapes.empty(), false));
        assertEquals(WallProfileScanner.CollisionKind.FOLIAGE, WallProfileScanner.classifyCollisionShape(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), true));
        assertEquals(WallProfileScanner.CollisionKind.THIN, WallProfileScanner.classifyCollisionShape(Shapes.box(0.45D, 0.0D, 0.45D, 0.55D, 1.0D, 0.55D), false));
        assertEquals(WallProfileScanner.CollisionKind.PARTIAL, WallProfileScanner.classifyCollisionShape(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), false));
    }

    @Test
    void sweptProbeIntersectsNearbyGeometryAndMissesTheCenterlineGap() {
        AABB part = new AABB(0.4D, 0.0D, 0.4D, 0.6D, 1.0D, 0.6D);
        assertTrue(WallProfileScanner.sweptIntersects(part, new Vec3(0.0D, 0.5D, 0.0D), new Vec3(1.0D, 0.5D, 1.0D), 0.31D));
        assertFalse(WallProfileScanner.sweptIntersects(part, new Vec3(0.0D, 0.5D, 0.0D), new Vec3(1.0D, 0.5D, 0.0D), 0.05D));
    }

    @Test
    void filtersHazardsByEvidenceKindAndLeavesCollisionCellsSeparate() {
        WallProfileScanner.RayEvidence ray = new WallProfileScanner.RayEvidence(
                7L,
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                new BlockPos(1, 64, 1),
                List.of(new BlockPos(1, 64, 1), new BlockPos(2, 64, 1), new BlockPos(3, 64, 1)),
                List.of(WallProfileScanner.CollisionKind.SOLID, WallProfileScanner.CollisionKind.LAVA,
                        WallProfileScanner.CollisionKind.OPEN_TRAPDOOR),
                false,
                0.31D,
                0.0D);
        assertEquals(List.of(new BlockPos(2, 64, 1), new BlockPos(3, 64, 1)), WallProfileScanner.hazardCells(ray));
        assertEquals(0.31D, ray.probeRadius(), 1.0E-9D);
    }

    @Test
    void typedHazardsStayDistinguishedAcrossEvidenceCopies() {
        WallProfileScanner.TargetEvidence exact = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(7L),
                List.of(new BlockPos(3, 64, 4), new BlockPos(2, 64, 4)),
                List.of(WallProfileScanner.CollisionKind.LAVA, WallProfileScanner.CollisionKind.PORTAL),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "JUMP_BRIDGE",
                1.0D,
                null);
        WallProfileScanner.TargetEvidence same = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(7L),
                List.of(new BlockPos(3, 64, 4), new BlockPos(2, 64, 4)),
                List.of(WallProfileScanner.CollisionKind.LAVA, WallProfileScanner.CollisionKind.PORTAL),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "JUMP_BRIDGE",
                1.0D,
                null);
        WallProfileScanner.TargetEvidence mismatch = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(7L),
                List.of(new BlockPos(3, 64, 4), new BlockPos(2, 64, 4)),
                List.of(WallProfileScanner.CollisionKind.VOID, WallProfileScanner.CollisionKind.PORTAL),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "JUMP_BRIDGE",
                1.0D,
                null);
        assertTrue(exact.matchesExactly(same));
        assertFalse(exact.matchesExactly(mismatch));
        assertTrue(WallProfileScanner.isHazardKind(WallProfileScanner.CollisionKind.VOID));
        assertTrue(WallProfileScanner.isHazardKind(WallProfileScanner.CollisionKind.PORTAL));
    }

    @Test
    void ironBarsRemainedThinObstructionEvidenceButNotSupportGeometry() {
        assertEquals(WallProfileScanner.CollisionKind.THIN,
                WallProfileScanner.classifyCollisionShape(Shapes.box(0.4375D, 0.0D, 0.4375D, 0.5625D, 1.0D, 0.5625D), false));
        assertFalse(LivingWallCoordinator.isReliableSupportShape(Shapes.box(0.4375D, 0.0D, 0.4375D, 0.5625D, 1.0D, 0.5625D)));
        assertTrue(LivingWallCoordinator.isReliableSupportShape(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)));
    }

    @Test
    void fallbackEvidenceHookIsBoundedAndDeterministicForNullWorlds() {
        assertEquals(List.of(), WallProfileScanner.scanEvidenceFallback(null, null, null, 2, 2));
        assertEquals(48, WallProfileScanner.MAX_LOGICAL_FALLBACK_RAYS);
    }

    @Test
    void hazardOnlyRaysRemainVisibleWithoutACollisionHit() {
        WallProfileScanner.RayEvidence ray = new WallProfileScanner.RayEvidence(
                11L,
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D),
                null,
                List.of(new BlockPos(4, 70, 4), new BlockPos(5, 70, 4)),
                List.of(WallProfileScanner.CollisionKind.LAVA, WallProfileScanner.CollisionKind.SOLID),
                false,
                0.31D,
                0.0D);
        assertEquals(List.of(new BlockPos(4, 70, 4)), WallProfileScanner.hazardCells(ray));
    }

    @Test
    void voidProjectionSeamFailsClosedForNullInputs() {
        assertEquals(WallProfileScanner.CollisionKind.UNKNOWN, WallProfileScanner.projectVoidExposure(null, null));
        assertEquals(WallProfileScanner.CollisionKind.UNKNOWN, WallProfileScanner.projectVoidExposure(null, new BlockPos(1, 64, 1)));
    }

    @Test
    void unloadedBelowEmptyIsUnknownAndScanIncomplete() {
        BlockPos cell = new BlockPos(1, 64, 1);
        Predicate<BlockPos> loaded = pos -> pos.getY() >= 64;
        Function<BlockPos, net.minecraft.world.level.block.state.BlockState> stateProvider = pos -> null;
        BiPredicate<BlockPos, net.minecraft.world.level.block.state.BlockState> emptyProbe = (pos, state) -> true;
        assertEquals(WallProfileScanner.CollisionKind.UNKNOWN,
                WallProfileScanner.projectVoidExposure(cell, loaded, stateProvider, emptyProbe));
    }

    @Test
    void fullyObservedEmptyColumnIsVoidExposedAndComplete() {
        BlockPos cell = new BlockPos(1, 64, 1);
        Predicate<BlockPos> loaded = pos -> true;
        Function<BlockPos, net.minecraft.world.level.block.state.BlockState> stateProvider = pos -> null;
        BiPredicate<BlockPos, net.minecraft.world.level.block.state.BlockState> emptyProbe = (pos, state) -> true;
        assertEquals(WallProfileScanner.CollisionKind.VOID_EXPOSED,
                WallProfileScanner.projectVoidExposure(cell, loaded, stateProvider, emptyProbe));
    }

    @Test
    void targetEvidenceCandidateKeyStaysDeterministicForTheSameCorridor() {
        WallProfileScanner.TargetEvidence first = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L, 2L),
                List.of(new BlockPos(3, 64, 4), new BlockPos(2, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        WallProfileScanner.TargetEvidence same = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L, 2L),
                List.of(new BlockPos(3, 64, 4), new BlockPos(2, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        WallProfileScanner.TargetEvidence different = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(9L),
                List.of(new BlockPos(3, 64, 4), new BlockPos(2, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 62, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                2.0D,
                "stale");
        assertTrue(first.matchesExactly(same));
        assertFalse(first.matchesExactly(different));
    }

    @Test
    void boundedShapeCacheEvictsBeyondTheConfiguredLimit() {
        ResourceLocation dimension = new ResourceLocation("soundattract", "test");
        for (int index = 0; index < 4097; index++) {
            WallProfileScanner.recordShapeCacheSample(dimension, 0L, index, index * 31L, 0, WallProfileScanner.CollisionKind.PARTIAL);
        }
        assertTrue(WallProfileScanner.shapeCacheSize(dimension) <= 4096);
        WallProfileScanner.clearCaches(dimension);
        assertEquals(0, WallProfileScanner.shapeCacheSize(dimension));
    }

    @Test
    void refineBudgetConstantRemainsBounded() {
        assertEquals(16, WallProfileScanner.MAX_REFINE_RAYS_PER_TICK);
    }

    @Test
    void recordsEmptyEvidenceForNullInputsWithoutThrowing() {
        assertEquals(List.of(), WallProfileScanner.scanEvidence(null, null, null, 2, 2));
    }

    @Test
    void targetScanDirectionsRemainDeterministicAroundThreat() {
        List<Vec3> directions = LivingWallCoordinator.targetScanDirections(new Vec3(0.5D, 80.0D, 0.5D), new BlockPos(16, 80, 0));
        assertEquals(5, directions.size());
        assertTrue(directions.stream().allMatch(direction -> Math.abs(direction.length() - 1.0D) < 1.0E-9D));
    }

    @Test
    void targetScanDirectionsRemainSymmetricAcrossMirroredTargets() {
        List<Vec3> positive = LivingWallCoordinator.targetScanDirections(new Vec3(0.5D, 80.0D, 0.5D), new BlockPos(16, 80, 4));
        List<Vec3> negative = LivingWallCoordinator.targetScanDirections(new Vec3(0.5D, 80.0D, 0.5D), new BlockPos(16, 80, -4));
        assertEquals(positive.size(), negative.size());
        for (Vec3 left : positive) {
            assertTrue(negative.stream().anyMatch(right -> Math.abs(left.x - right.x) < 1.0E-9D
                    && Math.abs(left.z + right.z) < 1.0E-9D));
        }
    }

    @Test
    void sameLevelTargetStaysCompatibleAcrossDifferentThreatHeights() {
        Vec3 center = new Vec3(0.5D, 64.0D, 0.5D);
        assertTrue(LivingWallCoordinator.isTargetVerticalCompatible(center, new BlockPos(8, 60, 0), new BlockPos(4, 64, 0)));
        assertTrue(LivingWallCoordinator.isTargetVerticalCompatible(center, new BlockPos(8, 68, 0), new BlockPos(4, 65, 0)));
    }

    @Test
    void scenarioGeometryParityStillMapsToExistingBridgeKinds() {
        assertEquals(LivingWallCoordinator.BridgeKind.TILT,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE));
        assertEquals(LivingWallCoordinator.BridgeKind.JUMP,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE));
        assertEquals(LivingWallCoordinator.BridgeKind.DROP,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE));
    }

    @Test
    void threatFacingRequiresAtLeastOneBlockOfForwardProjection() {
        BlockPos pivot = new BlockPos(0, 64, 0);
        BlockPos threat = new BlockPos(4, 64, 0);

        assertFalse(WallProfileScanner.isThreatFacing(pivot, pivot, threat));
        assertFalse(WallProfileScanner.isThreatFacing(pivot, new BlockPos(0, 64, 3), pivot));
        assertTrue(WallProfileScanner.isThreatFacing(pivot, new BlockPos(1, 64, 0), threat));
        assertTrue(WallProfileScanner.isThreatFacing(pivot, new BlockPos(1, 64, 1), threat));
        assertFalse(WallProfileScanner.isThreatFacing(pivot, new BlockPos(0, 64, 1), threat));
        assertFalse(WallProfileScanner.isThreatFacing(pivot, new BlockPos(-1, 64, 0), threat));
        assertEquals(1.0D, WallProfileScanner.threatProgressProjection(pivot, new BlockPos(1, 64, 0), threat), 1.0E-9D);
        assertEquals(0.0D, WallProfileScanner.threatProgressProjection(pivot, new BlockPos(0, 64, 1), threat), 1.0E-9D);
    }

    @Test
    void exteriorLandingCandidatesStayOnTheRayFacingSide() {
        BlockPos structural = new BlockPos(0, 5, 0);
        BlockPos pivot = new BlockPos(0, 4, 0);
        List<BlockPos> candidates = WallProfileScanner.exteriorLandingCandidates(
                structural, pivot, new Vec3(1.0D, 0.0D, 0.0D), 2, 1);

        assertEquals(new BlockPos(1, 5, 0), candidates.get(0));
        assertTrue(candidates.contains(new BlockPos(2, 6, 1)));
        assertTrue(candidates.contains(new BlockPos(0, 5, 1)));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.getX() > structural.getX()));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> candidates.add(new BlockPos(3, 5, 0)));
    }

    @Test
    void boundedLandingSearchIncludesElevatedNearbySurfaces() {
        BlockPos wall = new BlockPos(24, 64, 20);
        List<BlockPos> candidates = WallProfileScanner.exteriorLandingCandidates(
                wall, new BlockPos(14, 64, 16), new Vec3(1, 0, 1).normalize(), 12, 8);
        List<BlockPos> admitted = candidates.stream().limit(127).toList();
        assertTrue(admitted.contains(wall.offset(1, 8, 1)));
        assertTrue(admitted.contains(wall.offset(1, -8, 1)));
    }
}
