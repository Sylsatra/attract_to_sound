package com.example.soundattract.worker;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.quantified.QuantifiedWorkScheduler;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.RejectedExecutionException;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorkSchedulerManager {
    private static volatile SoundAttractWorkScheduler INSTANCE;
    private static volatile boolean ACCEPTING = true;
    private WorkSchedulerManager() {}

    public static SoundAttractWorkScheduler get() {
        if (!ACCEPTING) {
            throw new RejectedExecutionException("SoundAttract worker scheduler is stopped");
        }
        SoundAttractWorkScheduler local = INSTANCE;
        if (local != null) return local;
        synchronized (WorkSchedulerManager.class) {
            if (!ACCEPTING) {
                throw new RejectedExecutionException("SoundAttract worker scheduler is stopped");
            }
            if (INSTANCE != null) return INSTANCE;
            INSTANCE = build();
            return INSTANCE;
        }
    }

    public static void refresh() {
        synchronized (WorkSchedulerManager.class) {
            if (!ACCEPTING) {
                return;
            }
            if (INSTANCE != null) {
                INSTANCE.clearResults();
            }
            INSTANCE = build();
        }
    }

    public static void flushPendingSoundScores() {
        SoundAttractWorkScheduler local = INSTANCE;
        if (local != null) {
            local.flushPendingSoundScores();
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        synchronized (WorkSchedulerManager.class) {




            if (INSTANCE != null) {
                try {
                    INSTANCE.clearResults();
                } catch (Throwable ignored) {
                }
                INSTANCE = null;
            }
            ACCEPTING = true;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (WorkSchedulerManager.class) {
            ACCEPTING = false;
            if (INSTANCE != null) {
                INSTANCE.clearResults();
            }
            INSTANCE = null;
        }
    }

    private static SoundAttractWorkScheduler build() {
        if (!ModList.get().isLoaded("quantified")) {
            throw new IllegalStateException("SoundAttract requires Quantified API 2.x to schedule worker tasks");
        }
        try {
            return new QuantifiedWorkScheduler();
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.error("[WorkSchedulerManager] Quantified API failed to initialize", t);
            throw new IllegalStateException("Quantified API initialization failed", t);
        }
    }
}