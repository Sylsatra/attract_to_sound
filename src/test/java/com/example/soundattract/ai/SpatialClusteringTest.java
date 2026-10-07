package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpatialClusteringTest {
    private record Candidate(UUID uuid, Vec3 position) {
    }

    private static Candidate createCandidate(double x, double y, double z) {
        return new Candidate(UUID.randomUUID(), new Vec3(x, y, z));
    }

    @Test
    void elevationSeparatesCaveFromCliff() {
        List<Candidate> candidates = new ArrayList<>();
        Candidate c1 = createCandidate(10.0D, 89.0D, 10.0D);
        Candidate c2 = createCandidate(11.0D, 89.2D, 10.5D);
        Candidate c3 = createCandidate(10.5D, 89.0D, 11.0D);

        Candidate l1 = createCandidate(10.0D, 98.0D, 10.0D);
        Candidate l2 = createCandidate(11.0D, 98.5D, 10.5D);
        Candidate l3 = createCandidate(10.5D, 98.0D, 11.0D);

        candidates.addAll(List.of(c1, c2, c3, l1, l2, l3));

        List<List<Candidate>> clusters = LivingWallCoordinator.partitionSpatialElements(
                candidates, Candidate::position, Candidate::uuid, 12.0D, 5.0D, 3, 48);

        assertEquals(2, clusters.size());
        assertEquals(3, clusters.get(0).size());
        assertEquals(3, clusters.get(1).size());

        boolean firstHasCave = clusters.get(0).contains(c1);
        List<Candidate> caveCluster = firstHasCave ? clusters.get(0) : clusters.get(1);
        List<Candidate> cliffCluster = firstHasCave ? clusters.get(1) : clusters.get(0);

        assertTrue(caveCluster.containsAll(List.of(c1, c2, c3)));
        assertFalse(caveCluster.contains(l1));
        assertTrue(cliffCluster.containsAll(List.of(l1, l2, l3)));
        assertFalse(cliffCluster.contains(c1));
    }

    @Test
    void horizontalDistanceSeparatesDistantHordes() {
        List<Candidate> candidates = new ArrayList<>();
        Candidate a1 = createCandidate(0.0D, 64.0D, 0.0D);
        Candidate a2 = createCandidate(1.0D, 64.0D, 0.5D);
        Candidate a3 = createCandidate(0.5D, 64.0D, 1.0D);

        Candidate b1 = createCandidate(40.0D, 64.0D, 0.0D);
        Candidate b2 = createCandidate(41.0D, 64.0D, 0.5D);
        Candidate b3 = createCandidate(40.5D, 64.0D, 1.0D);

        candidates.addAll(List.of(a1, a2, a3, b1, b2, b3));

        List<List<Candidate>> clusters = LivingWallCoordinator.partitionSpatialElements(
                candidates, Candidate::position, Candidate::uuid, 12.0D, 5.0D, 3, 48);

        assertEquals(2, clusters.size());
        assertEquals(3, clusters.get(0).size());
        assertEquals(3, clusters.get(1).size());
    }

    @Test
    void connectedComponentsChainsThroughIntermediateNeighbors() {
        List<Candidate> candidates = new ArrayList<>();
        Candidate m1 = createCandidate(0.0D, 64.0D, 0.0D);
        Candidate m2 = createCandidate(8.0D, 64.0D, 0.0D);
        Candidate m3 = createCandidate(16.0D, 64.0D, 0.0D);
        Candidate m4 = createCandidate(24.0D, 64.0D, 0.0D);

        candidates.addAll(List.of(m1, m2, m3, m4));

        List<List<Candidate>> clusters = LivingWallCoordinator.partitionSpatialElements(
                candidates, Candidate::position, Candidate::uuid, 10.0D, 5.0D, 3, 48);

        assertEquals(1, clusters.size());
        assertEquals(4, clusters.get(0).size());
        assertTrue(clusters.get(0).containsAll(List.of(m1, m2, m3, m4)));
    }

    @Test
    void subMinimumClustersAreDiscarded() {
        List<Candidate> candidates = new ArrayList<>();
        candidates.add(createCandidate(0.0D, 64.0D, 0.0D));
        candidates.add(createCandidate(1.0D, 64.0D, 0.0D));

        List<List<Candidate>> clusters = LivingWallCoordinator.partitionSpatialElements(
                candidates, Candidate::position, Candidate::uuid, 12.0D, 5.0D, 3, 48);

        assertTrue(clusters.isEmpty());
    }

    @Test
    void clustersAreSortedBySizeDescending() {
        List<Candidate> candidates = new ArrayList<>();
        Candidate a1 = createCandidate(0.0D, 64.0D, 0.0D);
        Candidate a2 = createCandidate(1.0D, 64.0D, 0.0D);
        Candidate a3 = createCandidate(0.0D, 64.0D, 1.0D);

        Candidate b1 = createCandidate(50.0D, 64.0D, 0.0D);
        Candidate b2 = createCandidate(51.0D, 64.0D, 0.0D);
        Candidate b3 = createCandidate(50.0D, 64.0D, 1.0D);
        Candidate b4 = createCandidate(51.0D, 64.0D, 1.0D);
        Candidate b5 = createCandidate(50.5D, 64.0D, 0.5D);

        candidates.addAll(List.of(a1, a2, a3, b1, b2, b3, b4, b5));

        List<List<Candidate>> clusters = LivingWallCoordinator.partitionSpatialElements(
                candidates, Candidate::position, Candidate::uuid, 12.0D, 5.0D, 3, 48);

        assertEquals(2, clusters.size());
        assertEquals(5, clusters.get(0).size());
        assertEquals(3, clusters.get(1).size());
    }

    @Test
    void clusterIsCappedAtMaxMembers() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            candidates.add(createCandidate(i * 0.1D, 64.0D, 0.0D));
        }

        List<List<Candidate>> clusters = LivingWallCoordinator.partitionSpatialElements(
                candidates, Candidate::position, Candidate::uuid, 12.0D, 5.0D, 3, 48);

        assertEquals(1, clusters.size());
        assertEquals(48, clusters.get(0).size());
    }
}
