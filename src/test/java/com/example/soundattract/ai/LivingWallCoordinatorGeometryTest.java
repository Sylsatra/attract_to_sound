package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LivingWallCoordinatorGeometryTest {
    @Test
    void sourceDeflectionUsesFoundationColumnForMultiColumnSlots() {
        assertEquals(1, LivingWallCoordinator.foundationColumnForSlot(40, 3));
        assertEquals(2, LivingWallCoordinator.foundationColumnForSlot(41, 3));
        assertEquals(0, LivingWallCoordinator.foundationColumnForSlot(42, 3));
        assertEquals(1, LivingWallCoordinator.foundationColumnForSlot(4, 3));
    }

    @Test
    void provisionalWitnessMutationDoesNotEscapeCopy() {
        TraversalCorridorWitness live = TraversalCorridorWitness.forTest(
                net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"),
                List.of(UUID.randomUUID()), new AABB(0.0D, 0.0D, 0.0D, 4.0D, 4.0D, 4.0D));
        TraversalCorridorWitness provisional = live.copy();
        assertEquals(0, live.envelopeCount());
        assertTrue(provisional.recordEnvelopeFingerprintForTest(new BlockPos(1, 1, 1), "candidate"));
        assertTrue(provisional.recordShapeTest());
        assertEquals(0, live.envelopeCount());
        assertEquals(0, live.shapeTests());
        assertEquals(1, provisional.envelopeCount());
        assertEquals(1, provisional.shapeTests());
    }

    @Test
    void committedFoundationDoesNotConsumeRecruitmentCapacity() {
        assertFalse(LivingWallCoordinator.countsAsActiveRecruitmentTicket(
                LivingWallCoordinator.MobTicketState.ASSEMBLED, true));
        assertTrue(LivingWallCoordinator.countsAsActiveRecruitmentTicket(
                LivingWallCoordinator.MobTicketState.ASSEMBLED, false));
        assertFalse(LivingWallCoordinator.countsAsActiveRecruitmentTicket(
                LivingWallCoordinator.MobTicketState.RELEASED, false));
    }

    @Test
    void bridgeKindMappingMatchesDesignTaxonomy() {
        assertEquals(LivingWallCoordinator.BridgeKind.TILT,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE));
        assertEquals(LivingWallCoordinator.BridgeKind.JUMP,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE));
        assertEquals(LivingWallCoordinator.BridgeKind.DROP,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE));
        assertTrue(LivingWallCoordinator.requiresTilt(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE));
        assertFalse(LivingWallCoordinator.isDirectSpan(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE));
        assertTrue(LivingWallCoordinator.isDirectSpan(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE));
        assertTrue(LivingWallCoordinator.isDirectSpan(LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE));
    }

    @Test
    void wallLedgeRequiresElevationTowardTheHighWall() {
        BlockPos pivot = new BlockPos(-20, -60, 5);

        assertTrue(LivingWallCoordinator.isElevatedWallLedge(pivot, new BlockPos(-10, -57, 10)));
        assertFalse(LivingWallCoordinator.isElevatedWallLedge(pivot, new BlockPos(-20, -60, 7)));
        assertFalse(LivingWallCoordinator.isElevatedWallLedge(pivot, new BlockPos(-20, -61, 7)));
    }

    @Test
    void resolvedCorridorEvidenceRequiresOrderedLandingAndInvasion() {
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(12, 66, 0),
                java.util.List.of(1L),
                java.util.List.of(new BlockPos(8, 64, 0)),
                java.util.List.of(WallProfileScanner.CollisionKind.LAVA),
                new BlockPos(10, 64, 0),
                new BlockPos(10, 63, 0),
                new BlockPos(4, 64, 0),
                new BlockPos(20, 64, 0),
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE.name(),
                1.0D,
                null,
                java.util.List.of(new BlockPos(10, 64, 0)),
                new BlockPos(10, 64, 0),
                new BlockPos(4, 64, 0),
                new BlockPos(12, 66, 0),
                new BlockPos(12, 66, 0));

        assertTrue(LivingWallCoordinator.committedCorridorMatchesEvidence(
                new LivingWallCoordinator.SitePlan(
                        LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE,
                        new BlockPos(4, 64, 0), new BlockPos(12, 66, 0), new BlockPos(12, 66, 0),
                        new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(new BlockPos(4, 64, 0)),
                        java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(3, 64, 0))),
                        java.util.List.of(new BlockPos(5, 64, 0)), java.util.Set.of(new BlockPos(4, 64, 0)),
                        java.util.List.of(new BlockPos(8, 64, 0))),
                evidence));
    }

    @Test
    void canonicalOrderingAllowsContactPastLandingWhenFeetOrderingIsValid() throws Exception {
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0),
                new BlockPos(4, 65, 0),
                new BlockPos(6, 67, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(9, 67, 0),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(1L));

        assertTrue(LivingWallCoordinator.orderedCorridorTransitionValidForTest(
                corridor, null, new BlockPos(12, 67, 0),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE));
    }

    @Test
    void rayHazardWitnessesMayBeLateralToTheCorridorCenterline() {
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(6, 67, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(9, 67, 0),
                java.util.List.of(new BlockPos(2, 64, 4)),
                java.util.List.of(WallProfileScanner.CollisionKind.LAVA),
                java.util.List.of(1L));

        assertTrue(LivingWallCoordinator.orderedCorridorTransitionValidForTest(
                corridor, null, new BlockPos(12, 67, 0),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE));
    }

    @Test
    void sourceSideHazardPastLandingStillRejectsCorridorOrdering() {
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(6, 67, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(9, 67, 0),
                java.util.List.of(new BlockPos(8, 67, 0)),
                java.util.List.of(WallProfileScanner.CollisionKind.LAVA),
                java.util.List.of(1L));

        assertFalse(LivingWallCoordinator.orderedCorridorTransitionValidForTest(
                corridor, null, new BlockPos(12, 67, 0),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE));
    }

    @Test
    void canonicalCenterlineNeverIncludesObstacleContactCellAsBodyWaypoint() {
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0),
                new BlockPos(4, 65, 0),
                new BlockPos(6, 67, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(7, 67, 1),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());

        assertFalse(corridor.centerlineCells().contains(corridor.obstacleContactCell()));
    }

    @Test
    void stalePivotInvasionEndpointWouldExceedInclusiveHorizontalGap() throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod("horizontalDistance", BlockPos.class, BlockPos.class);
        method.setAccessible(true);
        double stale = (double) method.invoke(null, new BlockPos(0, -47, -5), new BlockPos(7, -46, -18));
        double corrected = (double) method.invoke(null, new BlockPos(0, -47, -5), new BlockPos(7, -46, -12));

        assertTrue(stale > 12.0D);
        assertTrue(corrected <= 12.0D);
    }

    @Test
    void exactConfiguredLengthRemainsInclusive() throws Exception {
        Method bridgeLength = LivingWallCoordinator.class.getDeclaredMethod("bridgeLength", BlockPos.class, BlockPos.class);
        bridgeLength.setAccessible(true);
        double exact = (double) bridgeLength.invoke(null, new BlockPos(0, 64, 0), new BlockPos(16, 64, 0));
        double over = (double) bridgeLength.invoke(null, new BlockPos(0, 64, 0), new BlockPos(17, 64, 0));

        assertEquals(16.0D, exact, 1.0E-6D);
        assertTrue(over > 16.0D);
    }

    @Test
    void adapterSelectionKeepsLegacyScenarioIdentifiersAndKindModesDistinct() {
        assertEquals(LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT,
                LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.NONE).orElseThrow().kind());
        assertEquals(LivingWallBridgeAdapterPlanner.AdapterKind.DROP,
                LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE).orElseThrow().kind());
        assertEquals(LivingWallBridgeAdapterPlanner.AdapterKind.JUMP,
                LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE).orElseThrow().kind());
        assertEquals(LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT,
                LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE).orElseThrow().kind());
        assertFalse(LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.HAZARD_AND_WALL).isPresent());
    }

    @Test
    void downwardThreatAllowsSameLevelCliffEdgeAnchorForJumpOrDropBridge() {
        Vec3 mobClusterCenter = new Vec3(0.5D, 64.0D, 0.5D);
        BlockPos lowerThreat = new BlockPos(8, 60, 0);

        assertTrue(LivingWallCoordinator.isTargetVerticalCompatible(
                mobClusterCenter, lowerThreat, new BlockPos(4, 64, 0)));
        assertTrue(LivingWallCoordinator.isTargetVerticalCompatible(
                mobClusterCenter, lowerThreat, new BlockPos(4, 63, 0)));
        assertFalse(LivingWallCoordinator.isTargetVerticalCompatible(
                mobClusterCenter, lowerThreat, new BlockPos(4, 65, 0)));
    }

    @Test
    void formationSlotPositionUsesFeetCellAlignment() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(14, 70, 10));
        setField(formation, "spanEnd", new BlockPos(18, 62, 10));
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", 1);
        setField(formation, "horizontalTouchStep", 1.0D);
        setField(formation, "pillarStep", 1.0D);
        setField(formation, "directSpan", false);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE,
                new BlockPos(10, 64, 10), new BlockPos(14, 70, 10), new BlockPos(18, 62, 10),
                new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of()));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        method.setAccessible(true);
        Vec3 position = (Vec3) method.invoke(null, formation, 0, 0.0D);

        assertEquals(64.0D, position.y, 1.0E-6D);
    }

    @Test
    void wallLedgeGeometryBuildsContactingSourceChainThenTiltsToSpanEnd() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(18, 67, 10));
        setField(formation, "spanEnd", new BlockPos(18, 67, 10));
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", 5);
        setField(formation, "horizontalTouchStep", 1.0D);
        setField(formation, "pillarStep", 2.0D);
        setField(formation, "directSpan", false);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(10, 64, 10), new BlockPos(18, 67, 10), new BlockPos(18, 67, 10),
                new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new BlockPos(12, 64, 10))));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        method.setAccessible(true);
        Vec3 source = (Vec3) method.invoke(null, formation, 4, 0.0D);
        Vec3 tilted = (Vec3) method.invoke(null, formation, 4, 1.0D);

        Method chainMethod = LivingWallCoordinator.class.getDeclaredMethod("bottomUpChainPosition",
                formation.getClass(), int.class, int.class, double.class);
        chainMethod.setAccessible(true);
        Vec3 midpoint = (Vec3) chainMethod.invoke(null, formation, 4, 4, 0.5D);
        Vec3 precedingMidpoint = (Vec3) chainMethod.invoke(null, formation, 3, 4, 0.5D);
        AABB movingBox = new AABB(midpoint.x - 0.3D, midpoint.y, midpoint.z - 0.3D,
                midpoint.x + 0.3D, midpoint.y + 1.95D, midpoint.z + 0.3D);
        AABB parentBox = new AABB(precedingMidpoint.x - 0.3D, precedingMidpoint.y, precedingMidpoint.z - 0.3D,
                precedingMidpoint.x + 0.3D, precedingMidpoint.y + 1.95D, precedingMidpoint.z + 0.3D);

        assertEquals(10.5D, source.x, 1.0E-6D);
        assertEquals(10.5D, source.z, 1.0E-6D);
        assertEquals(71.6D, source.y, 1.0E-6D);
        assertTrue(LivingWallCoordinator.controlledTiltBodiesConnected(movingBox, parentBox, 0.0675D),
                "the moving body must keep its ordinary collision box touching the body below it");
        assertEquals(18.5D, tilted.x, 1.0E-6D);
        assertEquals(67.0D, tilted.y, 1.0E-6D);
        assertEquals(10.5D, tilted.z, 1.0E-6D);
    }

    @Test
    void dropToLedgeBuildsContactingSourceChainThenDeploysDownToAnchor() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(14, 60, 10));
        setField(formation, "spanEnd", new BlockPos(14, 60, 10));
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", 3);
        setField(formation, "horizontalTouchStep", 1.0D);
        setField(formation, "pillarStep", 2.0D);
        setField(formation, "directSpan", true);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE,
                new BlockPos(10, 64, 10), new BlockPos(14, 60, 10), new BlockPos(14, 60, 10),
                new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new BlockPos(12, 64, 10))));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        method.setAccessible(true);
        Vec3 sourceLayer0 = (Vec3) method.invoke(null, formation, 0, 0.0D);
        Vec3 sourceLayer1 = (Vec3) method.invoke(null, formation, 1, 0.0D);
        Vec3 deployedLayer0 = (Vec3) method.invoke(null, formation, 0, 1.0D);
        Vec3 deployedLayer2 = (Vec3) method.invoke(null, formation, 2, 1.0D);

        assertEquals(64.0D, sourceLayer0.y, 1.0E-6D);
        assertEquals(62.0D, sourceLayer1.y, 1.0E-6D);
        assertEquals(64.0D, deployedLayer0.y, 1.0E-6D);
        assertEquals(60.0D, deployedLayer2.y, 1.0E-6D);
        assertEquals(14.5D, deployedLayer2.x, 1.0E-6D);
    }

    @Test
    void wallLandingChainIgnoresLongerEgressAndKeepsOrdinaryBoxesLinkedDuringBottomUpTilt() throws Exception {
        BlockPos pivot = new BlockPos(0, 64, 0);
        BlockPos spanEnd = new BlockPos(4, 66, 4);
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                pivot, pivot, spanEnd, new BlockPos(2, 66, 2), spanEnd,
                new BlockPos(2, 65, 1), java.util.List.of(), java.util.List.of(), java.util.List.of())
                .withCenterline(java.util.List.of(
                        pivot,
                        new BlockPos(0, 64, 1),
                        new BlockPos(0, 65, 2),
                        new BlockPos(1, 66, 2),
                        new BlockPos(2, 66, 2),
                        new BlockPos(3, 66, 3),
                        spanEnd));
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                pivot, spanEnd, spanEnd, new Vec3(1.0D, 0.0D, 1.0D), java.util.List.of(pivot),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(-1, 64, 0))),
                java.util.List.of(new BlockPos(0, 64, -1)), java.util.Set.of(pivot), java.util.List.of(), corridor);
        double horizontalStep = LivingWallCoordinator.lateralSpacingForBodyWidth(0.6D);
        double verticalStep = LivingWallCoordinator.layerSpacingForBodyHeight(1.95D);
        int layers = LivingWallCoordinator.requiredLayerCountForTilt(plan, horizontalStep, verticalStep);
        assertEquals(LivingWallCoordinator.requiredLayerCountForTilt(
                Math.sqrt(8.0D), 2.0D, horizontalStep, verticalStep), layers,
                "body count must describe the foundation-to-landing chain, not the longer walking egress");

        Object formation = bareFormation();
        setField(formation, "pivot", pivot);
        setField(formation, "anchor", spanEnd);
        setField(formation, "spanEnd", spanEnd);
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", layers);
        setField(formation, "horizontalTouchStep", horizontalStep);
        setField(formation, "pillarStep", verticalStep);
        setField(formation, "sourceStagingOffset", Vec3.ZERO);
        setField(formation, "plan", plan);

        Method chainMethod = LivingWallCoordinator.class.getDeclaredMethod("bottomUpChainPosition",
                formation.getClass(), int.class, int.class, double.class);
        chainMethod.setAccessible(true);
        Method slotMethod = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        slotMethod.setAccessible(true);
        assertTrue(Vec3.atBottomCenterOf(corridor.bridgeLandingFeet()).distanceToSqr(
                        (Vec3) slotMethod.invoke(null, formation, layers - 1, 1.0D)) < 1.0E-9D,
                "the structural chain stops at the landing; egress remains a walking route");
        for (int movingLayer = 1; movingLayer < layers; movingLayer++) {
            for (double progress : new double[]{0.0D, 0.25D, 0.5D, 0.75D, 1.0D}) {
                for (int slot = 1; slot < layers; slot++) {
                    Vec3 lower = (Vec3) chainMethod.invoke(null, formation, slot - 1, movingLayer, progress);
                    Vec3 upper = (Vec3) chainMethod.invoke(null, formation, slot, movingLayer, progress);
                    AABB lowerBox = new AABB(lower.x - 0.3D, lower.y, lower.z - 0.3D,
                            lower.x + 0.3D, lower.y + 1.95D, lower.z + 0.3D);
                    AABB upperBox = new AABB(upper.x - 0.3D, upper.y, upper.z - 0.3D,
                            upper.x + 0.3D, upper.y + 1.95D, upper.z + 0.3D);
                    assertTrue(LivingWallCoordinator.controlledTiltBodiesConnected(lowerBox, upperBox, 0.0675D),
                            "normal boxes separated at moving layer " + movingLayer + ", slot " + slot
                                    + ", progress " + progress);
                }
            }
        }
    }

    @Test
    void wallLedgeSourceChainStartsAtCommittedFoundationAndKeepsContactSpacing() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(22, 70, 15));
        setField(formation, "spanEnd", new BlockPos(22, 70, 15));
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", 4);
        setField(formation, "horizontalTouchStep", 0.62D);
        setField(formation, "pillarStep", 2.03D);
        setField(formation, "sourceStagingOffset", Vec3.ZERO);
        Map<Integer, Vec3> structuralSlots = new HashMap<>();
        Vec3 foundation = new Vec3(12.5D, 64.0D, 16.5D);
        structuralSlots.put(0, foundation);
        setField(formation, "structuralSlotPositions", structuralSlots);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(10, 64, 10), new BlockPos(22, 70, 15), new BlockPos(22, 70, 15),
                new Vec3(12.0D, 0.0D, 5.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new BlockPos(12, 64, 10))));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        method.setAccessible(true);
        Vec3 slotZero = (Vec3) method.invoke(null, formation, 0, 0.0D);
        Vec3 slotOne = (Vec3) method.invoke(null, formation, 1, 0.0D);

        assertEquals(foundation, slotZero);
        assertEquals(1.90D, slotZero.distanceTo(slotOne), 1.0E-6D);
        assertEquals(foundation.y + 1.90D, slotOne.y, 1.0E-6D);
        assertEquals(foundation.x, slotOne.x, 1.0E-6D);
        assertEquals(foundation.z, slotOne.z, 1.0E-6D);
        assertTrue(slotOne.y > slotZero.y);
    }

    @Test
    void sourceOffsetAppliesToCommittedFoundationWithoutChangingTargetGeometry() throws Exception {
        Object formation = bareFormation();
        Vec3 foundation = new Vec3(12.5D, 64.0D, 16.5D);
        Vec3 sourceOffset = new Vec3(-2.0D, 1.0D, 3.0D);
        BlockPos spanEnd = new BlockPos(22, 70, 15);
        Map<Integer, Vec3> structuralSlots = new HashMap<>();
        structuralSlots.put(0, foundation);
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", spanEnd);
        setField(formation, "spanEnd", spanEnd);
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", 2);
        setField(formation, "pillarStep", 1.90D);
        setField(formation, "structuralSlotPositions", structuralSlots);
        setField(formation, "sourceStagingOffset", sourceOffset);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(10, 64, 10), spanEnd, spanEnd, new Vec3(12.0D, 0.0D, 5.0D),
                java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new BlockPos(12, 64, 10))));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        method.setAccessible(true);
        Vec3 source = (Vec3) method.invoke(null, formation, 0, 0.0D);
        Vec3 elevatedSource = (Vec3) method.invoke(null, formation, 1, 0.0D);
        assertEquals(foundation.add(sourceOffset), source);
        assertEquals(source.y + 1.90D, elevatedSource.y, 1.0E-6D);
        assertEquals(foundation.x + sourceOffset.x, elevatedSource.x, 1.0E-6D);
        assertEquals(foundation.z + sourceOffset.z, elevatedSource.z, 1.0E-6D);
        assertEquals(foundation, structuralSlots.get(0));
        assertEquals(spanEnd, getField(formation, "spanEnd"));
    }

    @Test
    void wallLedgeGeometryUsesShortenedSpanEndWhenEarlyContactBecomesEndpoint() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(18, 70, 10));
        setField(formation, "spanEnd", new BlockPos(14, 67, 10));
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", 5);
        setField(formation, "horizontalTouchStep", 1.0D);
        setField(formation, "pillarStep", 1.5D);
        setField(formation, "directSpan", false);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(10, 64, 10), new BlockPos(18, 70, 10), new BlockPos(14, 67, 10),
                new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new BlockPos(12, 64, 10))));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        method.setAccessible(true);
        Vec3 tilted = (Vec3) method.invoke(null, formation, 4, 1.0D);

        assertEquals(14.5D, tilted.x, 1.0E-6D);
        assertEquals(67.0D, tilted.y, 1.0E-6D);
        assertTrue(tilted.x < 18.5D);
        assertTrue(tilted.y < 70.0D);
    }

    @Test
    void pillarStepForTiltNeverUndershootsBodyClearance() {
        double chain = 10.488088481701515D;
        double bodyHeight = 1.8700000476837157D;
        double layerSpacing = LivingWallCoordinator.layerSpacingForBodyHeight(bodyHeight);
        double step = LivingWallCoordinator.initialPillarStep(chain, 22, layerSpacing);

        assertEquals(layerSpacing, step, 1.0E-6D);
        assertTrue(step >= bodyHeight + 0.08D);
        assertEquals(layerSpacing, LivingWallCoordinator.initialPillarStep(0.0D, 2, layerSpacing), 1.0E-6D);
    }

    @Test
    void lateralSpacingIncludesStructuralMargin() {
        assertEquals(0.62D, LivingWallCoordinator.lateralSpacingForBodyWidth(0.6D), 1.0E-6D);
    }

    @Test
    void slotAndBasePositionsShareTheSameLateralLayout() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(10, 64, 18));
        setField(formation, "spanEnd", new BlockPos(10, 64, 18));
        setField(formation, "columnCount", 2);
        setField(formation, "layerCount", 1);
        setField(formation, "horizontalTouchStep", 0.62D);
        setField(formation, "pillarStep", 2.03D);
        setField(formation, "directSpan", true);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE,
                new BlockPos(10, 64, 10), new BlockPos(10, 64, 18), new BlockPos(10, 64, 18),
                new Vec3(0.0D, 0.0D, 1.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(10, 64, 9))),
                java.util.List.of(new BlockPos(10, 64, 11)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of()));

        Method slotMethod = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        Method baseMethod = LivingWallCoordinator.class.getDeclaredMethod("formationSlotBasePosition",
                formation.getClass(), int.class);
        slotMethod.setAccessible(true);
        baseMethod.setAccessible(true);

        for (int slot = 0; slot < 2; slot++) {
            Vec3 slotPosition = (Vec3) slotMethod.invoke(null, formation, slot, 0.0D);
            Vec3 basePosition = (Vec3) baseMethod.invoke(null, formation, slot);
            assertEquals(basePosition.x, slotPosition.x, 1.0E-6D);
            assertEquals(basePosition.z, slotPosition.z, 1.0E-6D);
        }
    }

    @Test
    void nonDropGeometryTargetsSpanEndInsteadOfLegacyAnchor() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(14, 70, 10));
        setField(formation, "spanEnd", new BlockPos(18, 62, 10));
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", 3);
        setField(formation, "horizontalTouchStep", 1.0D);
        setField(formation, "pillarStep", 1.0D);
        setField(formation, "directSpan", false);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE,
                new BlockPos(10, 64, 10), new BlockPos(14, 70, 10), new BlockPos(18, 62, 10),
                new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of()));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        method.setAccessible(true);
        Vec3 position = (Vec3) method.invoke(null, formation, 2, 1.0D);

        assertEquals(18.5D, position.x, 1.0E-6D);
        assertEquals(62.0D, position.y, 1.0E-6D);
        assertTrue(position.y < 70.0D);
    }

    @Test
    void directJumpBridgeRemovesSuspendedAssemblyCellsFromTerrainSupportValidation() throws Exception {
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE,
                new BlockPos(10, 64, 10), new BlockPos(18, 64, 10), new BlockPos(18, 64, 10),
                new Vec3(1.0D, 0.0D, 0.0D),
                java.util.List.of(new BlockPos(10, 64, 10), new BlockPos(11, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10), new BlockPos(12, 64, 10)),
                java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new BlockPos(11, 64, 10), new BlockPos(12, 64, 10)));

        var method = LivingWallCoordinator.class.getDeclaredMethod("terrainSupportValidationCells", plan.getClass());
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<BlockPos> cells = (java.util.Set<BlockPos>) method.invoke(null, plan);

        assertFalse(cells.contains(new BlockPos(11, 64, 10)));
        assertFalse(cells.contains(new BlockPos(12, 64, 10)));
        assertTrue(cells.contains(new BlockPos(10, 64, 10)));
    }

    @Test
    void jumpBridgeThreatProgressValidationKeepsClassificationButRejectsTangentAndReverseSpans() {
        BlockPos pivot = new BlockPos(10, 64, 10);
        BlockPos threat = new BlockPos(20, 64, 10);

        assertTrue(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, pivot, new BlockPos(11, 64, 10), threat));
        assertTrue(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, pivot, new BlockPos(11, 64, 11), threat));
        assertFalse(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, pivot, new BlockPos(10, 64, 11), threat));
        assertFalse(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, pivot, new BlockPos(9, 64, 10), threat));
        assertEquals(LivingWallCoordinator.BridgeKind.JUMP,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE));
    }

    @Test
    void coldStartSeedProviderUsesSourceAwareTargetScanIdentity() throws Exception {
        Class<?> providerClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ColdStartSeedProvider");
        var providerCtor = providerClass.getDeclaredConstructor(BlockPos.class, BlockPos.class);
        providerCtor.setAccessible(true);
        Object first = providerCtor.newInstance(new BlockPos(10, 64, 10), new BlockPos(30, 64, 30));
        Object second = providerCtor.newInstance(new BlockPos(14, 64, 12), new BlockPos(30, 64, 30));
        Method keyMethod = LivingWallCoordinator.class.getDeclaredMethod("targetScanKey", providerClass, long.class);
        keyMethod.setAccessible(true);
        Object firstKey = keyMethod.invoke(null, first, 7L);
        Object secondKey = keyMethod.invoke(null, second, 7L);
        assertFalse(firstKey.equals(secondKey));
    }

    @Test
    void allBridgeKindsRequireThreatFacingSpanProgress() {
        BlockPos pivot = new BlockPos(10, 64, 10);
        BlockPos threat = new BlockPos(20, 64, 10);

        assertTrue(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, pivot, new BlockPos(11, 65, 10), threat));
        assertFalse(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, pivot, new BlockPos(9, 65, 10), threat));
        assertTrue(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE, pivot, new BlockPos(11, 63, 10), threat));
        assertFalse(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE, pivot, new BlockPos(10, 63, 11), threat));
    }

    @Test
    void duplicateTacticalRouteIsDominatedAcrossPlannersAndDistinctRouteSurvives() throws Exception {
        Object state = barePerLevelState();
        setField(state, "formations", new HashMap<java.util.UUID, Object>());

        Object existing = tacticalFormation(
                java.util.UUID.randomUUID(),
                new BlockPos(4, 64, 0), new BlockPos(4, 65, 8), new BlockPos(4, 65, 8),
                new BlockPos(12, 64, 8), new BlockPos(8, 64, 2), new BlockPos(8, 63, 2));
        Object duplicate = tacticalFormation(
                java.util.UUID.randomUUID(),
                new BlockPos(5, 64, 0), new BlockPos(5, 65, 8), new BlockPos(5, 65, 8),
                new BlockPos(12, 64, 8), new BlockPos(8, 64, 2), new BlockPos(8, 63, 2));
        Object distinct = tacticalFormation(
                java.util.UUID.randomUUID(),
                new BlockPos(4, 64, 0), new BlockPos(-4, 65, 8), new BlockPos(-4, 65, 8),
                new BlockPos(-12, 64, 8), new BlockPos(-8, 64, 2), new BlockPos(-8, 63, 2));

        @SuppressWarnings("unchecked")
        Map<java.util.UUID, Object> formations = (Map<java.util.UUID, Object>) getField(state, "formations");
        formations.put((java.util.UUID) getField(existing, "id"), existing);

        Method method = LivingWallCoordinator.class.getDeclaredMethod("dominatingRouteFormation",
                state.getClass(), existing.getClass());
        method.setAccessible(true);

        assertEquals(existing, method.invoke(null, state, duplicate));
        assertEquals(null, method.invoke(null, state, distinct));
    }

    @Test
    void directUpperLayerStagesAtBaseBeforeFinalSlot() throws Exception {
        Object formation = bareFormation();
        setField(formation, "pivot", new BlockPos(10, 64, 10));
        setField(formation, "anchor", new BlockPos(18, 64, 10));
        setField(formation, "spanEnd", new BlockPos(18, 64, 10));
        setField(formation, "columnCount", 2);
        setField(formation, "horizontalTouchStep", 1.0D);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE,
                new BlockPos(10, 64, 10), new BlockPos(18, 64, 10), new BlockPos(18, 64, 10),
                new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10), new BlockPos(12, 64, 10),
                        new BlockPos(13, 64, 10), new BlockPos(14, 64, 10)),
                java.util.Set.of(new BlockPos(10, 64, 10)), java.util.List.of()));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("assemblyTargetPosition",
                formation.getClass(), int.class, Vec3.class, Vec3.class);
        method.setAccessible(true);
        Vec3 current = new Vec3(14.5D, 64.0D, 10.5D);
        Vec3 finalSlot = new Vec3(18.5D, 64.0D, 10.5D);
        Vec3 staged = (Vec3) method.invoke(null, formation, 2, current, finalSlot);

        assertEquals(new Vec3(10.5D, 64.0D, 10.0D), staged);
        assertFalse(finalSlot.equals(staged));
    }

    @Test
    void footprintClaimConflictLeavesExistingOwnershipUntouched() throws Exception {
        Object state = barePerLevelState();
        setField(state, "footprintOwners", new HashMap<Long, java.util.UUID>());
        java.util.UUID owner = java.util.UUID.randomUUID();
        java.util.UUID contender = java.util.UUID.randomUUID();
        long occupied = new BlockPos(10, 64, 10).asLong();
        ((HashMap<Long, java.util.UUID>) getField(state, "footprintOwners")).put(occupied, owner);

        Object formation = bareFormation();
        setField(formation, "id", contender);
        setField(formation, "plan", new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE,
                new BlockPos(10, 64, 10), new BlockPos(14, 64, 10), new BlockPos(18, 64, 10),
                new Vec3(1.0D, 0.0D, 0.0D),
                java.util.List.of(new BlockPos(10, 64, 10)),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)),
                java.util.Set.of(new BlockPos(10, 64, 10)),
                java.util.List.of()));
        setField(formation, "footprint", new HashSet<Long>(java.util.List.of(occupied, new BlockPos(11, 64, 10).asLong())));

        Method method = LivingWallCoordinator.class.getDeclaredMethod("claimFootprint",
                state.getClass(), formation.getClass());
        method.setAccessible(true);
        boolean claimed = (Boolean) method.invoke(null, state, formation);

        assertFalse(claimed);
        assertEquals(owner, ((HashMap<Long, java.util.UUID>) getField(state, "footprintOwners")).get(occupied));
    }

    @Test
    void positiveVolumeOverlapIsRejectedButBoundaryContactRemainsValid() {
        AABB first = new AABB(0.0D, 0.0D, 0.0D, 0.6D, 1.95D, 0.6D);
        AABB touching = new AABB(0.6D, 0.0D, 0.0D, 1.2D, 1.95D, 0.6D);
        AABB overlapping = new AABB(0.55D, 0.0D, 0.0D, 1.15D, 1.95D, 0.6D);

        assertFalse(LivingWallCoordinator.boxesPenetrate(first, touching));
        assertTrue(LivingWallCoordinator.boxesPenetrate(first, overlapping));
    }

    @Test
    void diagonalFootprintsTouchWithoutTreatingCornerOverlapAsPenetration() {
        AABB first = new AABB(-0.3D, 0.0D, -0.3D, 0.3D, 1.95D, 0.3D);
        AABB diagonalTouch = new AABB(0.2723D, 0.0D, -0.0615D, 0.8723D, 1.95D, 0.5385D);
        AABB deepOverlap = new AABB(0.1D, 0.0D, -0.3D, 0.7D, 1.95D, 0.3D);

        assertTrue(LivingWallCoordinator.boxesPenetrate(first, diagonalTouch));
        assertTrue(LivingWallCoordinator.structuralBodiesTouch(first, diagonalTouch));
        assertFalse(LivingWallCoordinator.structuralBodiesTouch(first, deepOverlap));
        assertTrue(LivingWallCoordinator.controlledTiltBodiesConnected(first, deepOverlap, 0.0675D));
    }

    @Test
    void tiltWaveMovesFromFoundationTowardFreeEnd() {
        double source = 0.25D;
        double target = 0.50D;

        assertEquals(target, LivingWallCoordinator.tiltWaveProgress(0, 2, source, target, 0.0D));
        assertEquals(target, LivingWallCoordinator.tiltWaveProgress(1, 2, source, target, 0.0D));
        assertEquals(0.375D, LivingWallCoordinator.tiltWaveProgress(2, 2, source, target, 0.5D));
        assertEquals(source, LivingWallCoordinator.tiltWaveProgress(3, 2, source, target, 1.0D));
    }

    @Test
    void wallLedgeStagingFollowsTheCurrentDock() {
        Vec3 pivotBase = new Vec3(13.5D, -58.0D, 14.5D);
        Vec3 thirdDock = new Vec3(12.355D, -58.0D, 14.023D);

        assertEquals(thirdDock, LivingWallCoordinator.structuralStagingCenter(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, pivotBase, thirdDock));
        assertEquals(pivotBase, LivingWallCoordinator.structuralStagingCenter(
                LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, pivotBase, thirdDock));
    }

    @Test
    void assemblyPipelinesSeveralWorkersAheadOfTheLockedChain() {
        assertEquals(1, LivingWallCoordinator.nextLayerLimitForProgress(true, 0, 1, 22, 4));
        assertEquals(5, LivingWallCoordinator.nextLayerLimitForProgress(false, 1, 1, 22, 4));
        assertEquals(22, LivingWallCoordinator.nextLayerLimitForProgress(false, 20, 1, 22, 4));
    }

    @Test
    void assemblyDelayAppliesAfterPlacementWithoutThrottlingEveryMovementStep() {
        assertTrue(LivingWallCoordinator.assemblyPlacementSettleElapsed(-1L, 100L, 5));
        assertFalse(LivingWallCoordinator.assemblyPlacementSettleElapsed(100L, 104L, 5));
        assertTrue(LivingWallCoordinator.assemblyPlacementSettleElapsed(100L, 105L, 5));
        assertTrue(LivingWallCoordinator.assemblyPlacementSettleElapsed(100L, 106L, 5));
    }

    @Test
    void structuralMovementOnlyTreatsAlreadyPlacedMembersAsBlockers() {
        UUID placed = UUID.randomUUID();
        UUID queued = UUID.randomUUID();

        assertTrue(LivingWallCoordinator.isPlacedStructuralMember(
                java.util.List.of(placed, queued), 1, placed));
        assertFalse(LivingWallCoordinator.isPlacedStructuralMember(
                java.util.List.of(placed, queued), 1, queued));
        assertTrue(LivingWallCoordinator.isPlacedStructuralMember(
                java.util.List.of(placed, queued), 2, queued));
    }

    @Test
    void committedRosterMayApproachBridgeFromItsStagingArea() {
        assertTrue(LivingWallCoordinator.isWithinBridgeEntryRecruitmentRange(true, 400.0D, 2.5D));
        assertTrue(LivingWallCoordinator.isWithinBridgeEntryRecruitmentRange(false, 6.25D, 2.5D));
        assertFalse(LivingWallCoordinator.isWithinBridgeEntryRecruitmentRange(false, 6.26D, 2.5D));
    }

    @Test
    void directSpanStrideGuaranteesBodyContactAcrossStations() {
        BlockPos pivot = new BlockPos(0, 64, 0);
        BlockPos spanEnd = new BlockPos(10, 64, 0);
        int directStations = LivingWallCoordinator.requiredStationsForRoute(
                pivot, spanEnd, LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE, 1.0D, 1.0D, null);
        double directStride = 10.0D / (directStations - 1);
        assertTrue(directStride <= 0.58D, "direct span stride must not exceed 0.58m to ensure 0.60m bodies touch");

        int tiltStations = LivingWallCoordinator.requiredStationsForRoute(
                pivot, spanEnd, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, 1.0D, 1.0D, null);
        assertTrue(directStations > tiltStations, "direct span requires denser stations to avoid gaps between bodies");
    }

    @Test
    void directSpanConsecutiveSlotsTouchAndOverlap() throws Exception {
        BlockPos pivot = new BlockPos(10, 64, 10);
        BlockPos spanEnd = new BlockPos(14, 60, 10);
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE.name(),
                pivot, pivot, spanEnd, spanEnd, spanEnd,
                new BlockPos(12, 61, 10), java.util.List.of(), java.util.List.of(), java.util.List.of())
                .withCenterline(java.util.List.of(
                        pivot,
                        new BlockPos(11, 64, 10),
                        new BlockPos(12, 63, 10),
                        new BlockPos(13, 62, 10),
                        new BlockPos(14, 61, 10),
                        spanEnd));
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE,
                pivot, spanEnd, spanEnd,
                new Vec3(1.0D, 0.0D, 0.0D), java.util.List.of(pivot),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(9, 64, 10))),
                java.util.List.of(new BlockPos(11, 64, 10)), java.util.Set.of(pivot),
                java.util.List.of(new BlockPos(12, 64, 10)), corridor);

        int layers = LivingWallCoordinator.requiredStationsForRoute(plan, 0.62D, 1.95D);
        assertTrue(layers >= 10, "route polyline should produce sufficient stations");

        Object formation = bareFormation();
        setField(formation, "pivot", pivot);
        setField(formation, "anchor", spanEnd);
        setField(formation, "spanEnd", spanEnd);
        setField(formation, "columnCount", 1);
        setField(formation, "layerCount", layers);
        setField(formation, "horizontalTouchStep", 0.62D);
        setField(formation, "pillarStep", 1.95D);
        setField(formation, "directSpan", true);
        setField(formation, "plan", plan);

        Method slotMethod = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        slotMethod.setAccessible(true);

        for (int i = 0; i < layers - 1; i++) {
            Vec3 first = (Vec3) slotMethod.invoke(null, formation, i, 1.0D);
            Vec3 second = (Vec3) slotMethod.invoke(null, formation, i + 1, 1.0D);
            double step = first.distanceTo(second);
            assertTrue(step <= 0.60D, "consecutive slots " + i + " and " + (i + 1) + " must not exceed body width 0.60m, actual: " + step);
            AABB firstBox = new AABB(first.x - 0.3D, first.y, first.z - 0.3D,
                    first.x + 0.3D, first.y + 1.95D, first.z + 0.3D);
            AABB secondBox = new AABB(second.x - 0.3D, second.y, second.z - 0.3D,
                    second.x + 0.3D, second.y + 1.95D, second.z + 0.3D);
            assertTrue(LivingWallCoordinator.controlledTiltBodiesConnected(firstBox, secondBox, 0.0675D),
                    "bodies at slot " + i + " and " + (i + 1) + " must touch and connect");
        }
    }

    @Test
    void structuralRouteMultiWaypointSequencing() throws Exception {
        Class<?> routeClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$StructuralRoute");
        var ctor = routeClass.getDeclaredConstructor(java.util.List.class);
        ctor.setAccessible(true);
        Vec3 w0 = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 w1 = new Vec3(4.0D, 5.0D, 6.0D);
        Vec3 w2 = new Vec3(7.0D, 8.0D, 9.0D);
        Object route = ctor.newInstance(java.util.List.of(w0, w1, w2));

        Method waypointMethod = routeClass.getDeclaredMethod("waypoint");
        waypointMethod.setAccessible(true);
        Method isFinalMethod = routeClass.getDeclaredMethod("isFinalWaypoint");
        isFinalMethod.setAccessible(true);
        Method advanceMethod = routeClass.getDeclaredMethod("advance");
        advanceMethod.setAccessible(true);

        assertEquals(w0, waypointMethod.invoke(route));
        assertFalse((Boolean) isFinalMethod.invoke(route));

        assertTrue((Boolean) advanceMethod.invoke(route));
        assertEquals(w1, waypointMethod.invoke(route));
        assertFalse((Boolean) isFinalMethod.invoke(route));

        assertTrue((Boolean) advanceMethod.invoke(route));
        assertEquals(w2, waypointMethod.invoke(route));
        assertTrue((Boolean) isFinalMethod.invoke(route));

        assertFalse((Boolean) advanceMethod.invoke(route));
        assertEquals(w2, waypointMethod.invoke(route));
        assertTrue((Boolean) isFinalMethod.invoke(route));
    }

    @Test
    void structuralRouteElevatedPositionSkipsBaseApproach() throws Exception {
        Class<?> routeClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$StructuralRoute");
        var ctor = routeClass.getDeclaredConstructor(Vec3.class, Vec3.class, Vec3.class, Vec3.class);
        ctor.setAccessible(true);
        Vec3 base = new Vec3(-67.71D, 97.0D, 183.5D);
        Vec3 lift = new Vec3(-67.71D, 102.7D, 183.5D);
        Vec3 dock = new Vec3(-67.71D, 102.7D, 183.5D);

        Vec3 groundPos = new Vec3(-67.71D, 97.2D, 183.5D);
        Object groundRoute = ctor.newInstance(base, lift, dock, groundPos);

        Vec3 elevatedPos = new Vec3(-67.71D, 101.67D, 183.5D);
        Object elevatedRoute = ctor.newInstance(base, lift, dock, elevatedPos);

        Method waypointMethod = routeClass.getDeclaredMethod("waypoint");
        waypointMethod.setAccessible(true);

        assertEquals(base, waypointMethod.invoke(groundRoute));
        assertEquals(lift, waypointMethod.invoke(elevatedRoute));
    }

    @Test
    void tiltBridgePillarLayerCountMatchesGapWithoutOvershoot() {
        double horizontalDistance = Math.sqrt(2.0D * 2.0D + 1.0D * 1.0D);
        double verticalDistance = 2.0D;
        double horizontalTouchStep = 0.62D;
        double verticalTouchStep = 1.95D;

        int layers = LivingWallCoordinator.requiredLayerCountForTilt(
                horizontalDistance, verticalDistance, horizontalTouchStep, verticalTouchStep);
        assertEquals(5, layers, "2.24m horizontal gap must require 5 layers to ensure horizontal stride <= 0.58m and body contact");
        assertTrue(horizontalDistance / (layers - 1) <= 0.58D, "stride along horizontal span must not exceed 0.58m");

        BlockPos pivot = new BlockPos(-66, 97, 184);
        BlockPos anchor = new BlockPos(-64, 99, 183);
        int stations = LivingWallCoordinator.requiredStationsForRoute(
                pivot, anchor, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                horizontalTouchStep, verticalTouchStep, null);
        assertEquals(3, stations, "requiredStationsForRoute for WALL_LEDGE must use verticalTouchStep");
    }

    private static Object bareFormation() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
        Class<?> formationType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        return allocateInstance.invoke(unsafe, formationType);
    }

    private static Object barePerLevelState() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
        Class<?> stateType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$PerLevelState");
        return allocateInstance.invoke(unsafe, stateType);
    }

    private static Object tacticalFormation(java.util.UUID plannerId, BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                                            BlockPos threat, BlockPos wallFace, BlockPos support) throws Exception {
        Object formation = bareFormation();
        setField(formation, "id", java.util.UUID.randomUUID());
        setField(formation, "plannerId", plannerId);
        setField(formation, "pivot", pivot);
        setField(formation, "anchor", anchor);
        setField(formation, "spanEnd", spanEnd);
        setField(formation, "collapsed", false);
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                pivot, anchor, spanEnd, new Vec3(0.0D, 0.0D, 1.0D),
                java.util.List.of(pivot),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(pivot.west())),
                java.util.List.of(pivot.north()),
                java.util.Set.of(pivot), java.util.List.of());
        setField(formation, "plan", plan);
        Class<?> requestType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Class<?> evidenceType = Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence");
        var evidenceCtor = evidenceType.getDeclaredConstructor(BlockPos.class, java.util.List.class, java.util.List.class,
                java.util.List.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                String.class, double.class, String.class);
        evidenceCtor.setAccessible(true);
        Object evidence = evidenceCtor.newInstance(anchor, java.util.List.of(1L), java.util.List.of(), java.util.List.of(),
                wallFace, support, pivot, threat, "WALL_LEDGE", 1.0D, null);
        var requestCtor = requestType.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class,
                BlockPos.class, Class.forName("com.example.soundattract.ai.LivingWallCoordinator$TargetScanSnapshot"),
                evidenceType, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        Object request = requestCtor.newInstance(Vec3.atBottomCenterOf(pivot), pivot, threat, anchor, threat,
                null, evidence, 200L, 200L, 1L);
        setField(formation, "request", request);
        return formation;
    }

    private static void setField(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object getField(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    @Test
    void perpendicularRavineCandidateRejectedByThreatProgress() {
        BlockPos pivot = new BlockPos(-62, 90, 185);
        BlockPos threat = new BlockPos(-61, 92, 192);
        BlockPos perpendicularRavineAnchor = new BlockPos(-70, 95, 185);
        assertFalse(LivingWallCoordinator.hasThreatProgress(pivot, perpendicularRavineAnchor, threat),
                "bridge extending along -X perpendicular to threat at +Z must be rejected");

        BlockPos forwardAnchor = new BlockPos(-61, 95, 189);
        assertTrue(LivingWallCoordinator.hasThreatProgress(pivot, forwardAnchor, threat),
                "bridge advancing towards threat must be accepted");
    }

    @Test
    void wideChasmTiltLayerCountEnsuresBodyContactWithoutGaps() {
        double horizontalDistance = 8.0D;
        double verticalDistance = 5.0D;
        double horizontalTouchStep = 0.58D;
        double verticalTouchStep = 1.95D;

        int layers = LivingWallCoordinator.requiredLayerCountForTilt(
                horizontalDistance, verticalDistance, horizontalTouchStep, verticalTouchStep);
        assertTrue(layers >= 15, "8m horizontal chasm must have at least 15 layers to guarantee body contact");
        double stride = horizontalDistance / (layers - 1);
        assertTrue(stride <= 0.58D, "stride along horizontal span must not exceed 0.58m, actual: " + stride);
    }

    @Test
    void plannerScoreRejectsCandidatesMovingAwayFromThreat() {
        LivingWallBridgeAdapterPlanner.AdapterSpec spec = LivingWallBridgeAdapterPlanner.adapterFor(
                "JUMP_BRIDGE", LivingWallBridgeAdapterPlanner.AdapterKind.JUMP).orElseThrow();
        LivingWallBridgeAdapterPlanner.Candidate retreatingCandidate = new LivingWallBridgeAdapterPlanner.Candidate(
                "retreating", LivingWallBridgeAdapterPlanner.AdapterKind.JUMP, 10, 5.0D, 0,
                0, 0, 0, 0, 0, 10, 1, 0, 0, 0,
                10, 15, true, false, false, 4,
                0L, 0L, 0L);
        assertTrue(LivingWallBridgeAdapterPlanner.score(retreatingCandidate, spec, 30).isEmpty(),
                "candidate with endpointThreatDistance > pivotThreatDistance must be rejected");

        LivingWallBridgeAdapterPlanner.Candidate advancingCandidate = new LivingWallBridgeAdapterPlanner.Candidate(
                "advancing", LivingWallBridgeAdapterPlanner.AdapterKind.JUMP, 10, 5.0D, 0,
                0, 0, 0, 0, 0, 10, 1, 0, 0, 0,
                15, 8, true, false, false, 4,
                0L, 0L, 0L);
        assertTrue(LivingWallBridgeAdapterPlanner.score(advancingCandidate, spec, 30).isPresent(),
                "candidate advancing towards threat must be accepted");
    }

    @Test
    void wallLedgeTiltBridgeAppliesUpwardArcLiftOverFloor() throws Exception {
        BlockPos pivot = new BlockPos(0, 64, 0);
        BlockPos anchor = new BlockPos(10, 70, 0);
        Object formation = bareFormation();
        setField(formation, "pivot", pivot);
        setField(formation, "anchor", anchor);
        setField(formation, "spanEnd", anchor);
        setField(formation, "layerCount", 11);
        setField(formation, "columnCount", 1);
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                pivot, anchor, anchor, new Vec3(1.0D, 0.0D, 0.0D),
                java.util.List.of(pivot),
                java.util.List.of(new LivingWallCoordinator.IntakeWindow(pivot.west())),
                java.util.List.of(pivot.north()),
                java.util.Set.of(pivot), java.util.List.of());
        setField(formation, "plan", plan);

        Class<?> formationClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        Method method = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition", formationClass, int.class, double.class);
        method.setAccessible(true);

        Vec3 basePos = (Vec3) method.invoke(null, formation, 0, 1.0D);
        Vec3 midPos = (Vec3) method.invoke(null, formation, 5, 1.0D);
        Vec3 endPos = (Vec3) method.invoke(null, formation, 10, 1.0D);

        assertEquals(64.0D, basePos.y, 0.01D);
        assertEquals(70.0D, endPos.y, 0.01D);

        double linearMidY = 64.0D + 0.5D * (70.0D - 64.0D);
        double expectedArc = Math.sin(0.5D * Math.PI) * (0.3D * 6.0D);
        assertEquals(linearMidY + expectedArc, midPos.y, 0.01D);
        assertTrue(midPos.y > linearMidY);
    }
}
