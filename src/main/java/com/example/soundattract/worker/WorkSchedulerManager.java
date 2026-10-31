package com.example.soundattract.worker;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.QuantifiedWorkScheduler;

import net.neoforged.fml.ModList;

public final class WorkSchedulerManager {
    private static volatile SoundAttractWorkScheduler INSTANCE;
    private static volatile boolean QUANTIFIED_INIT_FAILED = false;

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
            QUANTIFIED_INIT_FAILED = false;
            INSTANCE = build();
        }
    }

    private static SoundAttractWorkScheduler build() {
        boolean quantifiedLoaded = ModList.get().isLoaded("quantified");
        boolean enableQuantifiedIntegration = true;
        try {
            enableQuantifiedIntegration = SoundAttractConfig.COMMON.enableQuantifiedIntegration.get();
        } catch (Throwable ignored) {
        }

        if (quantifiedLoaded && enableQuantifiedIntegration && !QUANTIFIED_INIT_FAILED) {
            try {
                return new QuantifiedWorkScheduler();
            } catch (Throwable t) {
                QUANTIFIED_INIT_FAILED = true;
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[WorkSchedulerManager] Failed to initialize QuantifiedWorkScheduler, falling back to LocalWorkScheduler", t);
                }
            }
        }

        return new LocalWorkScheduler();
    }
}
