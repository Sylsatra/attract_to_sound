package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LivingBridgeMotionDiagnosticsTest {
    private static final UUID MEMBER = UUID.fromString("d23b26d2-8a61-4ef0-93a4-e5701c5930d8");

    @Test
    void classifiesPairOnlyAndVectorOnlyWithoutInferringTheOtherEvent() {
        assertEquals("pair_only_no_vector_observed", LivingBridgeMotionDiagnostics.classify(
                List.of(event(LivingBridgeMotionDiagnostics.EventKind.PAIR, 10L)), 0, 0));
        assertEquals("vector_only_no_pair_observed", LivingBridgeMotionDiagnostics.classify(
                List.of(event(LivingBridgeMotionDiagnostics.EventKind.VECTOR, 10L)), 0, 0));
        assertEquals("no_push_hook_observed", LivingBridgeMotionDiagnostics.classify(List.of(), 0, 0));
    }

    @Test
    void onlyCorrelatesOnePairAndVectorFromSameMemberAndTick() {
        assertEquals("unique_same_member_tick_correlation_noncausal", LivingBridgeMotionDiagnostics.classify(
                List.of(event(LivingBridgeMotionDiagnostics.EventKind.PAIR, 10L),
                        event(LivingBridgeMotionDiagnostics.EventKind.VECTOR, 10L)), 0, 0));
        assertEquals("pair_vector_different_tick_ambiguous", LivingBridgeMotionDiagnostics.classify(
                List.of(event(LivingBridgeMotionDiagnostics.EventKind.PAIR, 10L),
                        event(LivingBridgeMotionDiagnostics.EventKind.VECTOR, 11L)), 0, 0));
    }

    @Test
    void multipleCandidatesRemainAmbiguous() {
        assertEquals("multiple_candidates_ambiguous", LivingBridgeMotionDiagnostics.classify(
                List.of(event(LivingBridgeMotionDiagnostics.EventKind.PAIR, 10L),
                        event(LivingBridgeMotionDiagnostics.EventKind.PAIR, 10L),
                        event(LivingBridgeMotionDiagnostics.EventKind.VECTOR, 10L)), 0, 0));
    }

    @Test
    void anyOmittedEventMakesEveryCandidateConclusionTruncated() {
        List<LivingBridgeMotionDiagnostics.HookObservation> visible = List.of(
                event(LivingBridgeMotionDiagnostics.EventKind.PAIR, 10L),
                event(LivingBridgeMotionDiagnostics.EventKind.VECTOR, 10L));
        assertEquals("truncated_ambiguous", LivingBridgeMotionDiagnostics.classify(visible, 1, 0));
        assertEquals("truncated_ambiguous", LivingBridgeMotionDiagnostics.classify(visible, 0, 1));
        assertEquals("truncated_ambiguous", LivingBridgeMotionDiagnostics.classify(List.of(), 1, 0));
    }

    @Test
    void suppressedSameTickPairMakesTheCappedAttemptTruncated() {
        LivingBridgeMotionDiagnostics.Attempt attempt = attempt();
        fillCap(attempt);
        assertFalse(LivingBridgeMotionDiagnostics.reserve(attempt,
                LivingBridgeMotionDiagnostics.EventKind.PAIR, 100L));
        assertEquals(LivingBridgeMotionDiagnostics.MAX_DIRECT_EVENTS, attempt.observations.size());
        assertEquals(1, attempt.omittedPairs);
        assertEquals("truncated_ambiguous", LivingBridgeMotionDiagnostics.classify(
                attempt.observations, attempt.omittedPairs, attempt.omittedVectors));
    }

    @Test
    void suppressedSameTickVectorMakesTheCappedAttemptTruncated() {
        LivingBridgeMotionDiagnostics.Attempt attempt = attempt();
        fillCap(attempt);
        assertFalse(LivingBridgeMotionDiagnostics.reserve(attempt,
                LivingBridgeMotionDiagnostics.EventKind.VECTOR, 100L));
        assertEquals(LivingBridgeMotionDiagnostics.MAX_DIRECT_EVENTS, attempt.observations.size());
        assertEquals(1, attempt.omittedVectors);
        assertEquals("truncated_ambiguous", LivingBridgeMotionDiagnostics.classify(
                attempt.observations, attempt.omittedPairs, attempt.omittedVectors));
    }

    private static LivingBridgeMotionDiagnostics.Attempt attempt() {
        return new LivingBridgeMotionDiagnostics.Attempt(MEMBER, MEMBER, 0, 1,
                new ResourceLocation("minecraft", "overworld"), 100L, Vec3.ZERO);
    }

    private static void fillCap(LivingBridgeMotionDiagnostics.Attempt attempt) {
        LivingBridgeMotionDiagnostics.reserve(attempt, LivingBridgeMotionDiagnostics.EventKind.PAIR, 100L);
        LivingBridgeMotionDiagnostics.reserve(attempt, LivingBridgeMotionDiagnostics.EventKind.VECTOR, 100L);
        while (attempt.directEvents < LivingBridgeMotionDiagnostics.MAX_DIRECT_EVENTS) {
            LivingBridgeMotionDiagnostics.reserve(attempt, LivingBridgeMotionDiagnostics.EventKind.PAIR, 101L);
        }
    }

    private static LivingBridgeMotionDiagnostics.HookObservation event(
            LivingBridgeMotionDiagnostics.EventKind kind, long tick) {
        return new LivingBridgeMotionDiagnostics.HookObservation(kind, MEMBER, tick);
    }
}
