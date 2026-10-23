package com.example.soundattract.worker;

import java.util.List;
import java.util.concurrent.Future;

import net.minecraft.resources.Identifier;

public final class LocalWorkScheduler implements SoundAttractWorkScheduler {
    @Override
    public Future<?> submitGroupCompute(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, Identifier dimension) {
        return WorkerScheduler.submitGroupCompute(mobs, cfg, dimension);
    }

    @Override
    public Future<?> submitSoundScore(List<WorkerScheduler.SoundScoreRequest> batch) {
        return WorkerScheduler.submitSoundScore(batch);
    }

    @Override
    public List<WorkerScheduler.GroupComputeResult> drainGroupResults() {
        return WorkerScheduler.drainGroupResults();
    }

    @Override
    public List<WorkerScheduler.SoundScoreResult> drainSoundScoreResults() {
        return WorkerScheduler.drainSoundScoreResults();
    }
}
