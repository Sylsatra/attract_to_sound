package com.example.soundattract.worker;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.resources.Identifier;

public final class QuantifiedWorkScheduler implements SoundAttractWorkScheduler {
    private final BlockingQueue<WorkerScheduler.GroupComputeResult> groupResults = new LinkedBlockingQueue<>();
    private final BlockingQueue<WorkerScheduler.SoundScoreResult> soundResults = new LinkedBlockingQueue<>();

    private final LocalWorkScheduler fallback = new LocalWorkScheduler();

    private final Method register;
    private final Method submit;

    public QuantifiedWorkScheduler() {
        try {
            Class<?> api = Class.forName("org.admany.quantified.api.QuantifiedAPI");
            this.register = api.getMethod("register", String.class);
            this.submit = api.getMethod("submit", String.class, java.util.function.Supplier.class);
            this.register.invoke(null, SoundAttractMod.MOD_ID);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public Future<?> submitGroupCompute(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, Identifier dimension) {
        long computedDeadline = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computedDeadline = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        final long deadlineMs = computedDeadline;

        CompletableFuture<?> future = submitFuture("soundattract_group_compute", () -> WorkerComputations.computeGroups(mobs, cfg, deadlineMs, dimension));
        if (future.isCompletedExceptionally()) {
            return this.fallback.submitGroupCompute(mobs, cfg, dimension);
        }
        return future.handle((result, throwable) -> {
            if (throwable != null) {
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

        long computedDeadline = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computedDeadline = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        final long deadlineMs = computedDeadline;

        CompletableFuture<?> future = submitFuture("soundattract_sound_score", () -> WorkerComputations.computeSoundScores(batch, deadlineMs));
        if (future.isCompletedExceptionally()) {
            return this.fallback.submitSoundScore(batch);
        }
        return future.handle((result, throwable) -> {
            if (throwable != null) {
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

    private CompletableFuture<?> submitFuture(String taskName, java.util.function.Supplier<?> work) {
        try {
            Object future = this.submit.invoke(null, taskName, work);
            if (future instanceof CompletableFuture<?> cf) {
                return cf;
            }
            if (future instanceof Future<?> f) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable t) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(t);
            return failed;
        }
    }
}
