package com.example.soundattract.worker;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.QuantifiedWorkScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.concurrent.RejectedExecutionException;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class WorkSchedulerManager {
    private static volatile QuantifiedWorkScheduler instance;
    private static volatile boolean accepting = true;
    private static volatile MinecraftServer activeServer;

    private WorkSchedulerManager() {}

    public static SoundAttractWorkScheduler get() {
        if (!accepting) throw stopped();
        QuantifiedWorkScheduler local = instance;
        if (local != null) return local;
        synchronized (WorkSchedulerManager.class) {
            if (!accepting) throw stopped();
            if (instance == null) instance = build();
            return instance;
        }
    }

    public static void refresh() {
        synchronized (WorkSchedulerManager.class) {
            if (!accepting) return;
            clearCurrent();
            instance = build();
        }
    }

    public static void flushPendingSoundScores() {
        QuantifiedWorkScheduler local = instance;
        if (accepting && local != null) local.flushPendingSoundScores();
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        synchronized (WorkSchedulerManager.class) {
            clearCurrent();
            activeServer = event.getServer();
            accepting = true;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        QuantifiedWorkScheduler local = instance;
        if (local != null && event.getLevel() instanceof ServerLevel level && level.getServer() == activeServer) {
            local.clearDimension(level.dimension().location().toString());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (WorkSchedulerManager.class) {
            if (activeServer != null && event.getServer() != activeServer) return;
            accepting = false;
            activeServer = null;
            clearCurrent();
        }
    }

    private static void clearCurrent() {
        QuantifiedWorkScheduler previous = instance;
        instance = null;
        if (previous != null) previous.clearResults();
        WorkerScheduler.clearResults();
    }

    private static RejectedExecutionException stopped() {
        return new RejectedExecutionException("SoundAttract worker scheduler is stopped");
    }

    private static QuantifiedWorkScheduler build() {
        boolean quantifiedLoaded = ModList.get().isLoaded("quantified");
        boolean enableQuantifiedIntegration = true;
        try {
            enableQuantifiedIntegration = SoundAttractConfig.COMMON.enableQuantifiedIntegration.get();
        } catch (Throwable ignored) {
        }

        if (quantifiedLoaded && enableQuantifiedIntegration) {
            try {
                return new QuantifiedWorkScheduler();
            } catch (RuntimeException | LinkageError failure) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.WORKERS)) {
                    SoundAttractMod.LOGGER.warn("[WorkSchedulerManager] Quantified unavailable, using local workers", failure);
                }
            }
        }

        return QuantifiedWorkScheduler.local();
    }
}
