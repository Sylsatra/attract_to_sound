package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.async.AsyncManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import com.example.soundattract.worker.SoundAttractWorkScheduler;
import com.example.soundattract.worker.WorkerComputations;
import com.example.soundattract.worker.WorkerScheduler;
import org.admany.quantified.api.ComputeRequest;
import org.admany.quantified.api.QuantifiedAPI;
import org.admany.quantified.api.compute.QuantifiedCompute.Complexity;
import org.admany.quantified.api.compute.QuantifiedCompute.WorkloadKind;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuantifiedWorkScheduler implements SoundAttractWorkScheduler {

    private static final long MAX_SOUND_SCORE_SLICE_CACHE_ENTRIES = 65536L;
    private static final int RESULT_QUEUE_CAPACITY = 4096;
    private static final int MAX_IN_FLIGHT_TASKS = 256;
    private static final int SOUND_BATCH_QUEUE_CAPACITY = 4096;
    private static final int MAX_SOUND_REQUESTS_PER_FLUSH = 256;
    private final BlockingQueue<WorkerScheduler.GroupComputeResult> groupResults = new ArrayBlockingQueue<>(RESULT_QUEUE_CAPACITY);
    private final BlockingQueue<WorkerScheduler.SoundScoreResult> soundResults = new ArrayBlockingQueue<>(RESULT_QUEUE_CAPACITY);
    private final BlockingQueue<PendingSoundScore> pendingSoundScores = new ArrayBlockingQueue<>(SOUND_BATCH_QUEUE_CAPACITY);
    private final Map<SoundRequestKey, PendingSoundScore> pendingSoundByKey = new HashMap<>();
    private final Set<CompletableFuture<?>> inFlight = new HashSet<>();
    private final boolean useQuantified;
    private final Object lifecycleLock = new Object();
    private int reservedTasks;
    private volatile boolean acceptingResults = true;

    private record SoundRequestKey(UUID mobUuid, String dimensionKey) {
    }

    private record PendingSoundScore(SoundRequestKey key, WorkerScheduler.SoundScoreRequest request,
                                     CompletableFuture<Void> completion) {
    }

    public QuantifiedWorkScheduler() {
        this(true);
    }

    private QuantifiedWorkScheduler(boolean useQuantified) {
        if (useQuantified && !QuantifiedOptionalBridge.register(SoundAttractMod.MOD_ID)) {
            throw new IllegalStateException("Quantified worker API is unavailable");
        }
        this.useQuantified = useQuantified;
    }

    public static QuantifiedWorkScheduler local() {
        return new QuantifiedWorkScheduler(false);
    }

    @Override
    public Future<?> submitGroupCompute(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, ResourceLocation dimension) {
        if (mobs == null || mobs.isEmpty() || cfg == null || dimension == null || !reserveTask()) return null;
        CompletableFuture<WorkerScheduler.GroupComputeResult> future;
        try {
            List<WorkerScheduler.MobSnapshot> snapshot = List.copyOf(mobs);
            future = submitFuture("soundattract_group_compute",
                    () -> WorkerComputations.computeGroups(snapshot, cfg, Long.MAX_VALUE, dimension), computeTimeout());
        } catch (RuntimeException | LinkageError ignored) {
            releaseTask();
            return null;
        }
        if (future == null) {
            releaseTask();
            return null;
        }
        return track(future, result -> {
            if (result != null && result.dimension() != null) groupResults.offer(result);
        });
    }

    @Override
    public Future<?> submitSoundScore(List<WorkerScheduler.SoundScoreRequest> batch) {
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        List<CompletableFuture<Void>> completions = new ArrayList<>(batch.size());
        synchronized (lifecycleLock) {
            if (!acceptingResults) return CompletableFuture.failedFuture(stopped());
            for (WorkerScheduler.SoundScoreRequest request : batch) {
                if (request == null || request.mobUuid == null) continue;
                SoundRequestKey key = new SoundRequestKey(request.mobUuid, request.dimensionKey);
                PendingSoundScore existing = pendingSoundByKey.get(key);
                if (existing != null) {
                    completions.add(existing.completion());
                    continue;
                }
                CompletableFuture<Void> completion = new CompletableFuture<>();
                PendingSoundScore pending = new PendingSoundScore(key, request, completion);
                if (pendingSoundScores.offer(pending)) {
                    pendingSoundByKey.put(key, pending);
                } else {
                    completion.completeExceptionally(new RejectedExecutionException("SoundAttract sound-score queue is full"));
                }
                completions.add(completion);
            }
        }
        return CompletableFuture.allOf(completions.toArray(CompletableFuture<?>[]::new));
    }

    public void flushPendingSoundScores() {
        List<PendingSoundScore> pending = new ArrayList<>(MAX_SOUND_REQUESTS_PER_FLUSH);
        synchronized (lifecycleLock) {
            if (pendingSoundScores.isEmpty() || !reserveTask()) return;
            pendingSoundScores.drainTo(pending, MAX_SOUND_REQUESTS_PER_FLUSH);
        }
        List<WorkerScheduler.SoundScoreRequest> snapshot = pending.stream().map(PendingSoundScore::request).toList();
        long deadline = computeDeadlineMs();
        Duration timeout = computeTimeout();
        CompletableFuture<List<WorkerScheduler.SoundScoreResult>> future;
        try {
            CompletableFuture<List<WorkerScheduler.SoundScoreResult>> accelerated = useQuantified
                    ? QuantifiedGpuSoundScoreBridge.trySubmit(snapshot, deadline, timeout) : null;
            if (accelerated == null && useQuantified) {
                accelerated = ParallelComputeBridge.trySubmitSoundScores(snapshot, deadline, timeout);
            }
            future = accelerated == null
                    ? submitFuture("soundattract_sound_score", () -> WorkerComputations.computeSoundScores(snapshot, deadline), timeout)
                    : withCpuFallback(accelerated, snapshot, deadline, timeout);
        } catch (RuntimeException | LinkageError failure) {
            releaseTask();
            completeSoundScores(pending, failure);
            return;
        }
        if (future == null) {
            releaseTask();
            completeSoundScores(pending, new RejectedExecutionException("SoundAttract sound-score task was rejected"));
            return;
        }
        track(future, results -> publishSoundScores(pending, results))
                .whenComplete((result, failure) -> completeSoundScores(pending, failure));
    }

    private void publishSoundScores(List<PendingSoundScore> pending, List<WorkerScheduler.SoundScoreResult> results) {
        if (results == null) return;
        Map<SoundRequestKey, PendingSoundScore> requests = new HashMap<>();
        for (PendingSoundScore request : pending) {
            requests.put(request.key(), request);
        }
        for (WorkerScheduler.SoundScoreResult result : results) {
            if (result == null || result.mobUuid() == null || result.soundId() == null) continue;
            SoundRequestKey key = new SoundRequestKey(result.mobUuid(), result.dimensionKey());
            PendingSoundScore request = requests.get(key);
            if (request != null && pendingSoundByKey.get(key) == request) soundResults.offer(result);
        }
    }

    private void completeSoundScores(List<PendingSoundScore> pending, Throwable failure) {
        synchronized (lifecycleLock) {
            for (PendingSoundScore item : pending) pendingSoundByKey.remove(item.key(), item);
        }
        for (PendingSoundScore item : pending) {
            if (failure == null) item.completion().complete(null);
            else item.completion().completeExceptionally(failure);
        }
    }

    @Override
    public List<WorkerScheduler.GroupComputeResult> drainGroupResults() {
        List<WorkerScheduler.GroupComputeResult> out = new ArrayList<>();
        groupResults.drainTo(out);
        return out;
    }

    @Override
    public List<WorkerScheduler.SoundScoreResult> drainSoundScoreResults() {
        List<WorkerScheduler.SoundScoreResult> out = new ArrayList<>();
        soundResults.drainTo(out);
        return out;
    }

    public void clearDimension(String dimensionKey) {
        List<PendingSoundScore> removed = new ArrayList<>();
        synchronized (lifecycleLock) {
            pendingSoundScores.removeIf(item -> dimensionKey.equals(item.key().dimensionKey()));
            pendingSoundByKey.values().removeIf(item -> {
                if (!dimensionKey.equals(item.key().dimensionKey())) return false;
                removed.add(item);
                return true;
            });
            soundResults.removeIf(result -> dimensionKey.equals(result.dimensionKey()));
        }
        for (PendingSoundScore item : removed) item.completion().cancel(false);
    }

    public void clearResults() {
        Set<CompletableFuture<?>> toCancel;
        List<PendingSoundScore> pending;
        synchronized (lifecycleLock) {
            acceptingResults = false;
            toCancel = Set.copyOf(inFlight);
            inFlight.clear();
            pending = List.copyOf(pendingSoundByKey.values());
            pendingSoundScores.clear();
            pendingSoundByKey.clear();
            groupResults.clear();
            soundResults.clear();
        }
        RejectedExecutionException failure = stopped();
        for (PendingSoundScore item : pending) item.completion().completeExceptionally(failure);
        for (CompletableFuture<?> future : toCancel) future.cancel(false);
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> future, Consumer<T> publisher) {
        synchronized (lifecycleLock) {
            if (acceptingResults) {
                inFlight.add(future);
                return future.whenComplete((result, failure) -> {
                    synchronized (lifecycleLock) {
                        inFlight.remove(future);
                        reservedTasks--;
                        if (acceptingResults && failure == null) publisher.accept(result);
                    }
                });
            }
            reservedTasks--;
        }
        future.cancel(false);
        return future;
    }

    private static RejectedExecutionException stopped() {
        return new RejectedExecutionException("SoundAttract server stopped");
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
                    result.completeExceptionally(new RejectedExecutionException(
                            "SoundAttract server is stopped"));
                    result.clearSource();
                    return;
                }
                CompletableFuture<List<WorkerScheduler.SoundScoreResult>> cpuFallback = submitFuture(
                        "soundattract_sound_score_cpu_fallback",
                        () -> WorkerComputations.computeSoundScores(snapshot, deadlineMs),
                        timeout);
                if (cpuFallback == null) {
                    result.completeExceptionally(new RejectedExecutionException(
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

    private boolean reserveTask() {
        synchronized (lifecycleLock) {
            if (!acceptingResults || reservedTasks >= MAX_IN_FLIGHT_TASKS) return false;
            reservedTasks++;
            return true;
        }
    }

    private void releaseTask() {
        synchronized (lifecycleLock) {
            reservedTasks--;
        }
    }

    private <T> CompletableFuture<T> submitFuture(String taskName, Supplier<T> work, Duration timeout) {
        if (!useQuantified) return AsyncManager.submit(taskName, work, AsyncManager.Priority.LOW, true);
        ComputeRequest<T> request = QuantifiedAPI.<T>compute(SoundAttractMod.MOD_ID, taskName)
                .key(System.nanoTime())
                .work(work)
                .dataSizeBytes(4096L)
                .parallelUnits(configuredParallelUnits())
                .complexity(Complexity.MODERATE)
                .kind(WorkloadKind.GENERAL)
                .threadSafe()
                .allowMainThreadRerouting(false)
                .background();
        if (timeout != null) request.timeout(timeout);
        return request.submit();
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
        builder.append(req.mobUuid).append('|').append(bucket(req.mobX)).append('|')
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
                        return new WorkerScheduler.SoundScoreResult(req == null ? null : req.mobUuid, null, 0.0,
                                req == null ? null : req.dimensionKey);
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
