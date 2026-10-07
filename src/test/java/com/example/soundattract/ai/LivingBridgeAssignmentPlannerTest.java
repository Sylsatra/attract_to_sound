package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.example.soundattract.quantified.LivingBridgeAssignmentPlanner;
import org.junit.jupiter.api.Test;

class LivingBridgeAssignmentPlannerTest {
    @Test
    void solverMaximizesCardinalityBeforeRouteCost() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LivingBridgeAssignmentPlanner.Slot left = new LivingBridgeAssignmentPlanner.Slot(0, 64, 0);
        LivingBridgeAssignmentPlanner.Slot right = new LivingBridgeAssignmentPlanner.Slot(1, 64, 0);
        var edges = List.of(
                new LivingBridgeAssignmentPlanner.WorkerSlotEdge(first, left, 1.0D, Set.of()),
                new LivingBridgeAssignmentPlanner.WorkerSlotEdge(first, right, 2.0D, Set.of()),
                new LivingBridgeAssignmentPlanner.WorkerSlotEdge(second, left, 1.1D, Set.of()));
        var result = LivingBridgeAssignmentPlanner.solve(
                List.of(new LivingBridgeAssignmentPlanner.WorkerNode(first),
                        new LivingBridgeAssignmentPlanner.WorkerNode(second)),
                List.of(new LivingBridgeAssignmentPlanner.SlotNode(left, Set.of()),
                        new LivingBridgeAssignmentPlanner.SlotNode(right, Set.of())), edges);
        assertEquals(2, result.cardinality());
        assertEquals(Set.of(left, right), result.matches().stream()
                .map(LivingBridgeAssignmentPlanner.Match::slot).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void asyncRankingCarriesWorkerPositionToken() {
        UUID worker = UUID.fromString("00000000-0000-0000-0000-000000000003");
        var result = LivingBridgeAssignmentPlanner.solve(
                List.of(new LivingBridgeAssignmentPlanner.WorkerNode(worker)),
                List.of(new LivingBridgeAssignmentPlanner.SlotNode(
                        new LivingBridgeAssignmentPlanner.Slot(0, 64, 0), Set.of())),
                List.of(new LivingBridgeAssignmentPlanner.WorkerSlotEdge(worker,
                        new LivingBridgeAssignmentPlanner.Slot(0, 64, 0), 1.0D, Set.of())));
        assertEquals(1, result.cardinality());
        assertNotNull(result.matches().get(0).workerId());
    }

    @Test
    void staleRankingIsRejectedWhenGenerationTerrainOrPositionChanges() {
        UUID worker = UUID.fromString("00000000-0000-0000-0000-000000000003");
        var slot = new LivingBridgeAssignmentPlanner.Slot(0, 64, 0);
        var ranking = new LivingBridgeAssignmentPlanner.Ranking(worker, List.of(slot),
                new LivingBridgeAssignmentPlanner.WorkerToken(4L, 9L, worker, 12L));

        assertEquals(true, LivingBridgeAssignmentPlanner.isCurrentRanking(ranking, worker, 4L, 9L, 12L));
        assertEquals(false, LivingBridgeAssignmentPlanner.isCurrentRanking(ranking, worker, 5L, 9L, 12L));
        assertEquals(false, LivingBridgeAssignmentPlanner.isCurrentRanking(ranking, worker, 4L, 10L, 12L));
        assertEquals(false, LivingBridgeAssignmentPlanner.isCurrentRanking(ranking, worker, 4L, 9L, 13L));
    }
}
