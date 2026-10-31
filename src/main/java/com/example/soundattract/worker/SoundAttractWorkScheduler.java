package com.example.soundattract.worker;

import java.util.List;
import java.util.concurrent.Future;

import net.minecraft.resources.Identifier;

public interface SoundAttractWorkScheduler {
    Future<?> submitGroupCompute(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, Identifier dimension);

    Future<?> submitSoundScore(List<WorkerScheduler.SoundScoreRequest> batch);

    List<WorkerScheduler.GroupComputeResult> drainGroupResults();

    List<WorkerScheduler.SoundScoreResult> drainSoundScoreResults();
}
