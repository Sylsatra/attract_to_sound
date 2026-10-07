package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class LivingWallBridgeAdapterPlannerTest {
    @Test
    void configuredBodyLimitAllowsLargerFormationsAndRejectsOverCapacity() {
        var kind = LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT;
        var spec = LivingWallBridgeAdapterPlanner.adapterFor("WALL_LEDGE", kind).orElseThrow();
        var candidate = new LivingWallBridgeAdapterPlanner.Candidate(
                "twelveBodyWall", kind, 12, 10.0D, 0, 0, 0, 0, 0, 0, 12, 3, 0, 0, 4, 12, 1,
                true, false, false, 8, 1L, 2L, 3L);
        assertTrue(LivingWallBridgeAdapterPlanner.score(candidate, spec, 48).isPresent());
        assertTrue(LivingWallBridgeAdapterPlanner.score(candidate, spec, 12).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.score(candidate, spec, 11).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.score(candidate, spec).isPresent());
    }

    @Test
    void capturedDropLimitUsesBottomCenterEuclideanDistance() {
        LivingWallCoordinator.DropLimitSnapshot snapshot = new LivingWallCoordinator.DropLimitSnapshot(7.5D, 12.0D, 42L);
        assertEquals(7.5D, snapshot.maxDropBridgeLength());
        assertEquals(Math.sqrt(3.0D), LivingWallCoordinator.capturedBottomCenterDrop(
                new BlockPos(0, 0, 0), new BlockPos(1, 1, 1)), 1.0e-12D);
    }

    @Test
    void committedExecutionFreezesRosterAndIdentity() {
        UUID planner = UUID.randomUUID();
        UUID worker = UUID.randomUUID();
        LivingWallCoordinator.DropLimitSnapshot limits =
                new LivingWallCoordinator.DropLimitSnapshot(6.0D, 12.0D, 9L);
        LivingWallCoordinator.ConstructionSiteKey key = new LivingWallCoordinator.ConstructionSiteKey(
                4L, new BlockPos(0, 0, 0), new BlockPos(3, 0, 0), new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0), new BlockPos(3, 0, 0),
                LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE, List.of());
        LivingWallCoordinator.CommittedAdmissionSnapshot admission =
                new LivingWallCoordinator.CommittedAdmissionSnapshot(planner, Set.of(worker), limits, key);
        LivingWallCoordinator.CommittedBridgeExecution execution =
                new LivingWallCoordinator.CommittedBridgeExecution(planner, admission, key);
        assertEquals(Set.of(worker), execution.admission().roster());
        assertEquals(key, execution.committedKey());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> execution.admission().roster().add(UUID.randomUUID()));
    }

    @Test
    void allFiveAdaptersRespectLegacyCompatibility() {
        assertTrue(LivingWallBridgeAdapterPlanner.adapterFor("NONE", LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.adapterFor("JUMP_BRIDGE", LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.adapterFor("WALL_LEDGE", LivingWallBridgeAdapterPlanner.AdapterKind.TILT).isPresent());
        assertTrue(LivingWallBridgeAdapterPlanner.adapterFor("NONE", LivingWallBridgeAdapterPlanner.AdapterKind.TILT).isPresent());
        assertTrue(LivingWallBridgeAdapterPlanner.adapterFor("JUMP_BRIDGE", LivingWallBridgeAdapterPlanner.AdapterKind.JUMP).isPresent());
        assertTrue(LivingWallBridgeAdapterPlanner.adapterFor("DROP_TO_LEDGE", LivingWallBridgeAdapterPlanner.AdapterKind.DROP).isPresent());
        assertTrue(LivingWallBridgeAdapterPlanner.adapterFor("WALL_LEDGE", LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.adapterFor("HAZARD_AND_WALL", LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT).isPresent());
    }

    @Test
    void unsupportedLegacyScenarioCannotInstantiateAdapter() {
        assertFalse(LivingWallBridgeAdapterPlanner.adapterFor("HAZARD_AND_WALL", LivingWallBridgeAdapterPlanner.AdapterKind.TILT).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.adapterFor("HAZARD_AND_WALL", LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT).isPresent());
    }

    @Test
    void goldenCandidateWinnersMatchFrozenVectors() {
        assertWinner("supportedLane",
                scored("direct", LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT, "NONE",
                        new LivingWallBridgeAdapterPlanner.ScoreVector(667, 6667, 0, 0, 0, 1250, 3500, 10000, 10000), 5000, 7000, 8000),
                scored("pillar", LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT, "WALL_LEDGE",
                        new LivingWallBridgeAdapterPlanner.ScoreVector(2000, 8333, 0, 0, 2692, 1250, 8000, 10000, 10000), 5000, 7000, 8000));

        assertWinner("hazardGap",
                scored("jump", LivingWallBridgeAdapterPlanner.AdapterKind.JUMP, "JUMP_BRIDGE",
                        new LivingWallBridgeAdapterPlanner.ScoreVector(1111, 6250, 125, 0, 3462, 2500, 8500, 10000, 6000), 9000, 9001, 9002),
                scored("drop", LivingWallBridgeAdapterPlanner.AdapterKind.DROP, "DROP_TO_LEDGE",
                        new LivingWallBridgeAdapterPlanner.ScoreVector(1556, 7500, 125, 0, 3462, 2500, 7800, 5000, 6000), 9000, 9001, 9002));

        assertWinner("elevatedSupportedContour",
                scored("tilt", LivingWallBridgeAdapterPlanner.AdapterKind.TILT, "NONE",
                        new LivingWallBridgeAdapterPlanner.ScoreVector(1333, 7500, 0, 0, 2308, 1875, 7000, 10000, 10000), 7000, 7001, 7002),
                scored("pillar", LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT, "WALL_LEDGE",
                        new LivingWallBridgeAdapterPlanner.ScoreVector(2000, 8333, 0, 0, 2692, 1875, 9000, 10000, 10000), 7000, 7001, 7002));
    }

    @Test
    void scoreBpUsesFrozenIntegerBasisPoints() {
        assertEquals(-36576, LivingWallBridgeAdapterPlanner.scoreBp(
                new LivingWallBridgeAdapterPlanner.ScoreVector(667, 6667, 0, 0, 0, 1250, 3500, 10000, 10000)));
        assertEquals(-58820, LivingWallBridgeAdapterPlanner.scoreBp(
                new LivingWallBridgeAdapterPlanner.ScoreVector(1111, 6250, 125, 0, 3462, 2500, 8500, 10000, 6000)));
    }

    private static LivingWallBridgeAdapterPlanner.ScoredCandidate scored(
            String label, LivingWallBridgeAdapterPlanner.AdapterKind kind, String legacyScenario,
            LivingWallBridgeAdapterPlanner.ScoreVector vector, long pivot, long anchor, long span) {
        LivingWallBridgeAdapterPlanner.Candidate candidate = new LivingWallBridgeAdapterPlanner.Candidate(
                label, kind, 5, 6.5D, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 4, 100, 50,
                true, false, false, 8, pivot, anchor, span);
        LivingWallBridgeAdapterPlanner.AdapterSpec spec = LivingWallBridgeAdapterPlanner.adapterFor(legacyScenario, kind).orElseThrow();
        return new LivingWallBridgeAdapterPlanner.ScoredCandidate(candidate, spec,
                LivingWallBridgeAdapterPlanner.scoreBp(vector), (int) Math.floor(1000.0D * 6.5D + 0.5D));
    }

    private static void assertWinner(String fixture, LivingWallBridgeAdapterPlanner.ScoredCandidate first,
                                     LivingWallBridgeAdapterPlanner.ScoredCandidate second) {
        LivingWallBridgeAdapterPlanner.ScoredCandidate winner = List.of(first, second).stream()
                .sorted(LivingWallBridgeAdapterPlanner.ordering())
                .findFirst()
                .orElseThrow();
        if (fixture.equals("hazardGap")) {
            assertEquals(LivingWallBridgeAdapterPlanner.AdapterKind.JUMP, winner.candidate().kind());
        } else if (fixture.equals("supportedLane") || fixture.equals("elevatedSupportedContour")) {
            assertEquals(LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT, winner.candidate().kind());
        }
    }
}
