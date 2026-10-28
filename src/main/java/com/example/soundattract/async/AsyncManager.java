package com.example.soundattract.async;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class AsyncManager {

    public enum Priority {
        HIGH,
        LOW
    }

    private static final Queue<Runnable> MAIN_THREAD_QUEUE = new ConcurrentLinkedQueue<>();
    private static final ExecutorService FALLBACK_EXECUTOR = Executors.newFixedThreadPool(
        Math.max(2, Integer.getInteger("soundattract.async.fallbackThreads",
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2))),
        newNamedDaemonFactory("SoundAttract-Async")
    );

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
        newNamedDaemonFactory("SoundAttract-Timer")
    );

    private AsyncManager() {}

    public static boolean isQuantifiedAvailable() {
        if (!ModList.get().isLoaded("quantified")) return false;
        try {
            return SoundAttractConfig.COMMON.enableQuantifiedIntegration.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static <T> CompletableFuture<T> submit(String taskName, Supplier<T> supplier, Priority priority, boolean threadSafe) {
        if (supplier == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (isQuantifiedAvailable()) {
            CompletableFuture<T> quantifiedFuture = QuantifiedOptionalBridge.trySubmitTask(
                SoundAttractMod.MOD_ID,
                taskName,
                supplier,
                threadSafe,
                priority == Priority.HIGH,
                null,
                taskName
            );
            if (quantifiedFuture != null) {
                return quantifiedFuture;
            }
            SoundAttractMod.LOGGER.debug("[AsyncManager] Quantified submit bridge unavailable or failed for '{}'", taskName);
        }
        return CompletableFuture.supplyAsync(supplier, FALLBACK_EXECUTOR);
    }

    public static CompletableFuture<Void> submit(String taskName, Runnable task, Priority priority, boolean threadSafe) {
        return submit(taskName, () -> {
            task.run();
            return null;
        }, priority, threadSafe);
    }

    public static <T> CompletableFuture<T> runLater(String taskName, Supplier<T> supplier, long delayMs, Priority priority, boolean threadSafe) {
        CompletableFuture<T> future = new CompletableFuture<>();
        long effectiveDelay = Math.max(0L, delayMs);
        TIMER.schedule(() -> {
            try {
                submit(taskName, supplier, priority, threadSafe)
                    .whenComplete((value, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(value);
                        }
                    });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, effectiveDelay, TimeUnit.MILLISECONDS);
        return future;
    }

    public static void syncToMain(Runnable action) {
        if (action == null) return;
        MAIN_THREAD_QUEUE.add(action);
    }

    public static <T> CompletableFuture<T> callOnMain(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        syncToMain(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (MAIN_THREAD_QUEUE.isEmpty()) return;
        drainBudgeted(event.getServer());
    }

    private static void drainBudgeted(MinecraftServer server) {
        if (server == null) return;
        if (MAIN_THREAD_QUEUE.isEmpty()) return;
        final long budgetNs = TimeUnit.MICROSECONDS.toNanos(Long.getLong("soundattract.mainqueue.budget_us", 500L));
        final int maxTasks = Math.max(1, Integer.getInteger("soundattract.mainqueue.max_tasks", 8));

        long start = System.nanoTime();
        int ran = 0;
        Runnable action;
        while (ran < maxTasks && (action = MAIN_THREAD_QUEUE.poll()) != null) {
            try {
                action.run();
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[AsyncManager] Main thread action failed", t);
            }
            ran++;
            if ((System.nanoTime() - start) >= budgetNs) {
                break;
            }
        }
    }

    private static ThreadFactory newNamedDaemonFactory(String prefix) {
        AtomicInteger idx = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + idx.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
