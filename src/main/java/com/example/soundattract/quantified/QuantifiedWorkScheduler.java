package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import com.example.soundattract.worker.SoundAttractWorkScheduler;
import com.example.soundattract.worker.WorkerComputations;
import com.example.soundattract.worker.WorkerScheduler;
import org.admany.quantified.api.compute.QuantifiedCompute;
import org.admany.quantified.api.compute.QuantifiedCompute.Complexity;
import org.admany.quantified.api.compute.QuantifiedCompute.WorkloadKind;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class QuantifiedWorkScheduler implements SoundAttractWorkScheduler {


    private static final long MAX_SOUND_SCORE_SLICE_CACHE_ENTRIES = 65536L;
    private static final int RESULT_QUEUE_CAPACITY = 4096;
    private static final int MAX_IN_FLIGHT_TASKS = 256;
    private static final int SOUND_BATCH_QUEUE_CAPACITY = 4096;
    private static final int MAX_SOUND_REQUESTS_PER_FLUSH = 256;
    private final BlockingQueue<WorkerScheduler.GroupComputeResult> groupResults = new ArrayBlockingQueue<>(RESULT_QUEUE_CAPACITY);
    private final BlockingQueue<WorkerScheduler.SoundScoreResult> soundResults = new ArrayBlockingQueue<>(RESULT_QUEUE_CAPACITY);
    private final BlockingQueue<PendingSoundScore> pendingSoundScores = new ArrayBlockingQueue<>(SOUND_BATCH_QUEUE_CAPACITY);
    private final ConcurrentHashMap<SoundRequestKey, PendingSoundScore> pendingSoundByKey = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicInteger reservedTasks = new AtomicInteger();
    private final Object lifecycleLock = new Object();
    private volatile boolean acceptingResults = true;

    private record SoundRequestKey(UUID mobUuid, String dimensionKey) {
    }

    private record PendingSoundScore(SoundRequestKey key, WorkerScheduler.SoundScoreRequest request,
                                     CompletableFuture<Void> completion) {
    }

    public QuantifiedWorkScheduler() {
    }

    @Override
    public Future<?> submitGroupCompute(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, ResourceLocation dimension) {
        if (!acceptingResults || mobs == null || mobs.isEmpty() || cfg == null || dimension == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!reserveTask()) return CompletableFuture.completedFuture(null);
        List<WorkerScheduler.MobSnapshot> snapshot = Collections.unmodifiableList(new ArrayList<>(mobs));
        long deadlineMs = computeDeadlineMs();
        Duration timeout = computeTimeout();

        CompletableFuture<?> future;
        try {
            future = submitFuture(
                "soundattract_group_compute",
                () -> WorkerComputations.computeGroups(snapshot, cfg, deadlineMs, dimension),
                timeout
            );
        } catch (Throwable ignored) {
            releaseTask();
            return CompletableFuture.completedFuture(null);
        }
        if (future == null) {
            releaseTask();
            return CompletableFuture.completedFuture(null);
        }
        track(future);
        return future.handle((result, throwable) -> {
            if (acceptingResults && throwable == null && result instanceof WorkerScheduler.GroupComputeResult r && r.dimension() != null) {
                this.groupResults.offer(r);
            }
            return null;
        });
    }

    @Override
    public Future<?> submitSoundScore(List<WorkerScheduler.SoundScoreRequest> batch) {
        if (!acceptingResults || batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        List<CompletableFuture<Void>> completions = new ArrayList<>(batch.size());
        synchronized (lifecycleLock) {
            if (!acceptingResults) return CompletableFuture.completedFuture(null);
            for (WorkerScheduler.SoundScoreRequest request : batch) {
                if (request == null) {
                    continue;
                }
                SoundRequestKey key = new SoundRequestKey(request.mobUuid, request.dimensionKey);
                CompletableFuture<Void> completion = new CompletableFuture<>();
                PendingSoundScore pending = new PendingSoundScore(key, request, completion);
                PendingSoundScore existing = pendingSoundByKey.putIfAbsent(key, pending);
                if (existing != null) {
                    completions.add(existing.completion());
                    continue;
                }
                if (!pendingSoundScores.offer(pending)) {
                    pendingSoundByKey.remove(key, pending);
                    completion.completeExceptionally(new java.util.concurrent.RejectedExecutionException(
                            "SoundAttract sound-score queue is full"));
                }
                completions.add(completion);
            }
        }
        if (completions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(completions.toArray(new CompletableFuture<?>[0]));
    }

    @Override
    public void flushPendingSoundScores() {
        if (!acceptingResults) {
            return;
        }
        List<PendingSoundScore> pending = new ArrayList<>(MAX_SOUND_REQUESTS_PER_FLUSH);
        pendingSoundScores.drainTo(pending, MAX_SOUND_REQUESTS_PER_FLUSH);
        if (pending.isEmpty()) {
            return;
        }
        List<WorkerScheduler.SoundScoreRequest> batch = new ArrayList<>(pending.size());
        for (PendingSoundScore item : pending) {
            pendingSoundByKey.remove(item.key(), item);
            batch.add(item.request());
        }
        Future<?> submitted = submitSoundScoreNow(batch);
        if (submitted == null) {
            requeuePendingSoundScores(pending);
            return;
        }
        if (submitted instanceof CompletableFuture<?> completion) {
            completion.whenComplete((value, throwable) -> {
                for (PendingSoundScore item : pending) {
                    if (throwable == null) {
                        item.completion().complete(null);
                    } else {
                        item.completion().completeExceptionally(throwable);
                    }
                }
            });
        } else {
            for (PendingSoundScore item : pending) {
                item.completion().complete(null);
            }
        }
    }

    private void requeuePendingSoundScores(List<PendingSoundScore> pending) {
        synchronized (lifecycleLock) {
            for (PendingSoundScore item : pending) {
                if (!acceptingResults) {
                    item.completion().completeExceptionally(new java.util.concurrent.RejectedExecutionException(
                            "SoundAttract server stopped"));
                    continue;
                }
                PendingSoundScore existing = pendingSoundByKey.putIfAbsent(item.key(), item);
                if (existing != null || !pendingSoundScores.offer(item)) {
                    if (existing == null) {
                        pendingSoundByKey.remove(item.key(), item);
                    }
                    item.completion().completeExceptionally(new java.util.concurrent.RejectedExecutionException(
                            "SoundAttract sound-score queue is full"));
                }
            }
        }
    }

    private Future<?> submitSoundScoreNow(List<WorkerScheduler.SoundScoreRequest> batch) {
        if (!acceptingResults || batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        if (!reserveTask()) return null;
        List<WorkerScheduler.SoundScoreRequest> snapshot = Collections.unmodifiableList(new ArrayList<>(batch));

        long deadlineMs = computeDeadlineMs();
        Duration timeout = computeTimeout();

        CompletableFuture<?> future;
        try {
            CompletableFuture<List<WorkerScheduler.SoundScoreResult>> parallel = QuantifiedGpuSoundScoreBridge.trySubmit(snapshot, deadlineMs, timeout);
            if (parallel == null) {
                parallel = ParallelComputeBridge.trySubmitSoundScores(snapshot, deadlineMs, timeout);
            }
            if (parallel != null) {
                future = withCpuFallback(parallel, snapshot, deadlineMs, timeout);
            } else {
                future = submitFuture(
                    "soundattract_sound_score",
                    () -> WorkerComputations.computeSoundScores(snapshot, deadlineMs),
                    timeout
                );
            }
        } catch (Throwable ignored) {
            releaseTask();
            return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException(
                    "SoundAttract sound-score task was rejected"));
        }
        if (future == null) {
            releaseTask();
            return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException(
                    "SoundAttract sound-score task returned no future"));
        }
        track(future);
        return future.handle((result, throwable) -> {
            if (acceptingResults && throwable == null && result instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof WorkerScheduler.SoundScoreResult r) {
                        String dimension = r.dimensionKey();
                        if (dimension == null) {
                            dimension = dimensionFor(snapshot, r.mobUuid());
                        }
                        this.soundResults.offer(new WorkerScheduler.SoundScoreResult(
                                r.mobUuid(), r.soundId(), r.score(), dimension));
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

    @Override
    public void clearResults() {
        Set<CompletableFuture<?>> toCancel;
        synchronized (lifecycleLock) {
            acceptingResults = false;
            toCancel = Set.copyOf(inFlight);
            inFlight.clear();
            reservedTasks.set(0);
            groupResults.clear();
            soundResults.clear();
            PendingSoundScore pending;
            while ((pending = pendingSoundScores.poll()) != null) {
                pendingSoundByKey.remove(pending.key(), pending);
                pending.completion().completeExceptionally(new java.util.concurrent.RejectedExecutionException(
                        "SoundAttract server stopped"));
            }
            pendingSoundByKey.clear();
        }
        for (CompletableFuture<?> future : toCancel) {
            future.cancel(false);
        }
        WorkerScheduler.clearResults();
    }

    private <T> void track(CompletableFuture<T> future) {
        if (future == null) {
            releaseTask();
            return;
        }
        synchronized (lifecycleLock) {
            if (!acceptingResults) {
                releaseTask();
                future.cancel(false);
                return;
            }
            inFlight.add(future);
        }
        future.whenComplete((value, throwable) -> {
            inFlight.remove(future);
            releaseTask();
        });
    }

    private CompletableFuture<List<WorkerScheduler.SoundScoreResult>> withCpuFallback(
            CompletableFuture<List<WorkerScheduler.SoundScoreResult>> accelerated,
            List<WorkerScheduler.SoundScoreRequest> snapshot,
            long deadlineMs,
            Duration timeout) {
        CancellableSoundResults result = new CancellableSoundResults();
        result.setSource(accelerated);
        accelerated.whenComplete((values, throwable) -> {
            try {
                if (result.isCancelled()) {
                    return;
                }
                if (throwable == null && values != null && values.size() == snapshot.size()) {
                    result.complete(values);
                    result.clearSource();
                    return;
                }
                if (!acceptingResults) {
                    result.completeExceptionally(new java.util.concurrent.RejectedExecutionException(
                            "SoundAttract server is stopped"));
                    result.clearSource();
                    return;
                }
                CompletableFuture<List<WorkerScheduler.SoundScoreResult>> cpuFallback = submitFuture(
                        "soundattract_sound_score_cpu_fallback",
                        () -> WorkerComputations.computeSoundScores(snapshot, deadlineMs),
                        timeout);
                if (cpuFallback == null) {
                    result.completeExceptionally(new java.util.concurrent.RejectedExecutionException(
                            "Quantified CPU fallback returned no future"));
                    result.clearSource();
                    return;
                }
                result.setSource(cpuFallback);
                cpuFallback.whenComplete((fallbackValues, fallbackFailure) -> {
                    try {
                        if (result.isCancelled()) {
                            return;
                        }
                        if (fallbackFailure == null && fallbackValues != null) {
                            result.complete(fallbackValues);
                        } else if (fallbackFailure != null) {
                            result.completeExceptionally(fallbackFailure);
                        } else {
                            result.completeExceptionally(new IllegalStateException(
                                    "Quantified CPU fallback returned no result"));
                        }
                    } finally {
                        result.clearSource();
                    }
                });
            } catch (Throwable fallbackFailure) {
                result.completeExceptionally(fallbackFailure);
                result.clearSource();
            } finally {



                if (result.isCancelled()) {
                    result.clearSource();
                }
            }
        });
        return result;
    }

    private static final class CancellableSoundResults extends CompletableFuture<List<WorkerScheduler.SoundScoreResult>> {
        private volatile CompletableFuture<?> source;

        private void setSource(CompletableFuture<?> source) {
            this.source = source;
            if (isCancelled() && source != null) {
                source.cancel(false);
            }
        }

        private void clearSource() {
            source = null;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            CompletableFuture<?> current = source;
            if (cancelled && current != null) {
                current.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        }
    }

    private static String dimensionFor(List<WorkerScheduler.SoundScoreRequest> requests, UUID mobUuid) {
        if (requests == null || mobUuid == null) {
            return null;
        }
        for (WorkerScheduler.SoundScoreRequest request : requests) {
            if (request != null && mobUuid.equals(request.mobUuid)) {
                return request.dimensionKey;
            }
        }
        return null;
    }

    private boolean reserveTask() {
        synchronized (lifecycleLock) {
            if (!acceptingResults || reservedTasks.get() >= MAX_IN_FLIGHT_TASKS) {
                return false;
            }
            reservedTasks.incrementAndGet();
            return true;
        }
    }

    private void releaseTask() {
        reservedTasks.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    private <T> CompletableFuture<T> submitFuture(String taskName, java.util.function.Supplier<T> work, Duration timeout) {
        QuantifiedCompute.Builder<T> builder = QuantifiedCompute.<T>builder(
                SoundAttractMod.MOD_ID, taskName, System.nanoTime())
            .cpuFallback(work)
            .dataSizeBytes(4096L)
            .parallelUnits(configuredParallelUnits())
            .complexity(Complexity.MODERATE)
            .kind(WorkloadKind.GENERAL)
            .threadSafe()
            .allowMainThreadRerouting(false);
        if (timeout != null) {
            builder.timeout(timeout);
        }
        return builder.submit();
    }

    private static int configuredParallelUnits() {
        try {
            Integer configured = SoundAttractConfig.COMMON.workerThreads.get();
            if (configured != null) {
                return Math.max(1, Math.min(64, configured));
            }
        } catch (Throwable ignored) {
        }
        return Math.max(1, Math.min(16, Runtime.getRuntime().availableProcessors()));
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
            .append(req.dimensionKey == null ? "" : req.dimensionKey).append('|')
            .append(req.switchRatio).append('|')
            .append(req.noveltyBonus).append('|')
            .append(req.noveltyTicks).append('|')
            .append(req.gameTime / 5L);
        List<WorkerScheduler.SoundCandidate> normalized = new ArrayList<>(req.candidates);
        normalized.removeIf(candidate -> candidate == null);
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
            out.flush();
            return bytes.toByteArray();
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    private static WorkerScheduler.SoundScoreResult decodeSoundScoreResult(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new WorkerScheduler.SoundScoreResult(null, null, 0.0);
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            UUID mobUuid = readNullableUuid(in);
            String soundId = readNullableString(in);
            double score = in.readDouble();
            return new WorkerScheduler.SoundScoreResult(mobUuid, soundId, score);
        } catch (Throwable ignored) {
            return new WorkerScheduler.SoundScoreResult(null, null, 0.0);
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
                        return new WorkerScheduler.SoundScoreResult(req == null ? null : req.mobUuid, null, 0.0);
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
                    sliceCacheMaxEntries = Math.min(
                            MAX_SOUND_SCORE_SLICE_CACHE_ENTRIES,
                            Math.max(64L, SoundAttractConfig.COMMON.quantifiedSoundScoreSliceCacheMaxEntries.get().longValue()));
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



                    CancellableSoundResults cancellable = new CancellableSoundResults();
                    cancellable.setSource(future);
                    future.orTimeout(ms, TimeUnit.MILLISECONDS).whenComplete((value, throwable) -> {
                        if (throwable != null) {
                            future.cancel(false);
                            cancellable.completeExceptionally(throwable);
                        } else {
                            cancellable.complete(value);
                        }
                        cancellable.clearSource();
                    });
                    return cancellable;
                }
                return future;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}