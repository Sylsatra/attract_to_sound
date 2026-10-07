package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class TraversalCorridorWitnessTest {
    @Test
    void identityDependsOnMembershipAndEnvelope() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TraversalCorridorWitness base = TraversalCorridorWitness.forTest(
                new ResourceLocation("minecraft", "overworld"), List.of(first, second), new AABB(0, 64, 0, 4, 68, 4));
        TraversalCorridorWitness same = TraversalCorridorWitness.forTest(
                new ResourceLocation("minecraft", "overworld"), List.of(second, first), new AABB(0, 64, 0, 4, 68, 4));
        TraversalCorridorWitness changedRoster = TraversalCorridorWitness.forTest(
                new ResourceLocation("minecraft", "overworld"), List.of(first), new AABB(0, 64, 0, 4, 68, 4));
        TraversalCorridorWitness changedEnvelope = TraversalCorridorWitness.forTest(
                new ResourceLocation("minecraft", "overworld"), List.of(first, second), new AABB(0, 64, 0, 5, 68, 4));

        assertTrue(base.identityMatches(same));
        assertFalse(base.identityMatches(changedRoster));
        assertFalse(base.identityMatches(changedEnvelope));
        assertTrue(base.rosterMatches(new ResourceLocation("minecraft", "overworld"), Set.of(first, second)));
        assertFalse(base.rosterMatches(new ResourceLocation("minecraft", "the_nether"), Set.of(first, second)));
        assertFalse(base.rosterMatches(new ResourceLocation("minecraft", "overworld"), Set.of(first)));
    }

    @Test
    void centerlineBudgetIsExactAndDeduplicates() {
        TraversalCorridorWitness witness = TraversalCorridorWitness.forTest(
                new ResourceLocation("minecraft", "overworld"),
                List.of(UUID.fromString("00000000-0000-0000-0000-000000000011")),
                new AABB(0, 64, 0, 100, 68, 2));

        for (int i = 0; i < TraversalCorridorWitness.CENTERLINE_BUDGET; i++) {
            assertTrue(witness.recordCenterlineFingerprintForTest(new BlockPos(i, 64, 0), "solid:" + i));
        }
        assertTrue(witness.recordCenterlineFingerprintForTest(new BlockPos(0, 64, 0), "solid:0"));
        assertFalse(witness.recordCenterlineFingerprintForTest(new BlockPos(TraversalCorridorWitness.CENTERLINE_BUDGET, 64, 0), "overflow"));
        assertEquals(TraversalCorridorWitness.CENTERLINE_BUDGET, witness.centerlineCount());
    }

    @Test
    void envelopeBudgetAndMalformedFingerprintsFailClosedBeforePartialState() {
        TraversalCorridorWitness witness = TraversalCorridorWitness.forTest(
                new ResourceLocation("minecraft", "overworld"),
                List.of(UUID.fromString("00000000-0000-0000-0000-000000000012")),
                new AABB(0, 64, 0, 3000, 68, 2));

        assertFalse(witness.recordEnvelopeFingerprintForTest(new BlockPos(0, 64, 0), "unloaded"));
        assertEquals(0, witness.envelopeCount());
        for (int i = 0; i < TraversalCorridorWitness.ENVELOPE_BUDGET; i++) {
            assertTrue(witness.recordEnvelopeFingerprintForTest(new BlockPos(i, 64, 0), "cell:" + i));
        }
        assertFalse(witness.recordEnvelopeFingerprintForTest(new BlockPos(TraversalCorridorWitness.ENVELOPE_BUDGET, 64, 0), "overflow"));
        assertEquals(TraversalCorridorWitness.ENVELOPE_BUDGET, witness.envelopeCount());
    }

    @Test
    void shapeTestBudgetStopsAtInclusiveBoundary() {
        TraversalCorridorWitness witness = TraversalCorridorWitness.forTest(
                new ResourceLocation("minecraft", "overworld"),
                List.of(UUID.fromString("00000000-0000-0000-0000-000000000013")),
                new AABB(0, 64, 0, 2, 68, 2));

        for (int i = 0; i < TraversalCorridorWitness.SHAPE_TEST_BUDGET; i++) {
            assertTrue(witness.recordShapeTest());
        }
        assertFalse(witness.recordShapeTest());
        assertEquals(TraversalCorridorWitness.SHAPE_TEST_BUDGET, witness.shapeTests());

        witness.beginShapeTestPass();
        assertEquals(0, witness.shapeTests());
        assertTrue(witness.recordShapeTest());
    }
}
