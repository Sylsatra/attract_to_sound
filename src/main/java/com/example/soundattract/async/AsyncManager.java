package com.example.soundattract.async;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.loader.api.FabricLoader;

public final class AsyncManager {

    public enum Priority {
        HIGH,
        LOW
    }

    private static final int MAIN_THREAD_QUEUE_CAPACITY = 4096;
    private static final int FALLBACK_QUEUE_CAPACITY = 1024;
    private static final Queue<Runnable> MAIN_THREAD_QUEUE = new ArrayBlockingQueue<>(MAIN_THREAD_QUEUE_CAPACITY);
    private static final Object MAIN_THREAD_LIFECYCLE_LOCK = new Object();
    private static final AtomicBoolean DRAIN_SCHEDULED = new AtomicBoolean(false);
    private static volatile MinecraftServer serverRef;

    private static final ExecutorService FALLBACK_EXECUTOR = createFallbackExecutor();

    private static final ScheduledExecutorService TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
        newNamedDaemonFactory("SoundAttract-Timer"));

    private AsyncManager() {}

    public static boolean isQuantifiedAvailable() {
        if (!FabricLoader.getInstance().isModLoaded("quantified")) return false;
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
                Soundattract.MOD_ID,
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
            Soundattract.LOGGER.debug("[AsyncManager] Quantified submit bridge unavailable or failed for '{}'", taskName);
        }
        try {
            return CompletableFuture.supplyAsync(supplier, FALLBACK_EXECUTOR);
        } catch (RejectedExecutionException rejected) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(rejected);
            return failed;
        }
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
        if (!enqueueMainThreadAction(action)) {
            Soundattract.LOGGER.warn("[AsyncManager] Main-thread queue is full; dropping a fire-and-forget action");
        }
    }

    private static boolean enqueueMainThreadAction(Runnable action) {
        MinecraftServer server;
        synchronized (MAIN_THREAD_LIFECYCLE_LOCK) {
            server = serverRef;
            if (server == null || !MAIN_THREAD_QUEUE.offer(action)) return false;
        }
        if (server != null) {
            scheduleBudgetedDrain(server);
        }
        return true;
    }

    public static <T> CompletableFuture<T> callOnMain(Supplier<T> supplier) {
        MainThreadCall<T> call = new MainThreadCall<>(supplier);
        if (!enqueueMainThreadAction(call)) {
            call.reject(new RejectedExecutionException("SoundAttract main-thread queue is full"));
        }
        return call.future;
    }

    public static void onServerTick(MinecraftServer server) {
        synchronized (MAIN_THREAD_LIFECYCLE_LOCK) {
            serverRef = server;
        }
        if (MAIN_THREAD_QUEUE.isEmpty()) return;
        drainBudgeted(server);
    }

    public static void onServerStopped() {
        synchronized (MAIN_THREAD_LIFECYCLE_LOCK) {
            serverRef = null;
            DRAIN_SCHEDULED.set(false);
            Runnable action;
            while ((action = MAIN_THREAD_QUEUE.poll()) != null) {
                if (action instanceof MainThreadCall<?> call) {
                    call.reject(new RejectedExecutionException("SoundAttract server stopped"));
                }
            }
        }
    }

    private static void scheduleBudgetedDrain(MinecraftServer server) {
        if (server == null) return;
        if (!DRAIN_SCHEDULED.compareAndSet(false, true)) return;
        try {
            server.execute(() -> {
                DRAIN_SCHEDULED.set(false);
                drainBudgeted(server);
                if (!MAIN_THREAD_QUEUE.isEmpty()) {
                    scheduleBudgetedDrain(server);
                }
            });
        } catch (Throwable ignored) {
            DRAIN_SCHEDULED.set(false);
        }
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
                Soundattract.LOGGER.error("[AsyncManager] Main thread action failed", t);
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

    private static ExecutorService createFallbackExecutor() {
        int requested = Integer.getInteger("soundattract.async.fallbackThreads",
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        int threads = Math.max(2, Math.min(32, requested));
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(FALLBACK_QUEUE_CAPACITY),
                newNamedDaemonFactory("SoundAttract-Async"), new ThreadPoolExecutor.AbortPolicy());
    }

    private static final class MainThreadCall<T> implements Runnable {
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future = new CompletableFuture<>();

        private MainThreadCall(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void run() {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }

        private void reject(Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }
}
