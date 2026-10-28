package com.example.soundattract.worker;

import com.example.soundattract.SoundAttractMod;
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
import java.util.concurrent.LinkedBlockingQueue;
import net.minecraft.resources.ResourceLocation;

public final class WorkerScheduler {
    private static final BlockingQueue<GroupComputeResult> GROUP_RESULTS = new LinkedBlockingQueue<>();
    private static final BlockingQueue<SoundScoreResult> SOUND_RESULTS = new LinkedBlockingQueue<>();

    private WorkerScheduler() {}

    public static Future<?> submitGroupCompute(List<MobSnapshot> mobs, ConfigSnapshot cfg, ResourceLocation dimension) {
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
                GroupComputeResult result = computeGroups(mobs, cfg, deadlineMs, dimension);
                if (result != null) GROUP_RESULTS.offer(result);
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.error("[WorkerScheduler] Group compute failed", t);
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
                computeSoundScores(batch, deadlineMs);
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.error("[WorkerScheduler] Sound scoring failed", t);
                }
            }
        }, Priority.LOW, true);
    }

    public static List<SoundScoreResult> drainSoundScoreResults() {
        List<SoundScoreResult> list = new ArrayList<>();
        SOUND_RESULTS.drainTo(list);
        return list;
    }

    private static GroupComputeResult computeGroups(List<MobSnapshot> mobs, ConfigSnapshot cfg, long deadlineMs, ResourceLocation dimension) {
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
                                 int edgeMobsPerSector) {}

    public record GroupComputeResult(ResourceLocation dimension,
                                     Map<UUID, UUID> mobUuidToLeaderUuid,
                                     Map<UUID, Set<UUID>> edgeMobsByLeaderUuid,
                                     Set<UUID> deserterUuids) {}

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
                                 long gameTime, String currentTargetSoundId, String dimensionKey,
                                 List<SoundCandidate> candidates,
                                 double switchRatio, double noveltyBonus, int noveltyTicks) {
            this.mobUuid = mobUuid;
            this.mobX = mobX; this.mobY = mobY; this.mobZ = mobZ;
            this.gameTime = gameTime;
            this.currentTargetSoundId = currentTargetSoundId;
            this.dimensionKey = dimensionKey;
            this.candidates = candidates == null ? Collections.emptyList() : candidates;
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

    public record SoundScoreResult(UUID mobUuid, String soundId, double score, String dimensionKey) {}
}
