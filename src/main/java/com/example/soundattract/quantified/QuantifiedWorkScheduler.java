package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import com.example.soundattract.worker.LocalWorkScheduler;
import com.example.soundattract.worker.SoundAttractWorkScheduler;
import com.example.soundattract.worker.WorkerComputations;
import com.example.soundattract.worker.WorkerScheduler;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuantifiedWorkScheduler implements SoundAttractWorkScheduler {
    private final BlockingQueue<WorkerScheduler.GroupComputeResult> groupResults = new LinkedBlockingQueue<>();
    private final BlockingQueue<WorkerScheduler.SoundScoreResult> soundResults = new LinkedBlockingQueue<>();

    private final LocalWorkScheduler fallback = new LocalWorkScheduler();

    public QuantifiedWorkScheduler() {
        if (!QuantifiedOptionalBridge.register(SoundAttractMod.MOD_ID)) {
            throw new RuntimeException("Failed to initialize QuantifiedWorkScheduler");
        }
    }

    @Override
    public Future<?> submitGroupCompute(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, ResourceLocation dimension) {
        long deadlineMs = computeDeadlineMs();
        Duration timeout = computeTimeout();

        CompletableFuture<?> future = submitFuture(
            "soundattract_group_compute",
            () -> WorkerComputations.computeGroups(mobs, cfg, deadlineMs, dimension),
            timeout
        );
        return future.handle((result, throwable) -> {
            if (throwable != null || result == null) {
                return this.fallback.submitGroupCompute(mobs, cfg, dimension);
            }
            if (result instanceof WorkerScheduler.GroupComputeResult r && r.dimension() != null) {
                this.groupResults.offer(r);
            }
            return null;
        });
    }

    @Override
    public Future<?> submitSoundScore(List<WorkerScheduler.SoundScoreRequest> batch) {
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);

        long deadlineMs = computeDeadlineMs();
        Duration timeout = computeTimeout();

        CompletableFuture<?> future;
        CompletableFuture<List<WorkerScheduler.SoundScoreResult>> parallel = ParallelComputeBridge.trySubmitSoundScores(batch, deadlineMs, timeout);
        if (parallel != null) {
            future = parallel;
        } else {
            future = submitFuture(
                "soundattract_sound_score",
                () -> WorkerComputations.computeSoundScores(batch, deadlineMs),
                timeout
            );
        }
        return future.handle((result, throwable) -> {
            if (throwable != null) {
                return this.fallback.submitSoundScore(batch);
            }
            if (result == null) {
                return this.fallback.submitSoundScore(batch);
            }
            if (result instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof WorkerScheduler.SoundScoreResult r) {
                        this.soundResults.offer(r);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<WorkerScheduler.GroupComputeResult> drainGroupResults() {
        List<WorkerScheduler.GroupComputeResult> out = new ArrayList<>();
        this.groupResults.drainTo(out);
        out.addAll(WorkerScheduler.drainGroupResults());
        return out;
    }

    @Override
    public List<WorkerScheduler.SoundScoreResult> drainSoundScoreResults() {
        List<WorkerScheduler.SoundScoreResult> out = new ArrayList<>();
        this.soundResults.drainTo(out);
        out.addAll(WorkerScheduler.drainSoundScoreResults());
        return out;
    }

    private CompletableFuture<?> submitFuture(String taskName, java.util.function.Supplier<?> work, Duration timeout) {
        CompletableFuture<?> future = QuantifiedOptionalBridge.trySubmitTask(
            SoundAttractMod.MOD_ID,
            taskName,
            work,
            true,
            false,
            timeout,
            taskName
        );
        if (future != null) {
            return future;
        }
        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("Quantified task submission unavailable"));
        return failed;
    }

    public static String soundScoreCacheKeyForTests(WorkerScheduler.SoundScoreRequest req) {
        return soundScoreCacheKey(req);
    }

    public static byte[] encodeSoundScoreResultForTests(WorkerScheduler.SoundScoreResult result) {
        return encodeSoundScoreResult(result);
    }

    public static WorkerScheduler.SoundScoreResult decodeSoundScoreResultForTests(byte[] bytes) {
        return decodeSoundScoreResult(bytes);
    }

    private static String soundScoreCacheKey(WorkerScheduler.SoundScoreRequest req) {
        if (req == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(512);
        builder.append(bucket(req.mobX)).append('|')
            .append(bucket(req.mobY)).append('|')
            .append(bucket(req.mobZ)).append('|')
            .append(req.currentTargetSoundId == null ? "" : req.currentTargetSoundId).append('|')
            .append(req.switchRatio).append('|')
            .append(req.noveltyBonus).append('|')
            .append(req.noveltyTicks).append('|')
            .append(req.gameTime / 5L);
        List<WorkerScheduler.SoundCandidate> normalized = new ArrayList<>(req.candidates);
        normalized.sort(Comparator
            .comparing((WorkerScheduler.SoundCandidate c) -> c.soundId == null ? "" : c.soundId)
            .thenComparingDouble(c -> c.x)
            .thenComparingDouble(c -> c.y)
            .thenComparingDouble(c -> c.z)
            .thenComparingLong(c -> c.gameTime)
            .thenComparingDouble(c -> c.range)
            .thenComparingDouble(c -> c.weight)
            .thenComparingDouble(c -> c.mufflingFactor));
        for (WorkerScheduler.SoundCandidate candidate : normalized) {
            builder.append('|')
                .append(candidate.soundId == null ? "" : candidate.soundId).append('@')
                .append(bucket(candidate.x)).append(',')
                .append(bucket(candidate.y)).append(',')
                .append(bucket(candidate.z)).append(',')
                .append(candidate.gameTime / 5L).append(',')
                .append(candidate.range).append(',')
                .append(candidate.weight).append(',')
                .append(candidate.mufflingFactor);
        }
        return builder.toString();
    }

    private static byte[] encodeSoundScoreResult(WorkerScheduler.SoundScoreResult result) {
        if (result == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeNullableUuid(out, result.mobUuid());
            writeNullableString(out, result.soundId());
            out.writeDouble(result.score());
            writeNullableString(out, result.dimensionKey());
            out.flush();
            return bytes.toByteArray();
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    private static WorkerScheduler.SoundScoreResult decodeSoundScoreResult(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new WorkerScheduler.SoundScoreResult(null, null, 0.0, null);
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            UUID mobUuid = readNullableUuid(in);
            String soundId = readNullableString(in);
            double score = in.readDouble();
            String dimensionKey = in.available() > 0 ? readNullableString(in) : null;
            return new WorkerScheduler.SoundScoreResult(mobUuid, soundId, score, dimensionKey);
        } catch (Throwable ignored) {
            return new WorkerScheduler.SoundScoreResult(null, null, 0.0, null);
        }
    }

    private static long bucket(double value) {
        return Math.round(value * 100.0);
    }

    private static void writeNullableUuid(DataOutputStream out, UUID value) throws Exception {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeLong(value.getMostSignificantBits());
            out.writeLong(value.getLeastSignificantBits());
        }
    }

    private static UUID readNullableUuid(DataInputStream in) throws Exception {
        if (!in.readBoolean()) {
            return null;
        }
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeNullableString(DataOutputStream out, String value) throws Exception {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readNullableString(DataInputStream in) throws Exception {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private long computeDeadlineMs() {
        long computed = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computed = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        return computed;
    }

    private Duration computeTimeout() {
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = Math.max(5L, budget.longValue());
                return Duration.ofMillis(cfgBudget);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static final class ParallelComputeBridge {
        private static CompletableFuture<List<WorkerScheduler.SoundScoreResult>> trySubmitSoundScores(
            List<WorkerScheduler.SoundScoreRequest> batch,
            long deadlineMs,
            Duration timeout
        ) {
            if (batch == null || batch.isEmpty()) {
                return CompletableFuture.completedFuture(java.util.Collections.emptyList());
            }
            try {
                long taskKey = System.nanoTime();
                Object builder = QuantifiedOptionalBridge.tryCreateParallelBuilder(SoundAttractMod.MOD_ID, "soundattract_sound_score_parallel", taskKey);
                if (builder == null) {
                    return null;
                }

                Supplier<List<WorkerScheduler.SoundScoreRequest>> supplier = () -> batch;
                if (!QuantifiedOptionalBridge.trySetParallelSlices(builder, supplier)) {
                    return null;
                }

                Function<WorkerScheduler.SoundScoreRequest, WorkerScheduler.SoundScoreResult> scorer = req -> {
                    try {
                        return WorkerComputations.computeSoundScore(req, deadlineMs);
                    } catch (Throwable t) {
                        return new WorkerScheduler.SoundScoreResult(req == null ? null : req.mobUuid, null, 0.0, req == null ? null : req.dimensionKey);
                    }
                };
                if (!QuantifiedOptionalBridge.trySetParallelSliceExecutor(builder, scorer)) {
                    return null;
                }

                boolean useSliceCache = false;
                boolean persistentSliceCache = false;
                long sliceCacheTtlTicks = 20L;
                long sliceCacheMaxEntries = 4096L;
                try {
                    useSliceCache = SoundAttractConfig.COMMON.enableQuantifiedSoundScoreSliceCache.get();
                    persistentSliceCache = SoundAttractConfig.COMMON.quantifiedSoundScoreSliceCachePersistent.get();
                    sliceCacheTtlTicks = Math.max(1L, SoundAttractConfig.COMMON.quantifiedSoundScoreSliceCacheTtlTicks.get().longValue());
                    sliceCacheMaxEntries = Math.max(64L, SoundAttractConfig.COMMON.quantifiedSoundScoreSliceCacheMaxEntries.get().longValue());
                } catch (Throwable ignored) {
                }
                if (useSliceCache) {
                    Duration sliceCacheTtl = Duration.ofMillis(sliceCacheTtlTicks * 50L);
                    boolean configured = persistentSliceCache
                        ? QuantifiedOptionalBridge.trySetParallelPersistentSliceCache(
                            builder,
                            "soundattract_sound_score_slice",
                            QuantifiedWorkScheduler::soundScoreCacheKey,
                            QuantifiedWorkScheduler::encodeSoundScoreResult,
                            QuantifiedWorkScheduler::decodeSoundScoreResult,
                            sliceCacheTtl,
                            sliceCacheMaxEntries,
                            false
                        )
                        : QuantifiedOptionalBridge.trySetParallelMemorySliceCache(
                            builder,
                            "soundattract_sound_score_slice",
                            QuantifiedWorkScheduler::soundScoreCacheKey,
                            QuantifiedWorkScheduler::encodeSoundScoreResult,
                            QuantifiedWorkScheduler::decodeSoundScoreResult,
                            sliceCacheTtl,
                            sliceCacheMaxEntries
                        );
                    if (!configured) {
                        return null;
                    }
                }

                int maxParallelism = 0;
                try {
                    maxParallelism = SoundAttractConfig.COMMON.workerThreads.get();
                } catch (Throwable ignored) {
                }
                if (maxParallelism > 0) {
                    if (!QuantifiedOptionalBridge.trySetParallelMaxParallelism(builder, maxParallelism)) {
                        return null;
                    }
                }

                QuantifiedOptionalBridge.trySetParallelBestEffort(builder);

                CompletableFuture<List<WorkerScheduler.SoundScoreResult>> future = QuantifiedOptionalBridge.trySubmitParallel(builder);
                if (future == null) {
                    return null;
                }
                if (timeout != null) {
                    long ms = Math.max(1L, timeout.toMillis());
                    return future.orTimeout(ms, TimeUnit.MILLISECONDS);
                }
                return future;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
