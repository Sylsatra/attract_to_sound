package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanonicalLandingRingPruningTest {
    @Test
    void ringLowerBoundMatchesEveryCandidateAndPreservesStrictTieBehavior() {
        for (int radius = 1; radius <= 4; radius++) {
            for (int maxHeight = 0; maxHeight <= 12; maxHeight++) {
                double enumeratedMinimum = Double.POSITIVE_INFINITY;
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                        for (int y = 0; y <= maxHeight; y++) {
                            double score = (double) x * x + (double) y * y + (double) z * z;
                            if (y > 0) score *= 0.5D;
                            enumeratedMinimum = Math.min(enumeratedMinimum, score);
                        }
                    }
                }
                double bound = LivingWallCoordinator.minimumCanonicalLandingRingScore(radius, maxHeight);
                assertEquals(enumeratedMinimum, bound,
                        "ring lower bound mismatch for radius=" + radius + ", height=" + maxHeight);
                for (double bestScore : new double[]{0.5D, 1.0D, 1.5D, 2.5D, 5.0D, 50.0D}) {
                    assertEquals(enumeratedMinimum < bestScore, bound < bestScore,
                            "pruning must preserve strict score ties for radius=" + radius
                                    + ", height=" + maxHeight + ", best=" + bestScore);
                }
            }
        }
    }

    @Test
    void knownDefaultRingCanSkip936NoncompetitiveCandidateAllocations() {
        double bestScore = 1.5D;
        int maxHeight = 12;
        int skippedCandidates = 0;
        for (int radius = 2; radius <= 4; radius++) {
            assertTrue(LivingWallCoordinator.minimumCanonicalLandingRingScore(radius, maxHeight) >= bestScore);
            skippedCandidates += 8 * radius * (maxHeight + 1);
        }
        assertEquals(936, skippedCandidates);
        assertFalse(LivingWallCoordinator.minimumCanonicalLandingRingScore(1, maxHeight) >= bestScore);
    }
}
