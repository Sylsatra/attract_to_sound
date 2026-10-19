package com.example.soundattract.quantified;

import org.admany.quantified.api.opencl.QuantifiedOpenCL;
import org.admany.quantified.api.vulkan.QuantifiedVulkan;
import com.example.soundattract.worker.WorkerScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class QuantifiedGpuSoundScoreBridge {
    private static final int MINIMUM_CANDIDATES = 32;

    private static final int MAX_GPU_REQUESTS = 1024;
    private static final int MAX_GPU_CANDIDATES = 65536;
    private static final long BACKEND_PROBE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final Object BACKEND_PROBE_LOCK = new Object();
    private static final AtomicLong NEXT_BACKEND_PROBE_NANOS = new AtomicLong();
    private static volatile Backend cachedBackend;

    private enum Backend {
        OPENCL,
        VULKAN,
        NONE
    }

    private QuantifiedGpuSoundScoreBridge() {
    }

    static String backendName() {
        Backend backend = availableBackend();
        return backend == null ? "CPU fallback" : backend.name();
    }

    static CompletableFuture<List<WorkerScheduler.SoundScoreResult>> trySubmit(
            List<WorkerScheduler.SoundScoreRequest> requests,
            long deadlineMs,
            Duration timeout) {
        if (requests == null || requests.isEmpty() || requests.size() > MAX_GPU_REQUESTS) {
            return null;
        }
        Backend backend = availableBackend();
        if (backend == null) {
            return null;
        }

        List<List<Entry>> groups = new ArrayList<>(requests.size());
        int total = 0;
        for (WorkerScheduler.SoundScoreRequest request : requests) {
            if (!isGpuSafe(request)) {
                return null;
            }
            List<Entry> group = new ArrayList<>();
            if (request.candidates != null) {
                for (WorkerScheduler.SoundCandidate candidate : request.candidates) {
                    if (candidate == null) {
                        return null;
                    }
                    if (!isGpuSafe(candidate)) {
                        return null;
                    }
                    double dx = request.mobX - candidate.x;
                    double dz = request.mobZ - candidate.z;
                    double distance = Math.hypot(dx, dz);
                    boolean valid = distance <= Math.max(0.0001, candidate.range);
                    long age = Math.max(0L, request.gameTime - candidate.gameTime);
                    float weight = valid ? (float) candidate.weight : -Float.MAX_VALUE;
                    float novelty = valid && age <= request.noveltyTicks ? (float) request.noveltyBonus : 0.0F;
                    if (valid) {
                        double cpuScore = candidate.weight
                                + (age <= request.noveltyTicks ? request.noveltyBonus : 0.0D);
                        if ((double) (weight + novelty) != cpuScore) {
                            return null;
                        }
                    }
                    group.add(new Entry(candidate, distance, weight, novelty, valid));
                    total++;
                    if (total > MAX_GPU_CANDIDATES) {
                        return null;
                    }
                }
            }
            groups.add(group);
        }
        if (total < MINIMUM_CANDIDATES) {
            return null;
        }

        float[] weights = new float[total];
        float[] novelty = new float[total];
        int offset = 0;
        for (List<Entry> group : groups) {
            for (Entry entry : group) {
                weights[offset] = entry.weight;
                novelty[offset] = entry.novelty;
                entry.index = offset++;
            }
        }

        CompletableFuture<float[]> future;
        try {
            future = backend == Backend.OPENCL
                    ? QuantifiedOpenCL.parallelVectorAdd(
                        "soundattract", "sound_score_gpu_opencl", System.nanoTime(), weights, novelty)
                    : QuantifiedVulkan.parallelVectorAdd(
                        "soundattract", "sound_score_gpu_vulkan", System.nanoTime(), weights, novelty);
        } catch (Throwable ignored) {
            invalidateBackend();
            return null;
        }
        if (future == null) {
            invalidateBackend();
            return null;
        }
        CompletableFuture<float[]> source = future;
        CancellableResult<List<WorkerScheduler.SoundScoreResult>> result = new CancellableResult<>();
        result.setSource(future);
        if (timeout != null) {
            source = source.orTimeout(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        }
        source.whenComplete((scores, throwable) -> {
            try {
                if (throwable != null) {



                    future.cancel(false);
                    invalidateBackend();
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(selectResults(requests, groups, scores));
                }
            } catch (Throwable selectionFailure) {
                result.completeExceptionally(selectionFailure);
            } finally {
                result.clearSource();
            }
        });
        return result;
    }

    private static Backend availableBackend() {
        long now = System.nanoTime();
        Backend known = cachedBackend;
        if (known != null && now < NEXT_BACKEND_PROBE_NANOS.get()) {
            return known == Backend.NONE ? null : known;
        }
        synchronized (BACKEND_PROBE_LOCK) {
            now = System.nanoTime();
            known = cachedBackend;
            if (known != null && now < NEXT_BACKEND_PROBE_NANOS.get()) {
                return known == Backend.NONE ? null : known;
            }
            Backend detected = Backend.NONE;
            try {
                if (QuantifiedOpenCL.isGpuReady()) {
                    detected = Backend.OPENCL;
                }
            } catch (Throwable ignored) {

            }
            if (detected == Backend.NONE) {
                try {
                    if (QuantifiedVulkan.isGpuReady()) {
                        detected = Backend.VULKAN;
                    }
                } catch (Throwable ignored) {

                }
            }
            cachedBackend = detected;
            NEXT_BACKEND_PROBE_NANOS.set(now + BACKEND_PROBE_INTERVAL_NANOS);
            return detected == Backend.NONE ? null : detected;
        }
    }

    private static void invalidateBackend() {
        synchronized (BACKEND_PROBE_LOCK) {
            cachedBackend = null;
            NEXT_BACKEND_PROBE_NANOS.set(0L);
        }
    }

    private static boolean isGpuSafe(WorkerScheduler.SoundScoreRequest request) {
        return request != null
                && Double.isFinite(request.mobX)
                && Double.isFinite(request.mobY)
                && Double.isFinite(request.mobZ)
                && Double.isFinite(request.switchRatio)
                && Double.isFinite(request.noveltyBonus)
                && Math.abs(request.noveltyBonus) <= Float.MAX_VALUE;
    }

    private static boolean isGpuSafe(WorkerScheduler.SoundCandidate candidate) {
        return candidate != null
                && Double.isFinite(candidate.x)
                && Double.isFinite(candidate.y)
                && Double.isFinite(candidate.z)
                && Double.isFinite(candidate.range)
                && Double.isFinite(candidate.weight)
                && Math.abs(candidate.weight) <= Float.MAX_VALUE
                && (double) (float) candidate.weight == candidate.weight;
    }

    private static List<WorkerScheduler.SoundScoreResult> selectResults(
            List<WorkerScheduler.SoundScoreRequest> requests,
            List<List<Entry>> groups,
            float[] scores) {
        if (scores == null) {
            return Collections.emptyList();
        }
        int expectedScores = 0;
        for (List<Entry> group : groups) {
            expectedScores += group.size();
        }
        if (scores.length != expectedScores) {
            return Collections.emptyList();
        }
        for (List<Entry> group : groups) {
            for (Entry entry : group) {
                if (entry.index < 0 || entry.index >= scores.length) {
                    return Collections.emptyList();
                }
                if (!Float.isFinite(scores[entry.index])) {
                    return Collections.emptyList();
                }
            }
        }
        List<WorkerScheduler.SoundScoreResult> results = new ArrayList<>(requests.size());
        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            WorkerScheduler.SoundScoreRequest request = requests.get(requestIndex);
            String currentId = null;
            Double currentScore = null;
            String bestId = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            double bestDistance = Double.MAX_VALUE;
            for (Entry entry : groups.get(requestIndex)) {
                if (!entry.valid) {
                    continue;
                }
                double score = scores[entry.index];
                if (request.currentTargetSoundId != null
                        && request.currentTargetSoundId.equals(entry.candidate.soundId)) {
                    currentId = entry.candidate.soundId;
                    currentScore = score;
                }
                if (score > bestScore || (Math.abs(score - bestScore) < 0.001 && entry.distance < bestDistance)) {
                    bestScore = score;
                    bestId = entry.candidate.soundId;
                    bestDistance = entry.distance;
                }
            }
            if (currentScore != null && currentId != null && bestId != null
                    && !currentId.equals(bestId) && bestScore < currentScore * request.switchRatio) {
                bestId = currentId;
                bestScore = currentScore;
            }
            results.add(new WorkerScheduler.SoundScoreResult(
                    request.mobUuid, bestId,
                    bestScore == Double.NEGATIVE_INFINITY ? 0.0 : bestScore,
                    request.dimensionKey));
        }
        return results;
    }

    private static final class Entry {
        private final WorkerScheduler.SoundCandidate candidate;
        private final double distance;
        private final float weight;
        private final float novelty;
        private final boolean valid;
        private int index;

        private Entry(WorkerScheduler.SoundCandidate candidate, double distance, float weight, float novelty, boolean valid) {
            this.candidate = candidate;
            this.distance = distance;
            this.weight = weight;
            this.novelty = novelty;
            this.valid = valid;
        }
    }

    private static final class CancellableResult<T> extends CompletableFuture<T> {
        private volatile CompletableFuture<?> source;

        private void setSource(CompletableFuture<?> source) {
            this.source = source;
            if (isCancelled() && source != null) {
                source.cancel(false);
            }
        }

        private void clearSource() {
            this.source = null;
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
}
