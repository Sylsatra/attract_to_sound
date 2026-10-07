package com.example.soundattract.worker;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class WorkerComputationsTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void soundScoringHandlesNullInputsAndRangeBoundaries() {
        assertEquals(0.0, WorkerComputations.computeSoundScore(null, Long.MAX_VALUE).score());

        UUID mobId = UUID.randomUUID();
        WorkerScheduler.SoundCandidate outOfRange = new WorkerScheduler.SoundCandidate(
                "far", 20.0, 0.0, 0.0, 0L, 4.0, 50.0, 1.0);
        WorkerScheduler.SoundCandidate valid = new WorkerScheduler.SoundCandidate(
                "near", 3.0, 0.0, 0.0, 0L, 4.0, 2.0, 1.0);
        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 0.0, 0.0, 10L, null,
                Arrays.asList(null, outOfRange, valid), 1.25, 0.0, 0);

        WorkerScheduler.SoundScoreResult result = WorkerComputations.computeSoundScore(request, Long.MAX_VALUE);

        assertEquals(mobId, result.mobUuid());
        assertEquals("near", result.soundId());
        assertEquals(2.0, result.score());
    }

    @Test
    void currentTargetIsRetainedWhenReplacementDoesNotMeetSwitchRatio() {
        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.0, 0.0, 0.0, 10L, "current",
                List.of(
                        new WorkerScheduler.SoundCandidate("current", 1.0, 0.0, 0.0, 0L, 4.0, 4.0, 1.0),
                        new WorkerScheduler.SoundCandidate("replacement", 1.0, 0.0, 0.0, 0L, 4.0, 4.5, 1.0)),
                1.25, 0.0, 0);

        WorkerScheduler.SoundScoreResult result = WorkerComputations.computeSoundScore(request, Long.MAX_VALUE);

        assertEquals("current", result.soundId());
        assertEquals(4.0, result.score());
    }

    @Test
    void groupingRejectsInvalidInputsAndIgnoresNullSnapshots() {
        WorkerScheduler.ConfigSnapshot config = new WorkerScheduler.ConfigSnapshot(16.0, 4, 8, 1.0, 4, 2);
        UUID leaderId = UUID.randomUUID();
        List<WorkerScheduler.MobSnapshot> mobs = new ArrayList<>();
        mobs.add(null);
        mobs.add(new WorkerScheduler.MobSnapshot(null, 0.0, 64.0, 0.0, 20.0, true));
        mobs.add(new WorkerScheduler.MobSnapshot(UUID.randomUUID(), Double.NaN, 64.0, 0.0, 20.0, true));
        mobs.add(new WorkerScheduler.MobSnapshot(leaderId, 0.0, 64.0, 0.0, 20.0, true));

        assertNull(WorkerComputations.computeGroups(mobs, null, Long.MAX_VALUE, DIMENSION));
        assertNull(WorkerComputations.computeGroups(mobs, config, Long.MAX_VALUE, null));

        WorkerScheduler.GroupComputeResult result = WorkerComputations.computeGroups(
                mobs, config, Long.MAX_VALUE, DIMENSION);

        assertNotNull(result);
        assertEquals(leaderId, result.mobUuidToLeaderUuid().get(leaderId));
    }

    @Test
    void groupingCarriesDimensionGenerationIntoWorkerResult() {
        UUID mobId = UUID.randomUUID();
        WorkerScheduler.ConfigSnapshot config = new WorkerScheduler.ConfigSnapshot(16.0, 4, 8, 1.0, 4, 2, 17L);
        WorkerScheduler.GroupComputeResult result = WorkerComputations.computeGroups(
                List.of(new WorkerScheduler.MobSnapshot(mobId, 0.0, 64.0, 0.0, 20.0, true)),
                config, Long.MAX_VALUE, DIMENSION);

        assertNotNull(result);
        assertEquals(17L, result.generation());
    }

    @Test
    void soundRequestOwnsItsCandidateSnapshot() {
        List<WorkerScheduler.SoundCandidate> candidates = new ArrayList<>();
        candidates.add(new WorkerScheduler.SoundCandidate(
                "snapshot", 0.0, 0.0, 0.0, 0L, 4.0, 1.0, 1.0));

        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.0, 0.0, 0.0, 0L, null, candidates, 1.0, 0.0, 0);
        candidates.clear();

        assertEquals(1, request.candidates.size());
        assertThrows(UnsupportedOperationException.class, () -> request.candidates.clear());
    }

    @Test
    void soundScoreCacheKeyIgnoresNullCandidates() {
        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.0, 0.0, 0.0, 0L, null,
                Arrays.asList(null, new WorkerScheduler.SoundCandidate(
                        "safe", 0.0, 0.0, 0.0, 0L, 4.0, 1.0, 1.0)),
                1.0, 0.0, 0);

        assertDoesNotThrow(() -> com.example.soundattract.quantified.QuantifiedWorkScheduler
                .soundScoreCacheKeyForTests(request));
    }

    @Test
    void soundScoreCarriesDimensionThroughWorkerBoundary() {
        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.0, 0.0, 0.0, 0L, null, DIMENSION.toString(),
                List.of(new WorkerScheduler.SoundCandidate(
                        "safe", 0.0, 0.0, 0.0, 0L, 4.0, 1.0, 1.0)),
                1.0, 0.0, 0);

        WorkerScheduler.SoundScoreResult result = WorkerComputations.computeSoundScore(request, Long.MAX_VALUE);

        assertEquals(DIMENSION.toString(), result.dimensionKey());
    }

    @Test
    void soundScoreCacheKeySeparatesDimensions() {
        WorkerScheduler.SoundCandidate candidate = new WorkerScheduler.SoundCandidate(
                "safe", 0.0, 0.0, 0.0, 0L, 4.0, 1.0, 1.0);
        UUID mobId = UUID.randomUUID();
        WorkerScheduler.SoundScoreRequest overworld = new WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 0.0, 0.0, 0L, null, "minecraft:overworld",
                List.of(candidate), 1.0, 0.0, 0);
        WorkerScheduler.SoundScoreRequest nether = new WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 0.0, 0.0, 0L, null, "minecraft:the_nether",
                List.of(candidate), 1.0, 0.0, 0);

        assertNotEquals(
                com.example.soundattract.quantified.QuantifiedWorkScheduler.soundScoreCacheKeyForTests(overworld),
                com.example.soundattract.quantified.QuantifiedWorkScheduler.soundScoreCacheKeyForTests(nether));
    }

    @Test
    void thousandMobSnapshotStressRemainsBoundedAndThreadSafe() {
        WorkerScheduler.ConfigSnapshot config = new WorkerScheduler.ConfigSnapshot(24.0, 64, 32, 1.5, 8, 4);
        List<WorkerScheduler.MobSnapshot> mobs = IntStream.range(0, 1000)
                .mapToObj(i -> new WorkerScheduler.MobSnapshot(
                        new UUID(0L, i + 1L), i % 100, 64.0, i / 100, 20.0, true))
                .toList();

        List<WorkerScheduler.GroupComputeResult> results = IntStream.range(0, 8)
                .parallel()
                .mapToObj(i -> WorkerComputations.computeGroups(mobs, config, Long.MAX_VALUE, DIMENSION))
                .toList();

        assertEquals(8, results.size());
        assertTrue(results.stream().allMatch(result -> result != null
                && result.mobUuidToLeaderUuid().size() <= mobs.size()
                && result.edgeMobsByLeaderUuid().size() <= config.maxLeaders()));
    }

    @Test
    void soundScoreMicrobatchCoalescesPendingMobRequestsAndCleansUp() throws Exception {
        com.example.soundattract.quantified.QuantifiedWorkScheduler scheduler =
                new com.example.soundattract.quantified.QuantifiedWorkScheduler();
        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.0, 0.0, 0.0, 0L, null, DIMENSION.toString(),
                List.of(new WorkerScheduler.SoundCandidate(
                        "safe", 0.0, 0.0, 0.0, 0L, 4.0, 1.0, 1.0)),
                1.0, 0.0, 0);

        Future<?> first = scheduler.submitSoundScore(List.of(request));
        Future<?> duplicate = scheduler.submitSoundScore(List.of(request));

        assertFalse(first.isDone());
        assertFalse(duplicate.isDone());
        scheduler.clearResults();
        assertThrows(java.util.concurrent.ExecutionException.class, first::get);
        assertThrows(java.util.concurrent.ExecutionException.class, duplicate::get);

        Future<?> afterStop = scheduler.submitSoundScore(List.of(request));
        assertTrue(afterStop.isDone());
    }

    @Test
    void soundScoreRequestsRemainPendingWhenInFlightBudgetIsFull() throws Exception {
        com.example.soundattract.quantified.QuantifiedWorkScheduler scheduler =
                new com.example.soundattract.quantified.QuantifiedWorkScheduler();
        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.0, 0.0, 0.0, 0L, null, DIMENSION.toString(),
                List.of(new WorkerScheduler.SoundCandidate(
                        "safe", 0.0, 0.0, 0.0, 0L, 4.0, 1.0, 1.0)),
                1.0, 0.0, 0);

        Future<?> completion = scheduler.submitSoundScore(List.of(request));
        Field reservedTasks = scheduler.getClass().getDeclaredField("reservedTasks");
        reservedTasks.setAccessible(true);
        AtomicInteger reserved = (AtomicInteger) reservedTasks.get(scheduler);
        reserved.set(256);

        scheduler.flushPendingSoundScores();
        assertFalse(completion.isDone());

        scheduler.clearResults();
        assertThrows(java.util.concurrent.ExecutionException.class, completion::get);
    }

    @Test
    void soundScoreHotPathBenchmarkEvidence() {
        List<WorkerScheduler.SoundCandidate> candidates = IntStream.range(0, 256)
                .mapToObj(i -> new WorkerScheduler.SoundCandidate(
                        "sound-" + i, (i % 32) - 16.0, 64.0, (i / 32) - 4.0,
                        i, 32.0, 1.0 + (i % 7) * 0.1, 1.0))
                .toList();
        WorkerScheduler.SoundScoreRequest request = new WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.5, 64.0, -1.5, 200L, "sound-17", DIMENSION.toString(),
                candidates, 1.25, 0.5, 20);

        double checksum = 0.0;
        for (int i = 0; i < 3_000; i++) {
            checksum += WorkerComputations.computeSoundScore(request, Long.MAX_VALUE).score();
        }

        long[] samples = new long[7];
        for (int sample = 0; sample < samples.length; sample++) {
            long start = System.nanoTime();
            for (int i = 0; i < 5_000; i++) {
                checksum += WorkerComputations.computeSoundScore(request, Long.MAX_VALUE).score();
            }
            samples[sample] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(samples);
        assertTrue(Double.isFinite(checksum));
        System.out.printf("sound-score-benchmark candidates=%d iterations=%d medianMs=%.3f checksum=%.3f%n",
                candidates.size(), 5_000, samples[samples.length / 2] / 1_000_000.0, checksum);
    }
}
