package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class LivingWallConstructionPlanTest {
    @Test
    void rankedAssemblySearchContinuesPastConflictedDirectLane() {
        List<String> visited = new java.util.ArrayList<>();
        String selected = LivingWallCoordinator.firstRankedAssemblyMatch(List.of("claimed", "free"), candidate -> {
            visited.add(candidate);
            return candidate.equals("free") ? candidate : null;
        });

        assertEquals("free", selected);
        assertEquals(List.of("claimed", "free"), visited);
    }

    @Test
    void rankedAssemblySearchReturnsNoCandidateWhenEveryLaneConflicts() {
        List<String> visited = new java.util.ArrayList<>();
        String selected = LivingWallCoordinator.firstRankedAssemblyMatch(List.of("claimed-a", "claimed-b"), candidate -> {
            visited.add(candidate);
            return null;
        });

        assertNull(selected);
        assertEquals(List.of("claimed-a", "claimed-b"), visited);
    }

    @Test
    void rankedAssemblySearchPreservesDeterministicCandidateOrderAndStopsAtFirstMatch() {
        List<String> visited = new java.util.ArrayList<>();
        String selected = LivingWallCoordinator.firstRankedAssemblyMatch(List.of("first", "second", "third"), candidate -> {
            visited.add(candidate);
            return candidate.equals("second") ? candidate : null;
        });

        assertEquals("second", selected);
        assertEquals(List.of("first", "second"), visited);
    }

    @Test
    void recruitmentBlockerFilterUsesActualSegmentInsteadOfExpandedBoxCorners() {
        Vec3 start = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 destination = new Vec3(10.0D, 64.0D, 0.0D);

        assertTrue(LivingWallCoordinator.isNearRecruitmentSegment(start, destination,
                new Vec3(5.0D, 64.0D, 0.5D), 0.65D));
        assertFalse(LivingWallCoordinator.isNearRecruitmentSegment(start, destination,
                new Vec3(0.2D, 64.0D, 0.66D), 0.65D));
        assertFalse(LivingWallCoordinator.isNearRecruitmentSegment(start, destination,
                new Vec3(5.0D, 65.0D, 0.0D), 0.65D));
    }

    @Test
    void intakeWindowCandidatesFillTheOppositeApproachSemicircle() {
        BlockPos pivot = new BlockPos(10, 64, 10);
        List<BlockPos> candidates = LivingWallCoordinator.intakeWindowCandidates(pivot, new Vec3(1.0D, 0.0D, 0.0D));
        assertTrue(candidates.size() > 6);
        assertTrue(candidates.stream().allMatch(cell -> cell.getX() <= pivot.getX()));
        assertTrue(candidates.stream().allMatch(cell -> {
            int dx = cell.getX() - pivot.getX();
            int dz = cell.getZ() - pivot.getZ();
            int distanceSqr = dx * dx + dz * dz;
            return distanceSqr >= 4 && distanceSqr <= 25;
        }));
        assertTrue(candidates.get(0).getX() < pivot.getX());
    }

    @Test
    void intakeWindowCandidatesAreDeterministicAndDistinct() {
        BlockPos pivot = new BlockPos(0, 70, 0);
        List<BlockPos> first = LivingWallCoordinator.intakeWindowCandidates(pivot, new Vec3(0.0D, 0.0D, -1.0D));
        List<BlockPos> second = LivingWallCoordinator.intakeWindowCandidates(pivot, new Vec3(0.0D, 0.0D, -1.0D));
        assertEquals(first, second);
        assertEquals(first.size(), first.stream().distinct().count());
        assertTrue(first.stream().allMatch(cell -> cell.getZ() >= pivot.getZ()));
    }

    @Test
    void assemblySlotsAndIntakeWindowsRemainSeparateDeterministicGeometry() {
        BlockPos pivot = new BlockPos(10, 64, 10);
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        List<BlockPos> windows = LivingWallCoordinator.intakeWindowCandidates(pivot, forward);
        List<BlockPos> assembly = LivingWallCoordinator.assemblySlotCandidates(pivot, forward);
        assertFalse(assembly.isEmpty());
        assertFalse(windows.isEmpty());
        assertEquals(assembly, LivingWallCoordinator.assemblySlotCandidates(pivot, forward));
        assertEquals(windows, LivingWallCoordinator.intakeWindowCandidates(pivot, forward));
        assertTrue(assembly.stream().noneMatch(pivot::equals));
    }

    @Test
    void staticSiteContractNeedsStructuralGeometryAndAssemblySlotWithoutAFeeder() {
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(40, 64, 40), new BlockPos(48, 64, 40), new BlockPos(48, 64, 40),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(40, 64, 40)),
                List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(38, 64, 40))),
                List.of(new BlockPos(36, 64, 40)),
                Set.of(new BlockPos(40, 64, 40)), List.of());
        assertTrue(LivingWallCoordinator.hasRequiredStaticSiteGeometry(plan));
        assertFalse(LivingWallCoordinator.hasRequiredStaticSiteGeometry(new LivingWallCoordinator.SitePlan(
                plan.scenario(), plan.pivot(), plan.anchor(), plan.spanEnd(), plan.forward(), plan.baseCells(),
                List.of(), plan.assemblySlots(), plan.validationCells(), plan.hazardCells())));
        assertFalse(LivingWallCoordinator.hasRequiredStaticSiteGeometry(new LivingWallCoordinator.SitePlan(
                plan.scenario(), plan.pivot(), plan.anchor(), plan.spanEnd(), plan.forward(), plan.baseCells(),
                plan.intakeWindows(), List.of(), plan.validationCells(), plan.hazardCells())));
        assertTrue(LivingWallCoordinator.hasRequiredStaticSiteGeometry(new LivingWallCoordinator.SitePlan(
                plan.scenario(), plan.pivot(), plan.anchor(), plan.spanEnd(), plan.forward(), plan.baseCells(),
                List.of(new LivingWallCoordinator.IntakeWindow(null)), plan.assemblySlots(),
                plan.validationCells(), plan.hazardCells())));
    }

    @Test
    void staticSiteContractRequiresAtLeastOneImmutableIntakeWindow() {
        LivingWallCoordinator.SitePlan invalid = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(40, 64, 40), new BlockPos(48, 64, 40), new BlockPos(48, 64, 40),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(40, 64, 40)),
                List.of(), List.of(new BlockPos(36, 64, 40)),
                Set.of(new BlockPos(40, 64, 40)), List.of());
        assertFalse(LivingWallCoordinator.hasRequiredStaticSiteGeometry(invalid));
    }

    @Test
    void sitePlanAdmitsAssemblySlotsWithoutLegacyRallyGeometry() {
        BlockPos assemblySlot = new BlockPos(12, 64, 10);
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(10, 64, 10), new BlockPos(18, 64, 10), new BlockPos(18, 64, 10),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(10, 64, 10)),
                List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(8, 64, 10))),
                List.of(assemblySlot), Set.of(assemblySlot),
                List.of(new BlockPos(14, 64, 10)));
        assertEquals(List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(8, 64, 10))), plan.intakeWindows());
        assertEquals(List.of(new BlockPos(12, 64, 10)), plan.assemblySlots());
        assertFalse(LivingWallCoordinator.permanentFootprintKeys(plan).contains(assemblySlot.asLong()));
        assertTrue(LivingWallCoordinator.permanentFootprintKeys(plan).contains(new BlockPos(8, 64, 10).asLong()));
        assertThrows(NoSuchMethodException.class, () -> LivingWallCoordinator.SitePlan.class.getDeclaredMethod("rallyCells"));
    }

    @Test
    void transientAssemblyAnchorCanBeUsedForSuctionWithoutAStaticFeeder() {
        BlockPos slot = new BlockPos(12, 64, 10);
        LivingWallCoordinator.IntakeWindow window = new LivingWallCoordinator.IntakeWindow(slot, new BlockPos(10, 64, 10), false);
        assertFalse(window.transientAnchor());
        assertTrue(LivingWallCoordinator.isCaptureReadySuctionPose(
                Vec3.atBottomCenterOf(slot), window, List.of(new BlockPos(8, 64, 10)), slot));
        assertFalse(LivingWallCoordinator.isCaptureReadySuctionPose(
                Vec3.atBottomCenterOf(slot), window, List.of(slot), null));
    }

    @Test
    void alternateIntakeWindowIsSelectedBeforeRouteFailure() {
        LivingWallCoordinator.IntakeWindow blocked = new LivingWallCoordinator.IntakeWindow(new BlockPos(-2, 64, 0));
        LivingWallCoordinator.IntakeWindow reachable = new LivingWallCoordinator.IntakeWindow(new BlockPos(-3, 64, 1));
        LivingWallCoordinator.IntakeWindow selected = LivingWallCoordinator.selectIntakeWindow(
                List.of(blocked, reachable), reachable::equals);
        assertEquals(reachable, selected);
    }

    @Test
    void intakeRouteUsesThePlannedApproachInsteadOfReconstructingTheWindow() {
        BlockPos feeder = new BlockPos(0, 64, 0);
        BlockPos approach = new BlockPos(-2, 64, 0);
        LivingWallCoordinator.IntakeWindow window =
                new LivingWallCoordinator.IntakeWindow(feeder, approach, false);

        assertTrue(LivingWallCoordinator.isIntakeRouteTarget(window, approach));
        assertFalse(LivingWallCoordinator.isIntakeRouteTarget(window, feeder));
    }

    @Test
    void admissionIntakeOriginPrefersCanonicalTakeoffFeet() {
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                new BlockPos(8, 64, 8),
                new BlockPos(3, 64, 8),
                new BlockPos(10, 66, 8),
                new BlockPos(10, 66, 8),
                new BlockPos(11, 66, 8),
                new BlockPos(9, 65, 8),
                List.of(),
                List.of(),
                List.of());

        assertEquals(new BlockPos(3, 64, 8),
                LivingWallCoordinator.admissionIntakeOriginForTest(new BlockPos(10, 66, 8), corridor));
        assertEquals(new BlockPos(10, 66, 8),
                LivingWallCoordinator.admissionIntakeOriginForTest(new BlockPos(10, 66, 8), null));
    }

    @Test
    void admissionIntakeCandidatesStayBoundedAndOnTheWorkerFacingSourceSide() {
        BlockPos takeoff = new BlockPos(10, 64, 10);
        List<BlockPos> candidates = LivingWallCoordinator.admissionIntakeCandidatesForTest(
                takeoff, new Vec3(-1.0D, 0.0D, 0.0D));

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.size() <= 49);
        assertEquals(candidates.size(), candidates.stream().distinct().count());
        assertTrue(candidates.stream().allMatch(cell -> cell.getX() < takeoff.getX()));
        assertTrue(candidates.stream().allMatch(cell -> Math.abs(cell.getZ() - takeoff.getZ()) <= 3));
    }

    @Test
    void workerRouteStartClassificationPrefersDeferReasonsBeforeTerrain() {
        assertEquals("worker_start_unloaded_defer",
                LivingWallCoordinator.classifyWorkerRouteStartForTest(false, true, true, true, 0.0F, false));
        assertEquals("worker_airborne_defer",
                LivingWallCoordinator.classifyWorkerRouteStartForTest(true, true, true, false, 0.0F, false));
        assertEquals("worker_airborne_defer",
                LivingWallCoordinator.classifyWorkerRouteStartForTest(true, true, true, true, 1.0F, false));
        assertEquals("worker_fluid_defer",
                LivingWallCoordinator.classifyWorkerRouteStartForTest(true, true, true, true, 0.0F, true));
        assertNull(LivingWallCoordinator.classifyWorkerRouteStartForTest(true, true, true, true, 0.0F, false));
    }

    @Test
    void workerStartSupportAcceptsGroundedPartialCollisionSupport() {
        AABB body = new AABB(0.2D, 0.5D, 0.2D, 0.8D, 2.3D, 0.8D);
        assertTrue(LivingWallCoordinator.isMobCompatibleWorkerSupportShape(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
                body,
                BlockPos.ZERO));
        assertTrue(LivingWallCoordinator.isMobCompatibleWorkerSupportShape(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 0.5D),
                new AABB(0.2D, 0.5D, 0.1D, 0.8D, 2.3D, 0.45D),
                BlockPos.ZERO));
    }

    @Test
    void workerStartSupportRejectsUnsupportedOrTooLowCollision() {
        AABB body = new AABB(0.2D, 1.0D, 0.2D, 0.8D, 2.8D, 0.8D);
        assertFalse(LivingWallCoordinator.isMobCompatibleWorkerSupportShape(Shapes.empty(), body, BlockPos.ZERO));
        assertFalse(LivingWallCoordinator.isMobCompatibleWorkerSupportShape(
                Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.3D, 1.0D),
                body,
                BlockPos.ZERO));
    }

    @Test
    void admissionIntakeFailureReasonDistinguishesCandidateAndWorkerSpecificMisses() {
        assertEquals("intake_cells_unloaded_defer",
                LivingWallCoordinator.terminalAdmissionIntakeFailureReasonForTest(
                        0, false, false, false, false, false, false, false));
        assertEquals("worker_intake_surface_unsafe",
                LivingWallCoordinator.terminalAdmissionIntakeFailureReasonForTest(
                        4, false, true, false, false, false, false, false));
        assertEquals("worker_intake_no_support",
                LivingWallCoordinator.terminalAdmissionIntakeFailureReasonForTest(
                        4, false, false, true, false, false, false, false));
        assertEquals("worker_intake_body_blocked",
                LivingWallCoordinator.terminalAdmissionIntakeFailureReasonForTest(
                        4, false, false, false, true, false, false, false));
        assertEquals("worker_intake_surface_elevation",
                LivingWallCoordinator.terminalAdmissionIntakeFailureReasonForTest(
                        4, true, false, false, false, false, false, false));
        assertEquals("worker_intake_clearance_blocked",
                LivingWallCoordinator.terminalAdmissionIntakeFailureReasonForTest(
                        4, true, false, false, false, true, false, false));
        assertEquals("worker_intake_wrong_side",
                LivingWallCoordinator.terminalAdmissionIntakeFailureReasonForTest(
                        4, true, false, false, false, true, true, false));
    }

    @Test
    void admissionIntakeFailureScopeMarksWorkerSpecificRejectionsSeparately() {
        assertEquals("CANDIDATE", LivingWallCoordinator.admissionIntakeFailureScopeForTest("intake_cells_unloaded_defer"));
        assertEquals("CANDIDATE", LivingWallCoordinator.admissionIntakeFailureScopeForTest("no_worker_facing_cells"));
        assertEquals("WORKER", LivingWallCoordinator.admissionIntakeFailureScopeForTest("worker_intake_surface_unsafe"));
        assertEquals("WORKER", LivingWallCoordinator.admissionIntakeFailureScopeForTest("worker_intake_no_support"));
    }

    @Test
    void intakeWindowClearanceRejectsStructuralAndAssemblyOverlap() {
        BlockPos feeder = new BlockPos(-2, 64, 0);
        assertFalse(LivingWallCoordinator.isIntakeWindowClear(feeder, Set.of(feeder), List.of()));
        assertTrue(LivingWallCoordinator.isIntakeWindowClear(feeder, Set.of(), List.of(new BlockPos(-1, 64, 0))));
        assertTrue(LivingWallCoordinator.isIntakeWindowClear(feeder, Set.of(), List.of(new BlockPos(2, 64, 0))));
    }

    @Test
    void assemblyPoolReservesFeederClearance() {
        LivingWallCoordinator.IntakeWindow feeder =
                new LivingWallCoordinator.IntakeWindow(new BlockPos(-2, 64, 0));
        assertTrue(LivingWallCoordinator.occupiesIntakeClearance(
                new BlockPos(-1, 64, 0), List.of(feeder)));
        assertFalse(LivingWallCoordinator.occupiesIntakeClearance(
                new BlockPos(0, 64, 0), List.of(feeder)));
    }

    @Test
    void routeFailureStaysMobSpecificAndDoesNotBlacklistSites() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos assembly = new BlockPos(1, 64, 1);
        BlockPos feeder = new BlockPos(2, 64, 1);
        LivingWallCoordinator.RecruitmentRouteKey failed =
                new LivingWallCoordinator.RecruitmentRouteKey(first, assembly, feeder);
        LivingWallCoordinator.RecruitmentRouteKey unaffected =
                new LivingWallCoordinator.RecruitmentRouteKey(second, assembly, feeder);
        Map<LivingWallCoordinator.RecruitmentRouteKey, Boolean> routes = new HashMap<>();
        routes.put(failed, false);
        assertFalse(routes.get(failed));
        assertFalse(routes.containsKey(unaffected));
    }

    @Test
    void routeBudgetFailureReasonOnlyReportsBudgetExhaustionWhenLocalProbeDidNotFinish() {
        assertEquals("probe_budget_exhausted_defer",
                LivingWallCoordinator.localRouteBudgetFailureForTest(3, 0, 0, 1));
        assertEquals("route_incomplete",
                LivingWallCoordinator.localRouteBudgetFailureForTest(1, 1, 0, 2));
        assertNull(LivingWallCoordinator.localRouteBudgetFailureForTest(1, 1, 1, 1));
    }

    @Test
    void corridorBudgetRemainsExactlyNinetySixCells() {
        assertEquals(96, LivingWallCoordinator.MAX_CORRIDOR_INTERVAL_CELLS);
    }

    @Test
    void ticketLifecycleFollowsTheCoordinatorOwnedSequence() {
        var state = LivingWallCoordinator.MobTicketState.class;
        assertTrue(LivingWallCoordinator.canTransitionTicketState(state.cast(state.getEnumConstants()[0]),
                LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY,
                LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY,
                LivingWallCoordinator.MobTicketState.ASSEMBLED));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.ASSEMBLED,
                LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.ASSEMBLED,
                LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER,
                LivingWallCoordinator.MobTicketState.SUCTIONING));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.SUCTIONING,
                LivingWallCoordinator.MobTicketState.BUILDING));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.BUILDING,
                LivingWallCoordinator.MobTicketState.RELEASED));
        assertFalse(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.ASSEMBLED,
                LivingWallCoordinator.MobTicketState.BUILDING));
    }

    @Test
    void assemblyEnvelopeAndExactLockStayDistinct() {
        BlockPos slot = new BlockPos(10, 64, 10);
        Vec3 coarseOnly = new Vec3(12.47D, 64.0D, 10.5D);
        Vec3 exact = Vec3.atBottomCenterOf(slot);
        assertTrue(LivingWallCoordinator.isWithinAssemblyAcceptanceEnvelope(coarseOnly, slot));
        assertFalse(LivingWallCoordinator.exactAssemblyDistanceSqr(coarseOnly, slot) <= 0.35D * 0.35D);
        assertEquals(0.0D, LivingWallCoordinator.exactAssemblyDistanceSqr(exact, slot), 1.0E-6D);
    }

    @Test
    void assemblySlotOwnershipIsExclusiveAndReleasedTransactionally() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos slot = new BlockPos(4, 64, 4);
        Map<UUID, BlockPos> assignments = new HashMap<>();
        Map<BlockPos, UUID> owners = new HashMap<>();
        assertTrue(LivingWallCoordinator.claimAssemblySlotOwner(assignments, owners, first, slot));
        assertFalse(LivingWallCoordinator.claimAssemblySlotOwner(assignments, owners, second, slot));
        assertEquals(slot, LivingWallCoordinator.releaseAssemblySlotOwner(assignments, owners, first));
        assertTrue(assignments.isEmpty());
        assertTrue(owners.isEmpty());
        assertTrue(LivingWallCoordinator.claimAssemblySlotOwner(assignments, owners, second, slot));
    }

    @Test
    void committedFoundationOwnershipSurvivesRecruiterCleanupUntilMemberRelease() {
        UUID foundation = UUID.randomUUID();
        Set<UUID> committed = new HashSet<>();
        committed.add(foundation);
        assertTrue(LivingWallCoordinator.isCommittedFoundationOwner(committed, foundation));
        assertFalse(LivingWallCoordinator.isCommittedFoundationOwner(committed, UUID.randomUUID()));
        committed.remove(foundation);
        assertFalse(LivingWallCoordinator.isCommittedFoundationOwner(committed, foundation));
    }

    @Test
    void committedFoundationAdvancesAssemblyOnlyWithAllStructuralGuards() {
        assertTrue(LivingWallCoordinator.committedFoundationCountsAsAssembled(true, true, true, true));
        assertFalse(LivingWallCoordinator.committedFoundationCountsAsAssembled(true, true, true, false));
        assertFalse(LivingWallCoordinator.committedFoundationCountsAsAssembled(true, false, true, true));
        assertFalse(LivingWallCoordinator.committedFoundationCountsAsAssembled(false, true, true, true));
    }

    @Test
    void committedFoundationUsesRecruiterSlotAsStructuralSlotZero() {
        Map<Integer, Vec3> mapped = new HashMap<>();
        Vec3 acquired = new Vec3(51.5D, 8.0D, 44.5D);
        Vec3 geometric = new Vec3(52.5D, 8.0D, 45.5D);
        mapped.put(0, acquired);
        assertEquals(acquired, LivingWallCoordinator.structuralSlotPositionForTest(mapped, 0, geometric));
        assertEquals(geometric, LivingWallCoordinator.structuralSlotPositionForTest(mapped, 1, geometric));
    }

    @Test
    void suctionWindowIsInclusiveAtOnePointFiveAndNeverAcceptsBelow() {
        BlockPos feeder = new BlockPos(0, 64, 0);
        LivingWallCoordinator.IntakeWindow window = new LivingWallCoordinator.IntakeWindow(feeder);
        assertTrue(LivingWallCoordinator.isValidSuctionWindow(new Vec3(2.0D, 64.0D, 0.5D), feeder));
        assertTrue(LivingWallCoordinator.isValidSuctionWindow(new Vec3(0.5D, 65.5D, 0.5D), feeder));
        assertFalse(LivingWallCoordinator.isValidSuctionWindow(new Vec3(0.5D, 63.999D, 0.5D), feeder));
        assertFalse(LivingWallCoordinator.isValidSuctionWindow(new Vec3(0.5D, 65.5001D, 0.5D), feeder));
        assertTrue(LivingWallCoordinator.isValidSuctionPose(new Vec3(2.0D, 64.0D, 0.5D), window,
                List.of(new BlockPos(8, 64, 0))));
        assertFalse(LivingWallCoordinator.isValidSuctionPose(new Vec3(0.5D, 64.0D, 0.5D), window,
                List.of(feeder)));
    }

    @Test
    void feederArrivalRequiresSuctionEnvelopeAndKeepsApproachSeparate() {
        BlockPos feeder = new BlockPos(0, 64, 0);
        BlockPos approach = new BlockPos(-2, 64, 0);
        LivingWallCoordinator.IntakeWindow window =
                new LivingWallCoordinator.IntakeWindow(feeder, approach, false);
        assertEquals(approach, window.feederApproach());
        assertTrue(LivingWallCoordinator.isAtFeederArrival(new Vec3(2.0D, 64.0D, 0.5D), window));
        assertFalse(LivingWallCoordinator.isAtFeederArrival(new Vec3(-2.0D, 64.0D, 0.5D), window));
        assertFalse(LivingWallCoordinator.isAtFeederArrival(new Vec3(0.5D, 65.501D, 0.5D), window));
    }

    @Test
    void onlyAnAssemblySlotOnTheFeederCellRejectsTheFeederPose() {
        BlockPos feeder = new BlockPos(0, 64, 0);
        BlockPos assignedSlot = new BlockPos(1, 64, 0);
        BlockPos unrelatedSlot = new BlockPos(8, 64, 0);
        LivingWallCoordinator.IntakeWindow window = new LivingWallCoordinator.IntakeWindow(feeder);

        assertTrue(LivingWallCoordinator.isValidSuctionPoseForAssemblySlot(
                new Vec3(0.5D, 64.0D, 0.5D), window, assignedSlot, List.of(assignedSlot)));
        assertTrue(LivingWallCoordinator.isValidSuctionPoseForAssemblySlot(
                new Vec3(0.5D, 64.0D, 0.5D), window, unrelatedSlot, List.of(assignedSlot)));
        assertFalse(LivingWallCoordinator.isValidSuctionPoseForAssemblySlot(
                new Vec3(0.5D, 64.0D, 0.5D), window, unrelatedSlot, List.of(feeder)));
    }

    @Test
    void assemblyAcceptanceUsesAZoneInsteadOfAnExactBlock() {
        BlockPos slot = new BlockPos(10, 64, 10);
        assertTrue(LivingWallCoordinator.isWithinAssemblyAcceptanceEnvelope(
                new Vec3(12.0D, 64.0D, 10.5D), slot));
        assertTrue(LivingWallCoordinator.isWithinAssemblyAcceptanceEnvelope(
                new Vec3(10.5D, 65.5D, 10.5D), slot));
        assertFalse(LivingWallCoordinator.isWithinAssemblyAcceptanceEnvelope(
                new Vec3(12.6D, 64.0D, 10.5D), slot));
        assertFalse(LivingWallCoordinator.isWithinAssemblyAcceptanceEnvelope(
                new Vec3(10.5D, 65.501D, 10.5D), slot));
    }

    @Test
    void assemblyApproachMustRemainInsideTheAcceptanceEnvelope() {
        BlockPos slot = new BlockPos(10, 64, 10);
        assertTrue(LivingWallCoordinator.isValidAssemblyApproach(new BlockPos(12, 64, 10), slot));
        assertFalse(LivingWallCoordinator.isValidAssemblyApproach(new BlockPos(14, 64, 10), slot));
        assertFalse(LivingWallCoordinator.isValidAssemblyApproach(new BlockPos(12, 66, 10), slot));
    }

    @Test
    void assemblyApproachUsesNavigationArrivalToleranceBeforeExactSlotHandoff() {
        BlockPos approach = new BlockPos(23, 63, 15);
        assertTrue(LivingWallCoordinator.hasReachedAssemblyApproach(
                new Vec3(23.95D, 63.0D, 15.62D), approach));
        assertFalse(LivingWallCoordinator.hasReachedAssemblyApproach(
                new Vec3(24.3D, 63.0D, 15.5D), approach));
    }

    @Test
    void onlyFoundationSlotsMayAssembleWithoutIntakeProvenance() {
        BlockPos feeder = new BlockPos(8, 64, 10);
        assertTrue(LivingWallCoordinator.foundationMayAssembleWithoutIntake(0, 2, null));
        assertTrue(LivingWallCoordinator.foundationMayAssembleWithoutIntake(1, 2, null));
        assertFalse(LivingWallCoordinator.foundationMayAssembleWithoutIntake(2, 2, null));
        assertFalse(LivingWallCoordinator.foundationMayAssembleWithoutIntake(0, 2, feeder));
    }

    @Test
    void upperLayersRequireFoundationCommitmentAndContactBeforeAdmission() {
        assertTrue(LivingWallCoordinator.foundationCommittedForLayer(0, 0, 2, false, 0));
        assertFalse(LivingWallCoordinator.foundationCommittedForLayer(1, 2, 2, false, 2));
        assertFalse(LivingWallCoordinator.foundationCommittedForLayer(1, 1, 2, true, 2));
        assertTrue(LivingWallCoordinator.foundationCommittedForLayer(2, 2, 2, true, 2));
    }

    @Test
    void rejectedCandidateProjectionPreservesIdentityAndProvenance() {
        BlockPos source = new BlockPos(1, 64, 2);
        BlockPos edge = new BlockPos(4, 65, 6);
        BlockPos landing = new BlockPos(7, 66, 8);
        BlockPos egress = new BlockPos(8, 66, 8);
        WallProfileScanner.RayEvidence ray = new WallProfileScanner.RayEvidence(17L,
                Vec3.atBottomCenterOf(source), new Vec3(1.0D, 0.0D, 0.0D), edge,
                List.of(edge, new BlockPos(5, 65, 6)), List.of(WallProfileScanner.CollisionKind.SOLID,
                        WallProfileScanner.CollisionKind.LAVA), List.of(edge), false, 0.31D, 0.0D);

        LivingWallCoordinator.RejectedLivingWallCandidate diagnostic =
                LivingWallCoordinator.projectRejectedLivingWallCandidate(22L, 123L, ray, edge, null, landing,
                        List.of(new BlockPos(5, 65, 6), new BlockPos(6, 65, 7)), egress,
                        LivingWallCoordinator.RejectedLivingWallReason.HAZARD_CORRIDOR_INTERVAL_INVALID);

        assertEquals("22/17/" + edge + "/" + landing, diagnostic.candidateId());
        assertEquals(source, diagnostic.source());
        assertEquals(edge, diagnostic.edge());
        assertEquals(edge, diagnostic.firstSolidComponent());
        assertEquals(List.of(new BlockPos(5, 65, 6), new BlockPos(6, 65, 7)), diagnostic.hazardInterval());
        assertEquals(2, diagnostic.hazardIntervalTotal());
        assertFalse(diagnostic.hazardIntervalTruncated());
        assertEquals(List.of(source, edge, landing, egress), diagnostic.gapInterval());
        assertEquals(landing, diagnostic.landing());
        assertEquals(egress, diagnostic.egress());
        assertEquals(LivingWallCoordinator.RejectedLivingWallReason.HAZARD_CORRIDOR_INTERVAL_INVALID,
                diagnostic.reason());
        assertEquals(22L, diagnostic.scanGeneration());
        assertEquals(123L, diagnostic.captureTick());
        assertEquals(123L + 40L, diagnostic.scanExpiresAt());
        assertEquals(-1L, diagnostic.requestRevision());
    }

    @Test
    void rejectedCandidateProjectionUsesNullMarkersAndExactBranchReasons() {
        LivingWallCoordinator.RejectedLivingWallReason[] reasons = {
                LivingWallCoordinator.RejectedLivingWallReason.NO_SAFE_LANDING,
                LivingWallCoordinator.RejectedLivingWallReason.VERTICAL_PROGRESS_INCOMPATIBLE,
                LivingWallCoordinator.RejectedLivingWallReason.NO_BRIDGE_TRIGGER,
                LivingWallCoordinator.RejectedLivingWallReason.HAZARD_CORRIDOR_INTERVAL_INVALID,
                LivingWallCoordinator.RejectedLivingWallReason.SCENARIO_NONE,
                LivingWallCoordinator.RejectedLivingWallReason.HAZARD_SCENARIO_WITHOUT_HAZARD
        };
        assertEquals(6, reasons.length);
        assertEquals("reachable:no_safe_landing",
                LivingWallCoordinator.exactCorridorRejectionProvenanceCategory(reasons[0]));
        assertEquals("N/A", LivingWallCoordinator.exactCorridorRejectionProvenanceCategory(reasons[1]));
        assertEquals("reachable:no_bridge_trigger",
                LivingWallCoordinator.exactCorridorRejectionProvenanceCategory(reasons[2]));
        assertEquals("reachable:hazard_corridor_interval_invalid",
                LivingWallCoordinator.exactCorridorRejectionProvenanceCategory(reasons[3]));
        assertEquals("reachable:scenario_none",
                LivingWallCoordinator.exactCorridorRejectionProvenanceCategory(reasons[4]));
        assertEquals("reachable:hazard_scenario_without_hazard",
                LivingWallCoordinator.exactCorridorRejectionProvenanceCategory(reasons[5]));
        assertEquals("N/A", LivingWallCoordinator.exactCorridorRejectionProvenanceCategory(null));

        LivingWallCoordinator.RejectedLivingWallCandidate diagnostic =
                LivingWallCoordinator.projectRejectedLivingWallCandidate(1L, 2L, null, null, null, null, List.of(),
                        null, LivingWallCoordinator.RejectedLivingWallReason.NO_SAFE_LANDING);
        assertNull(diagnostic.source());
        assertNull(diagnostic.edge());
        assertNull(diagnostic.firstSolidComponent());
        assertEquals(List.of(), diagnostic.hazardInterval());
        assertEquals(0, diagnostic.hazardIntervalTotal());
        assertFalse(diagnostic.hazardIntervalTruncated());
        assertTrue(diagnostic.gapInterval().isEmpty());
        assertNull(diagnostic.landing());
        assertNull(diagnostic.egress());
    }

    @Test
    void rejectedCandidateProjectionCapsHazardProvenanceAtEight() {
        List<BlockPos> hazards = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> new BlockPos(i, 64, 0))
                .toList();
        LivingWallCoordinator.RejectedLivingWallCandidate diagnostic =
                LivingWallCoordinator.projectRejectedLivingWallCandidate(9L, 4L, null, new BlockPos(1, 64, 1),
                        null, new BlockPos(2, 64, 2), hazards, new BlockPos(3, 64, 3),
                        LivingWallCoordinator.RejectedLivingWallReason.SCENARIO_NONE);
        assertEquals(12, diagnostic.hazardIntervalTotal());
        assertTrue(diagnostic.hazardIntervalTruncated());
        assertEquals(8, diagnostic.hazardInterval().size());
        assertEquals(List.of(new BlockPos(2, 64, 2), new BlockPos(3, 64, 3)), diagnostic.gapInterval());
    }

    @Test
    void groundingRequiresTwoConsecutiveStableServerTicks() {
        assertEquals(0, LivingWallCoordinator.groundedTicksAfterObservation(
                Long.MIN_VALUE, 0, 10L, false, 0.0D));
        assertEquals(2, LivingWallCoordinator.groundedTicksAfterObservation(
                10L, 1, 11L, true, 0.0D));
        assertEquals(2, LivingWallCoordinator.groundedTicksAfterObservation(
                11L, 1, 12L, true, 0.0D));
        assertEquals(0, LivingWallCoordinator.groundedTicksAfterObservation(
                12L, 2, 13L, true, -0.09D));
        assertEquals(1, LivingWallCoordinator.groundedTicksAfterObservation(
                12L, 2, 14L, true, 0.0D));
    }

    @Test
    void isOnApproachSideAcceptsPivotAtCliffEdgeWithHazardsAhead() {
        BlockPos seed = new BlockPos(8, -47, -4);
        Vec3 forward = new Vec3(0.0D, 0.0D, -1.0D);
        Set<BlockPos> hazards = Set.of(
                new BlockPos(8, -48, -5),
                new BlockPos(7, -49, -8),
                new BlockPos(8, -60, -8));
        assertTrue(LivingWallCoordinator.isOnApproachSide(seed, seed, forward, hazards));
    }

    @Test
    void isOnApproachSideIgnoresDeepDropHazardsBelowStandingPlane() {
        BlockPos seed = new BlockPos(8, -47, -4);
        BlockPos candidate = new BlockPos(8, -47, -3);
        Vec3 forward = new Vec3(0.0D, 0.0D, -1.0D);
        Set<BlockPos> hazards = Set.of(new BlockPos(8, -60, -4));
        assertTrue(LivingWallCoordinator.isOnApproachSide(seed, candidate, forward, hazards));
    }

    @Test
    void validateSourceApproachRouteAcceptsNodesReachingIntakeOrigin() {
        BlockPos intakeOrigin = new BlockPos(8, -47, -4);
        Vec3 towardWorker = new Vec3(0.0D, 0.0D, 4.0D);
        List<BlockPos> validRoute = List.of(
                new BlockPos(8, -47, -2),
                new BlockPos(8, -47, -3),
                new BlockPos(8, -47, -4));
        assertNull(LivingWallCoordinator.validateSourceApproachRouteForTest(intakeOrigin, towardWorker, null, validRoute));

        List<BlockPos> invalidRoute = List.of(
                new BlockPos(8, -47, -3),
                new BlockPos(8, -47, -4),
                new BlockPos(8, -47, -5));
        assertEquals("source_approach_wrong_side",
                LivingWallCoordinator.validateSourceApproachRouteForTest(intakeOrigin, towardWorker, null, invalidRoute));
    }
}
