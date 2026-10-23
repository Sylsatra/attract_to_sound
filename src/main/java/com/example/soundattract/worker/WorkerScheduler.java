package com.example.soundattract.worker;

import com.example.soundattract.Soundattract;
import com.example.soundattract.async.AsyncManager;
import com.example.soundattract.async.AsyncManager.Priority;
import com.example.soundattract.config.SoundAttractConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import net.minecraft.resources.Identifier;

public final class WorkerScheduler {
    private static final int RESULT_QUEUE_CAPACITY = 4096;
    private static final BlockingQueue<GroupComputeResult> GROUP_RESULTS = new ArrayBlockingQueue<>(RESULT_QUEUE_CAPACITY);
    private static final BlockingQueue<SoundScoreResult> SOUND_RESULTS = new ArrayBlockingQueue<>(RESULT_QUEUE_CAPACITY);

    private WorkerScheduler() {}

    public static Future<?> submitGroupCompute(List<MobSnapshot> mobs, ConfigSnapshot cfg, Identifier dimension) {
        List<MobSnapshot> snapshot = mobs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(mobs));
        long computedDeadline = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computedDeadline = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        final long deadlineMs = computedDeadline;
        return AsyncManager.submit("soundattract_group_compute", () -> {
            try {
                GroupComputeResult result = computeGroups(snapshot, cfg, deadlineMs, dimension);
                if (result != null) GROUP_RESULTS.offer(result);
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.error("[WorkerScheduler] Group compute failed", t);
                }
            }
        }, Priority.LOW, true);
    }

    public static List<GroupComputeResult> drainGroupResults() {
        List<GroupComputeResult> list = new ArrayList<>();
        GROUP_RESULTS.drainTo(list);
        return list;
    }

    public static Future<?> submitSoundScore(List<SoundScoreRequest> batch) {
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        List<SoundScoreRequest> snapshot = Collections.unmodifiableList(new ArrayList<>(batch));
        long computedDeadline = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computedDeadline = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        final long deadlineMs = computedDeadline;
        return AsyncManager.submit("soundattract_sound_score", () -> {
            try {
                computeSoundScores(snapshot, deadlineMs);
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.error("[WorkerScheduler] Sound scoring failed", t);
                }
            }
        }, Priority.LOW, true);
    }

    public static List<SoundScoreResult> drainSoundScoreResults() {
        List<SoundScoreResult> list = new ArrayList<>();
        SOUND_RESULTS.drainTo(list);
        return list;
    }

    public static void clearResults() {
        GROUP_RESULTS.clear();
        SOUND_RESULTS.clear();
    }

    private static GroupComputeResult computeGroups(List<MobSnapshot> mobs, ConfigSnapshot cfg, long deadlineMs, Identifier dimension) {
        return WorkerComputations.computeGroups(mobs, cfg, deadlineMs, dimension);
    }

    private static void computeSoundScores(List<SoundScoreRequest> batch, long deadlineMs) {
        List<SoundScoreResult> results = WorkerComputations.computeSoundScores(batch, deadlineMs);
        if (results.isEmpty()) return;
        for (SoundScoreResult r : results) {
            SOUND_RESULTS.offer(r);
        }
    }

    public record MobSnapshot(UUID uuid, double x, double y, double z, double health, boolean alive) {}

    public record ConfigSnapshot(double leaderGroupRadius,
                                 int maxLeaders,
                                 int maxGroupSize,
                                 double leaderSpacingMultiplier,
                                 int numEdgeSectors,
                                 int edgeMobsPerSector,
                                 long generation) {
        public ConfigSnapshot(double leaderGroupRadius,
                              int maxLeaders,
                              int maxGroupSize,
                              double leaderSpacingMultiplier,
                              int numEdgeSectors,
                              int edgeMobsPerSector) {
            this(leaderGroupRadius, maxLeaders, maxGroupSize, leaderSpacingMultiplier,
                    numEdgeSectors, edgeMobsPerSector, 0L);
        }
    }

    public record GroupComputeResult(Identifier dimension,
                                     Map<UUID, UUID> mobUuidToLeaderUuid,
                                     Map<UUID, Set<UUID>> edgeMobsByLeaderUuid,
                                     Set<UUID> deserterUuids,
                                     long generation) {
        public GroupComputeResult(Identifier dimension,
                                  Map<UUID, UUID> mobUuidToLeaderUuid,
                                  Map<UUID, Set<UUID>> edgeMobsByLeaderUuid,
                                  Set<UUID> deserterUuids) {
            this(dimension, mobUuidToLeaderUuid, edgeMobsByLeaderUuid, deserterUuids, 0L);
        }
    }

    public static final class SoundScoreRequest {
        public final UUID mobUuid;
        public final double mobX, mobY, mobZ;
        public final long gameTime;
        public final String currentTargetSoundId; 
        public final String dimensionKey;
        public final List<SoundCandidate> candidates;
        public final double switchRatio;
        public final double noveltyBonus;
        public final int noveltyTicks;

        public SoundScoreRequest(UUID mobUuid, double mobX, double mobY, double mobZ,
                                 long gameTime, String currentTargetSoundId,
                                 List<SoundCandidate> candidates,
                                 double switchRatio, double noveltyBonus, int noveltyTicks) {
            this(mobUuid, mobX, mobY, mobZ, gameTime, currentTargetSoundId, null, candidates,
                    switchRatio, noveltyBonus, noveltyTicks);
        }

        public SoundScoreRequest(UUID mobUuid, double mobX, double mobY, double mobZ,
                                 long gameTime, String currentTargetSoundId, String dimensionKey,
                                 List<SoundCandidate> candidates,
                                 double switchRatio, double noveltyBonus, int noveltyTicks) {
            this.mobUuid = mobUuid;
            this.mobX = mobX; this.mobY = mobY; this.mobZ = mobZ;
            this.gameTime = gameTime;
            this.currentTargetSoundId = currentTargetSoundId;
            this.dimensionKey = dimensionKey;
            this.candidates = candidates == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(candidates));
            this.switchRatio = switchRatio;
            this.noveltyBonus = noveltyBonus;
            this.noveltyTicks = noveltyTicks;
        }
    }

    public static final class SoundCandidate {
        public final String soundId;
        public final double x, y, z;
        public final long gameTime; 
        public final double range;  
        public final double weight; 
        public final double mufflingFactor; 

        public SoundCandidate(String soundId, double x, double y, double z, long gameTime,
                              double range, double weight, double mufflingFactor) {
            this.soundId = soundId;
            this.x = x; this.y = y; this.z = z;
            this.gameTime = gameTime;
            this.range = range;
            this.weight = weight;
            this.mufflingFactor = mufflingFactor;
        }
    }

    public record SoundScoreResult(UUID mobUuid, String soundId, double score, String dimensionKey) {
        public SoundScoreResult(UUID mobUuid, String soundId, double score) {
            this(mobUuid, soundId, score, null);
        }
    }
}
