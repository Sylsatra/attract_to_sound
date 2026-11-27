package com.example.soundattract.async;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class AsyncManager {
    public enum Priority {
        HIGH,
        LOW
    }

    private static final int MAIN_THREAD_QUEUE_CAPACITY = 4096;
    private static final int DELAYED_QUEUE_CAPACITY = 4096;
    private static final int MAX_PENDING_COMPUTE_FUTURES = 512;
    private static final long MAX_DELAY_NANOS = Long.MAX_VALUE / 4;
    private static final Object WORK_LIFECYCLE_LOCK = new Object();
    private static final BlockingQueue<Runnable> MAIN_THREAD_QUEUE = new ArrayBlockingQueue<>(MAIN_THREAD_QUEUE_CAPACITY);
    private static final PriorityQueue<DelayedTask<?>> DELAYED_TASKS = new PriorityQueue<>(
            (left, right) -> Long.compare(left.dueNanos() - right.dueNanos(), 0L));
    private static final Set<CompletableFuture<?>> PENDING_MAIN_FUTURES = new HashSet<>();
    private static final Set<CompletableFuture<?>> PENDING_DELAYED_FUTURES = new HashSet<>();
    private static final Set<CompletableFuture<?>> PENDING_COMPUTE_FUTURES = ConcurrentHashMap.newKeySet();

    private static boolean acceptingWork = true;
    private static MinecraftServer server;
    private static ThreadPoolExecutor fallbackExecutor;

    private AsyncManager() {}

    public static boolean isQuantifiedAvailable() {
        if (!ModList.get().isLoaded("quantified")) return false;
        try {
            return SoundAttractConfig.COMMON.enableQuantifiedIntegration.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean canAcceptCompute() {
        synchronized (WORK_LIFECYCLE_LOCK) {
            return acceptingWork && PENDING_COMPUTE_FUTURES.size() < MAX_PENDING_COMPUTE_FUTURES;
        }
    }

    public static <T> CompletableFuture<T> submit(String taskName, Supplier<T> supplier, Priority priority, boolean threadSafe) {
        if (supplier == null) return CompletableFuture.completedFuture(null);
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (!acceptingWork) {
                throw new RejectedExecutionException("SoundAttract server is stopped");
            }
            if (PENDING_COMPUTE_FUTURES.size() >= MAX_PENDING_COMPUTE_FUTURES) {
                throw new RejectedExecutionException("SoundAttract compute queue is full");
            }

            CompletableFuture<T> future = null;
            if (isQuantifiedAvailable()) {
                future = QuantifiedOptionalBridge.trySubmitTask(
                        SoundAttractMod.MOD_ID, taskName, supplier, threadSafe, priority == Priority.HIGH, null, taskName);
                if (future == null && DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.WORKERS)) {
                    SoundAttractMod.LOGGER.debug("[AsyncManager] Quantified submit bridge unavailable or failed for '{}'", taskName);
                }
            }
            return trackCompute(future == null ? submitFallback(supplier) : future);
        }
    }

    public static CompletableFuture<Void> submit(String taskName, Runnable task, Priority priority, boolean threadSafe) {
        return submit(taskName, () -> {
            task.run();
            return null;
        }, priority, threadSafe);
    }

    private static <T> CompletableFuture<T> trackCompute(CompletableFuture<T> future) {
        PENDING_COMPUTE_FUTURES.add(future);
        future.whenComplete((value, error) -> PENDING_COMPUTE_FUTURES.remove(future));
        return future;
    }

    private static <T> CompletableFuture<T> submitFallback(Supplier<T> supplier) {
        if (fallbackExecutor == null) {
            int threads = Math.max(2, Integer.getInteger("soundattract.async.fallbackThreads",
                    Math.max(2, Runtime.getRuntime().availableProcessors() / 2)));
            AtomicInteger threadId = new AtomicInteger();
            fallbackExecutor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_PENDING_COMPUTE_FUTURES), runnable -> {
                        Thread thread = new Thread(runnable, "SoundAttract-Async-" + threadId.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    });
        }
        ThreadPoolExecutor executor = fallbackExecutor;
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable action = () -> {
            if (future.isDone()) return;
            try {
                future.complete(supplier.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        };
        executor.execute(action);
        future.whenComplete((value, error) -> executor.remove(action));
        return future;
    }

    public static <T> CompletableFuture<T> runLater(String taskName, Supplier<T> supplier, long delayMs, Priority priority,
                                                    boolean threadSafe) {
        if (supplier == null) return CompletableFuture.completedFuture(null);
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (!acceptingWork) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("SoundAttract server is stopped"));
            }
            if (PENDING_DELAYED_FUTURES.size() >= DELAYED_QUEUE_CAPACITY) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("SoundAttract delayed queue is full"));
            }
            DelayedFuture<T> future = new DelayedFuture<>();
            long delayNanos = Math.min(MAX_DELAY_NANOS, TimeUnit.MILLISECONDS.toNanos(Math.max(0L, delayMs)));
            DelayedTask<T> task = new DelayedTask<>(System.nanoTime() + delayNanos, taskName, supplier, priority, threadSafe, future);
            PENDING_DELAYED_FUTURES.add(future);
            DELAYED_TASKS.offer(task);
            future.whenComplete((value, error) -> {
                synchronized (WORK_LIFECYCLE_LOCK) {
                    PENDING_DELAYED_FUTURES.remove(future);
                    if (!task.started) {
                        DELAYED_TASKS.remove(task);
                    }
                }
            });
            return future;
        }
    }

    public static boolean syncToMain(Runnable action) {
        if (action == null) return false;
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (!acceptingWork) return false;
            if (MAIN_THREAD_QUEUE.offer(action)) return true;
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.WORKERS)) {
                SoundAttractMod.LOGGER.debug("[AsyncManager] Main-thread queue is full; dropping deferred action");
            }
            return false;
        }
    }

    public static <T> CompletableFuture<T> callOnMain(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable action = () -> {
            if (future.isDone()) return;
            try {
                future.complete(supplier.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        };
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (!acceptingWork) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("SoundAttract server is stopped"));
            }
            PENDING_MAIN_FUTURES.add(future);
            future.whenComplete((value, error) -> {
                synchronized (WORK_LIFECYCLE_LOCK) {
                    PENDING_MAIN_FUTURES.remove(future);
                    if (future.isCancelled()) {
                        MAIN_THREAD_QUEUE.remove(action);
                    }
                }
            });
            if (!MAIN_THREAD_QUEUE.offer(action)) {
                future.completeExceptionally(new RejectedExecutionException("SoundAttract main-thread queue is full"));
            }
        }
        return future;
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        synchronized (WORK_LIFECYCLE_LOCK) {
            server = event.getServer();
            acceptingWork = true;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Set<CompletableFuture<?>> mainFutures;
        Set<CompletableFuture<?>> delayedFutures;
        Set<CompletableFuture<?>> computeFutures;
        ThreadPoolExecutor executor;
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (server != null && server != event.getServer()) return;
            acceptingWork = false;
            server = null;
            MAIN_THREAD_QUEUE.clear();
            DELAYED_TASKS.clear();
            mainFutures = Set.copyOf(PENDING_MAIN_FUTURES);
            delayedFutures = Set.copyOf(PENDING_DELAYED_FUTURES);
            computeFutures = Set.copyOf(PENDING_COMPUTE_FUTURES);
            PENDING_MAIN_FUTURES.clear();
            PENDING_DELAYED_FUTURES.clear();
            PENDING_COMPUTE_FUTURES.clear();
            executor = fallbackExecutor;
            fallbackExecutor = null;
        }
        RejectedExecutionException stopped = new RejectedExecutionException("SoundAttract server stopped");
        mainFutures.forEach(future -> future.completeExceptionally(stopped));
        delayedFutures.forEach(future -> future.completeExceptionally(stopped));
        computeFutures.forEach(future -> future.cancel(false));
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (server != event.getServer() || !acceptingWork) return;
        }
        processDelayedTasks();
        drainBudgeted();
    }

    private static void processDelayedTasks() {
        long now = System.nanoTime();
        for (int processed = 0; processed < DELAYED_QUEUE_CAPACITY; processed++) {
            DelayedTask<?> task;
            synchronized (WORK_LIFECYCLE_LOCK) {
                task = DELAYED_TASKS.peek();
                if (task == null || now - task.dueNanos() < 0) return;
                DELAYED_TASKS.remove();
                task.started = true;
            }
            task.start();
        }
    }

    private static void drainBudgeted() {
        if (MAIN_THREAD_QUEUE.isEmpty()) return;
        long budgetNs = TimeUnit.MICROSECONDS.toNanos(Long.getLong("soundattract.mainqueue.budget_us", 500L));
        int maxTasks = Math.max(1, Integer.getInteger("soundattract.mainqueue.max_tasks", 8));
        long start = System.nanoTime();
        for (int ran = 0; ran < maxTasks; ran++) {
            Runnable action = MAIN_THREAD_QUEUE.poll();
            if (action == null) return;
            try {
                action.run();
            } catch (Throwable failure) {
                SoundAttractMod.LOGGER.error("[AsyncManager] Main thread action failed", failure);
            }
            if (System.nanoTime() - start >= budgetNs) return;
        }
    }

    private static final class DelayedFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<CompletableFuture<T>> compute = new AtomicReference<>();

        private void setCompute(CompletableFuture<T> task) {
            compute.set(task);
            if (isDone()) task.cancel(false);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!super.cancel(mayInterruptIfRunning)) return false;
            CompletableFuture<T> task = compute.get();
            if (task != null) task.cancel(mayInterruptIfRunning);
            return true;
        }
    }

    private static final class DelayedTask<T> {
        private final long dueNanos;
        private final String taskName;
        private final Supplier<T> supplier;
        private final Priority priority;
        private final boolean threadSafe;
        private final DelayedFuture<T> future;
        private boolean started;

        private DelayedTask(long dueNanos, String taskName, Supplier<T> supplier, Priority priority,
                            boolean threadSafe, DelayedFuture<T> future) {
            this.dueNanos = dueNanos;
            this.taskName = taskName;
            this.supplier = supplier;
            this.priority = priority;
            this.threadSafe = threadSafe;
            this.future = future;
        }

        private long dueNanos() {
            return dueNanos;
        }

        private void start() {
            if (future.isDone()) return;
            try {
                CompletableFuture<T> task = submit(taskName, supplier, priority, threadSafe);
                future.setCompute(task);
                task.whenComplete((value, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                    } else {
                        future.complete(value);
                    }
                });
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        }
    }
}
