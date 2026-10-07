package com.example.soundattract.ai;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivingBridgeSafeBaseRouteTest {
    @Test
    void forwardFrameMustAgreeWithPivotToSpan() {
        Vec3 forward = LivingBridgeSafeBaseRoute.verifiedForward(
                new BlockPos(0, 64, 0), new BlockPos(8, 64, 0), new Vec3(1.0D, 0.0D, 0.0D));

        assertEquals(new Vec3(1.0D, 0.0D, 0.0D), forward);
        assertNull(LivingBridgeSafeBaseRoute.verifiedForward(
                new BlockPos(0, 64, 0), new BlockPos(8, 64, 0), new Vec3(-1.0D, 0.0D, 0.0D)));
    }

    @Test
    void sourceCenterAndBucketMustBothBeStrictlyBehindPivot() {
        Vec3 pivot = new Vec3(0.5D, 64.0D, 0.5D);
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);

        assertTrue(LivingBridgeSafeBaseRoute.trustedSourceIsBehind(
                pivot, forward, new Vec3(-8.0D, 64.0D, 0.5D), new BlockPos(-8, 64, 0)));
        assertFalse(LivingBridgeSafeBaseRoute.trustedSourceIsBehind(
                pivot, forward, new Vec3(-8.0D, 64.0D, 0.5D), new BlockPos(1, 64, 0)));
    }

    @Test
    void finalizedDiagonalSiteDoesNotUseAggregateSourceAsWorkerReachabilityProof() {
        BlockPos pivot = new BlockPos(-15, -60, 1);
        BlockPos spanEnd = new BlockPos(-19, -60, 6);
        double length = Math.sqrt(41.0D);
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, pivot, spanEnd, spanEnd,
                new Vec3(-4.0D / length, 0.0D, 5.0D / length), List.of(), List.of(), List.of(),
                java.util.Set.of(), List.of());

        assertNull(LivingWallCoordinator.finalizedSourceAdmissionReasonForTest(
                plan, new Vec3(-15.0D, -60.0D, 2.0D), new BlockPos(-15, -60, 2)));
        assertNull(LivingWallCoordinator.finalizedSourceAdmissionReasonForTest(
                plan, new Vec3(-11.0D, -60.0D, -3.0D), new BlockPos(-21, -60, -5)));
        assertNull(LivingWallCoordinator.finalizedSourceAdmissionReasonForTest(
                plan, new Vec3(-11.0D, -60.0D, -3.0D), new BlockPos(-15, -60, 2)));
        assertEquals("source_frame_invalid", LivingWallCoordinator.finalizedSourceAdmissionReasonForTest(
                new LivingWallCoordinator.SitePlan(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                        pivot, spanEnd, spanEnd, Vec3.ZERO, List.of(), List.of(), List.of(), java.util.Set.of(), List.of()),
                Vec3.ZERO, pivot));
    }

    @Test
    void trustedSourceBoundaryIsStrictAndRejectsOnPlaneCenter() {
        Vec3 pivot = new Vec3(0.5D, 64.0D, 0.5D);
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);

        assertFalse(LivingBridgeSafeBaseRoute.trustedSourceIsBehind(
                pivot, forward, new Vec3(0.4999D, 64.0D, 0.5D), new BlockPos(-2, 64, 0)));
        assertFalse(LivingBridgeSafeBaseRoute.trustedSourceIsBehind(
                pivot, forward, pivot, new BlockPos(-2, 64, 0)));
    }

    @Test
    void trustedSourceValidationDistinguishesInvalidFrameFromAmbiguousSource() {
        Vec3 pivot = new Vec3(0.5D, 64.0D, 0.5D);
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        BlockPos sourceBucket = new BlockPos(-8, 64, 0);

        LivingBridgeSafeBaseRoute.Result valid = LivingBridgeSafeBaseRoute.validateTrustedSource(
                pivot, forward, new Vec3(-8.0D, 64.0D, 0.5D), sourceBucket);
        LivingBridgeSafeBaseRoute.Result ambiguous = LivingBridgeSafeBaseRoute.validateTrustedSource(
                pivot, forward, new Vec3(8.0D, 64.0D, 0.5D), sourceBucket);
        LivingBridgeSafeBaseRoute.Result invalid = LivingBridgeSafeBaseRoute.validateTrustedSource(
                pivot, new Vec3(Double.NaN, 0.0D, 0.0D), Vec3.ZERO, sourceBucket);

        assertTrue(valid.safe());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.SOURCE_SIDE_AMBIGUOUS, ambiguous.rejection());
        assertEquals(sourceBucket, ambiguous.firstUnsafeCell());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.INVALID_FRAME, invalid.rejection());
    }

    @Test
    void everyPathNodeMustRemainInSourceHalfSpaceAndPathMustCompleteAtRequestedEnd() {
        Vec3 pivot = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        BlockPos requested = new BlockPos(-3, 64, 0);
        List<BlockPos> safeNodes = List.of(new BlockPos(-7, 64, 0), requested);

        assertTrue(LivingBridgeSafeBaseRoute.validatePathShape(safeNodes, requested, true,
                requested, pivot, forward, 0.0D).safe());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.SOURCE_SIDE_CROSSING,
                LivingBridgeSafeBaseRoute.validatePathShape(
                        List.of(new BlockPos(-7, 64, 0), new BlockPos(1, 64, 0)),
                        requested, true, requested, pivot, forward, 0.0D).rejection());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.INCOMPLETE_PATH,
                LivingBridgeSafeBaseRoute.validatePathShape(safeNodes, requested, false,
                        requested, pivot, forward, 0.0D).rejection());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.WRONG_ENDPOINT,
                LivingBridgeSafeBaseRoute.validatePathShape(safeNodes, requested, true,
                        new BlockPos(4, 64, 0), pivot, forward, 0.0D).rejection());
    }

    @Test
    void pathRejectionRetainsTypedCauseAndFirstUnsafeNode() {
        Vec3 pivot = Vec3.ZERO;
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        BlockPos requested = new BlockPos(-3, 64, 0);
        BlockPos crossing = new BlockPos(1, 64, 0);

        LivingBridgeSafeBaseRoute.Result sourceSide = LivingBridgeSafeBaseRoute.validatePathShape(
                List.of(new BlockPos(-7, 64, 0), crossing), requested, true, requested, pivot, forward, 0.0D);
        LivingBridgeSafeBaseRoute.Result endpoint = LivingBridgeSafeBaseRoute.validatePathShape(
                List.of(new BlockPos(-7, 64, 0)), requested, true, new BlockPos(4, 64, 0), pivot, forward, 0.0D);
        LivingBridgeSafeBaseRoute.Result invalidFrame = LivingBridgeSafeBaseRoute.validatePathShape(
                List.of(requested), requested, true, requested, null, forward, 0.0D);

        assertEquals(LivingBridgeSafeBaseRoute.Rejection.SOURCE_SIDE_CROSSING, sourceSide.rejection());
        assertEquals(crossing, sourceSide.firstUnsafeCell());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.WRONG_ENDPOINT, endpoint.rejection());
        assertEquals(new BlockPos(4, 64, 0), endpoint.firstUnsafeCell());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.INVALID_FRAME, invalidFrame.rejection());
    }

    @Test
    void completeBodyEnvelopeMustRespectTheConfiguredSourceMargin() {
        Vec3 pivot = Vec3.ZERO;
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        AABB fullyBehind = new AABB(-2.0D, 0.0D, -0.4D, -1.25D, 1.8D, 0.4D);
        AABB intersectsMargin = new AABB(-2.0D, 0.0D, -0.4D, -0.75D, 1.8D, 0.4D);

        assertTrue(LivingBridgeSafeBaseRoute.bodyRemainsBehind(pivot, forward, fullyBehind, 1.0D));
        assertFalse(LivingBridgeSafeBaseRoute.bodyRemainsBehind(pivot, forward, intersectsMargin, 1.0D));
    }

    @Test
    void finiteBridgeEnvelopeAllowsWrappingPastEitherEndButRejectsCrossingItsFootprint() {
        BlockPos pivot = new BlockPos(0, 64, 0);
        BlockPos spanEnd = new BlockPos(8, 64, 0);
        AABB sourceSide = new AABB(-2.0D, 64.0D, -0.4D, -1.1D, 65.8D, 0.4D);
        AABB throughSpan = new AABB(3.0D, 64.0D, -0.4D, 3.9D, 65.8D, 0.4D);
        AABB aroundEnd = new AABB(8.4D, 64.0D, 3.0D, 9.3D, 65.8D, 3.8D);

        assertTrue(LivingBridgeSafeBaseRoute.bodyOutsideFiniteEnvelope(sourceSide, pivot, spanEnd, 0.8D, 64.0D, 66.0D));
        assertFalse(LivingBridgeSafeBaseRoute.bodyOutsideFiniteEnvelope(throughSpan, pivot, spanEnd, 0.8D, 64.0D, 66.0D));
        assertTrue(LivingBridgeSafeBaseRoute.bodyOutsideFiniteEnvelope(aroundEnd, pivot, spanEnd, 0.8D, 64.0D, 66.0D));
    }

    @Test
    void finitePathShapeKeepsEndpointAndWorkFrameChecksWhileAllowingSafeWrap() {
        BlockPos pivot = new BlockPos(0, 64, 0);
        BlockPos spanEnd = new BlockPos(8, 64, 0);
        BlockPos staging = new BlockPos(10, 64, 4);
        List<BlockPos> wrapped = List.of(new BlockPos(9, 64, 4), staging);

        assertTrue(LivingBridgeSafeBaseRoute.validateFinitePathShape(wrapped, staging, true, staging,
                pivot, spanEnd, 0.8D, 64.0D, 66.0D).safe());
        assertTrue(LivingBridgeSafeBaseRoute.validatePathShape(
                List.of(new BlockPos(-7, 64, 0), new BlockPos(-3, 64, 0)), new BlockPos(-3, 64, 0),
                true, new BlockPos(-3, 64, 0), Vec3.atBottomCenterOf(pivot),
                new Vec3(1.0D, 0.0D, 0.0D), 0.8D).safe());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.INCOMPLETE_PATH,
                LivingBridgeSafeBaseRoute.validateFinitePathShape(wrapped, staging, false, staging,
                        pivot, spanEnd, 0.8D, 64.0D, 66.0D).rejection());
    }

    @Test
    void completeAuthorizedPathShapeChecksOnlyReachabilityCompletenessAndEndpoint() {
        BlockPos plannedEnvelopeCrossing = new BlockPos(4, 64, 0);
        BlockPos sourceSideEnd = new BlockPos(-3, 64, 0);
        List<BlockPos> crossingRoute = List.of(plannedEnvelopeCrossing, sourceSideEnd);

        assertTrue(LivingBridgeSafeBaseRoute.validateCompletePathShape(
                crossingRoute, sourceSideEnd, true, sourceSideEnd).safe());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.INCOMPLETE_PATH,
                LivingBridgeSafeBaseRoute.validateCompletePathShape(crossingRoute, sourceSideEnd,
                        false, sourceSideEnd).rejection());
        assertEquals(LivingBridgeSafeBaseRoute.Rejection.WRONG_ENDPOINT,
                LivingBridgeSafeBaseRoute.validateCompletePathShape(crossingRoute, sourceSideEnd,
                        true, new BlockPos(-1, 64, 0)).rejection());
    }

    @Test
    void boundedCandidateSelectionSamplesWholeStablePoolExactly() {
        assertEquals(List.of(0, 1, 2, 3), LivingBridgeSafeBaseRoute.boundedCandidateIndices(4, 32));
        List<Integer> indices = LivingBridgeSafeBaseRoute.boundedCandidateIndices(95, 32);
        assertEquals(32, indices.size());
        assertEquals(0, indices.get(0));
        assertEquals(94, indices.get(31));
        assertEquals(List.of(0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45,
                        48, 51, 54, 57, 60, 63, 66, 69, 72, 75, 78, 81, 84, 87, 90, 94), indices);
        assertEquals(32, indices.stream().distinct().count());
        assertTrue(LivingBridgeSafeBaseRoute.boundedCandidateIndices(1000, 32).size() <= 32);
    }

    @Test
    void authorizationRequiresExactCurrentStructuralMemberAndReservationIdentity() {
        UUID owner = UUID.randomUUID();
        UUID formation = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(authorization(true, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 1, 1, 1, 1, owner, true, formation));
        assertFalse(authorization(false, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 1, 1, 1, 1, owner, true, formation));
        assertFalse(authorization(true, owner, formation, other, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 1, 1, 1, 1, owner, true, formation));
        assertFalse(authorization(true, owner, formation, formation, "SETTLE",
                "ASSEMBLE", 9, 9, 1, 1, 1, 1, owner, true, formation));
        assertFalse(authorization(true, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 8, 9, 1, 1, 1, 1, owner, true, formation));
        assertFalse(authorization(true, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 0, 1, 1, 1, owner, true, formation));
        assertFalse(authorization(true, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 1, 2, 1, 1, owner, true, formation));
        assertFalse(authorization(true, owner, formation, formation, "ASSEMBLE",
                "SETTLE", 9, 9, 1, 1, 1, 1, owner, true, formation));
        assertFalse(authorization(true, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 1, 1, 1, 1, other, true, formation));
        assertFalse(authorization(true, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 1, 1, 1, 1, owner, false, formation));
        assertTrue(authorization(true, owner, formation, formation, "ASSEMBLE",
                "ASSEMBLE", 9, 9, 1, 1, 1, 1, owner, true, formation));
    }

    private static boolean authorization(boolean live, UUID owner, UUID formation, UUID currentFormation,
                                         String phase, String routePhase,
                                         long revision, long currentRevision, int memberIndex, int slot,
                                         int assembled, int active, UUID member, boolean structuralBuilder,
                                         UUID ownerFormation) {
        return LivingWallCoordinator.safeBaseAuthorizationPolicy(live, true, owner, formation,
                currentFormation, (LivingWallCoordinator.Phase) phase(phase),
                (LivingWallCoordinator.Phase) phase(routePhase), revision, currentRevision, memberIndex, slot,
                assembled, active, member, structuralBuilder, ownerFormation);
    }

    @SuppressWarnings("unchecked")
    private static Object phase(String name) {
        try {
            Class<? extends Enum> phaseType = (Class<? extends Enum>) Class.forName(
                    "com.example.soundattract.ai.LivingWallCoordinator$Phase");
            return Enum.valueOf(phaseType, name);
        } catch (ClassNotFoundException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Test
    void certificateRouteMembershipAcceptsOnlyTheCertifiedPolylineOrEndpoint() {
        List<BlockPos> route = List.of(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0), new BlockPos(4, 64, 1));

        assertTrue(LivingBridgeSafeBaseRoute.routeContains(new Vec3(1.5D, 64.0D, 0.5D), route,
                new BlockPos(4, 64, 1), 0.20D));
        assertTrue(LivingBridgeSafeBaseRoute.routeContains(Vec3.atBottomCenterOf(new BlockPos(5, 64, 1)), route,
                new BlockPos(5, 64, 1), 0.20D));
        assertFalse(LivingBridgeSafeBaseRoute.routeContains(new Vec3(1.5D, 64.0D, 4.5D), route,
                new BlockPos(5, 64, 1), 0.20D));
    }

    @Test
    void timeoutAndCancellationCannotReleaseNonterminalOrDivergentOwners() {
        assertTrue(LivingBridgeSafeBaseRoute.mustPreserveOwner(true, false));
        assertTrue(LivingBridgeSafeBaseRoute.mustPreserveOwner(false, true));
        assertTrue(LivingBridgeSafeBaseRoute.mustPreserveOwner(false, false));
        assertFalse(LivingBridgeSafeBaseRoute.mustPreserveOwner(true, true));
    }

    @Test
    void sweepSubdivisionIsBoundedAtPointTwoBlocks() {
        assertEquals(5, LivingBridgeSafeBaseRoute.subdivisions(1.0D));
        assertEquals(16, LivingBridgeSafeBaseRoute.subdivisions(3.2D));
        assertTrue(LivingBridgeSafeBaseRoute.subdivisions(3.21D)
                > LivingBridgeSafeBaseRoute.MAX_SWEEP_SUBDIVISIONS);
        assertTrue(LivingBridgeSafeBaseRoute.subdivisions(Double.NaN)
                > LivingBridgeSafeBaseRoute.MAX_SWEEP_SUBDIVISIONS);
        assertTrue(LivingBridgeSafeBaseRoute.subdivisions(-0.1D)
                > LivingBridgeSafeBaseRoute.MAX_SWEEP_SUBDIVISIONS);
        assertTrue(LivingBridgeSafeBaseRoute.bodyRemainsBehind(new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D), new AABB(-2.0D, 0.0D, -0.5D, -1.0D, 1.8D, 0.5D), 0.0D));
        assertFalse(LivingBridgeSafeBaseRoute.bodyRemainsBehind(new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D), new AABB(-2.0D, 0.0D, -0.5D, 0.1D, 1.8D, 0.5D), 0.0D));
    }

    @Test
    void structuralLaneExclusivityChecksSweptSegmentsAndBodyWidths() {
        List<BlockPos> first = List.of(new BlockPos(0, 64, 0), new BlockPos(4, 64, 4));
        List<BlockPos> crossing = List.of(new BlockPos(0, 64, 4), new BlockPos(4, 64, 0));
        List<BlockPos> separated = List.of(new BlockPos(0, 64, 7), new BlockPos(4, 64, 7));

        assertTrue(LivingBridgeSafeBaseRoute.laneCorridorsConflict(first, 0.6D,
                crossing, 0.6D, 0.75D));
        assertFalse(LivingBridgeSafeBaseRoute.laneCorridorsConflict(first, 0.6D,
                separated, 0.6D, 0.75D));
        assertTrue(LivingBridgeSafeBaseRoute.laneCorridorsConflict(
                List.of(new BlockPos(0, 64, 0)), 1.0D,
                List.of(new BlockPos(0, 64, 1)), 1.0D, 0.5D));
        assertTrue(LivingBridgeSafeBaseRoute.laneCorridorsConflict(first, 0.0D,
                separated, 0.6D, 0.75D));
        assertTrue(LivingBridgeSafeBaseRoute.laneCorridorsConflict(Arrays.asList(
                new BlockPos(0, 64, 0), null), 0.6D, separated, 0.6D, 0.75D));
    }

    @Test
    void failedLaneTransferRetainsOldClaimAndSuccessfulTransferReplacesItAtomically() {
        UUID owner = UUID.randomUUID();
        UUID blocker = UUID.randomUUID();
        List<BlockPos> oldLane = List.of(new BlockPos(0, 64, 0), new BlockPos(0, 64, 4));
        List<BlockPos> replacement = List.of(new BlockPos(4, 64, 0), new BlockPos(4, 64, 4));
        List<BlockPos> blockedReplacement = List.of(new BlockPos(0, 64, 2), new BlockPos(4, 64, 2));
        Map<UUID, List<BlockPos>> claims = new HashMap<>();
        claims.put(owner, oldLane);
        claims.put(blocker, List.of(new BlockPos(1, 64, 0), new BlockPos(1, 64, 4)));
        var conflicts = (java.util.function.BiPredicate<List<BlockPos>, List<BlockPos>>) (first, second) ->
                LivingBridgeSafeBaseRoute.laneCorridorsConflict(first, 0.8D, second, 0.8D, 0.5D);

        assertFalse(LivingBridgeSafeBaseRoute.transferLaneClaim(claims, owner, blockedReplacement, conflicts));
        assertSame(oldLane, claims.get(owner));
        assertTrue(LivingBridgeSafeBaseRoute.transferLaneClaim(claims, owner, replacement, conflicts));
        assertSame(replacement, claims.get(owner));
        assertTrue(claims.containsKey(blocker));
    }
}
