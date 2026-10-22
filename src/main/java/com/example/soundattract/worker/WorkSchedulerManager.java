package com.example.soundattract.worker;

import com.example.soundattract.quantified.QuantifiedWorkScheduler;

public final class WorkSchedulerManager {
    private static volatile SoundAttractWorkScheduler INSTANCE;
    private WorkSchedulerManager() {}

    public static SoundAttractWorkScheduler get() {
        SoundAttractWorkScheduler local = INSTANCE;
        if (local != null) return local;
        synchronized (WorkSchedulerManager.class) {
            if (INSTANCE != null) return INSTANCE;
            INSTANCE = build();
            return INSTANCE;
        }
    }

    public static void refresh() {
        synchronized (WorkSchedulerManager.class) {
            if (INSTANCE != null) INSTANCE.clearResults();
            INSTANCE = build();
        }
    }

    public static void clearResults() {
        synchronized (WorkSchedulerManager.class) {
            if (INSTANCE != null) INSTANCE.clearResults();
            INSTANCE = null;
            WorkerScheduler.clearResults();
        }
    }

    public static void flushPendingSoundScores() {
        SoundAttractWorkScheduler scheduler = INSTANCE;
        if (scheduler != null) scheduler.flushPendingSoundScores();
    }

    private static SoundAttractWorkScheduler build() {
        return new QuantifiedWorkScheduler();
    }
}
