package com.example.soundattract.async;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import org.admany.quantified.api.ComputeRequest;
import org.admany.quantified.api.QuantifiedAPI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AsyncManager {

    public enum Priority {
        HIGH,
        LOW
    }

    private static final int MAIN_THREAD_QUEUE_CAPACITY = 4096;
    private static final int MAX_PENDING_COMPUTE_FUTURES = 512;
    private static final BlockingQueue<Runnable> MAIN_THREAD_QUEUE = new ArrayBlockingQueue<>(MAIN_THREAD_QUEUE_CAPACITY);
    private static final Set<CompletableFuture<?>> PENDING_MAIN_FUTURES = ConcurrentHashMap.newKeySet();
    private static final Set<CompletableFuture<?>> PENDING_DELAYED_FUTURES = ConcurrentHashMap.newKeySet();
    private static final Set<CompletableFuture<?>> PENDING_COMPUTE_FUTURES = ConcurrentHashMap.newKeySet();
    private static final int DELAYED_QUEUE_CAPACITY = 4096;
    private static final BlockingQueue<DelayedTask<?>> DELAYED_TASKS = new ArrayBlockingQueue<>(DELAYED_QUEUE_CAPACITY);
    private static final Object WORK_LIFECYCLE_LOCK = new Object();
    private static final AtomicBoolean ACCEPTING_WORK = new AtomicBoolean(true);
    private static volatile MinecraftServer serverRef;
    private static volatile MinecraftServer stoppedServer;

    private AsyncManager() {}

    public static boolean isQuantifiedAvailable() {
        return ModList.get().isLoaded("quantified");
    }

    public static <T> CompletableFuture<T> submit(String taskName, Supplier<T> supplier, Priority priority, boolean threadSafe) {
        if (supplier == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!isQuantifiedAvailable()) {
            throw new IllegalStateException("SoundAttract requires Quantified API for asynchronous work");
        }
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (!ACCEPTING_WORK.get()) {
                throw new RejectedExecutionException("SoundAttract server is stopped");
            }
            if (PENDING_COMPUTE_FUTURES.size() >= MAX_PENDING_COMPUTE_FUTURES) {
                throw new RejectedExecutionException("SoundAttract compute queue is full");
            }
            ComputeRequest<T> request = QuantifiedAPI.<T>compute(SoundAttractMod.MOD_ID, taskName)
                .key(taskName)
                .work(supplier)
                .allowMainThreadRerouting(false);
            if (threadSafe) {
                request.threadSafe();
            } else {
                request.notThreadSafe();
            }
            if (priority == Priority.HIGH) {
                request.foreground();
            } else {
                request.background();
            }
            CompletableFuture<T> future = request.submit();
            if (future == null) {
                throw new RejectedExecutionException("Quantified API returned no future");
            }
            future.whenComplete((value, throwable) -> PENDING_COMPUTE_FUTURES.remove(future));
            PENDING_COMPUTE_FUTURES.add(future);
            if (future.isDone()) {
                PENDING_COMPUTE_FUTURES.remove(future);
            }
            return future;
        }
    }

    public static CompletableFuture<Void> submit(String taskName, Runnable task, Priority priority, boolean threadSafe) {
        return submit(taskName, () -> {
            task.run();
            return null;
        }, priority, threadSafe);
    }

    public static <T> CompletableFuture<T> runLater(String taskName, Supplier<T> supplier, long delayMs, Priority priority, boolean threadSafe) {
        DelayedFuture<T> future = new DelayedFuture<>();
        if (supplier == null) {
            future.complete(null);
            return future;
        }
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (!ACCEPTING_WORK.get()) {
                future.completeExceptionally(new RejectedExecutionException("SoundAttract server is stopped"));
                return future;
            }
            if (PENDING_DELAYED_FUTURES.size() >= DELAYED_QUEUE_CAPACITY) {
                future.completeExceptionally(new RejectedExecutionException("SoundAttract delayed queue is full"));
                return future;
            }
            PENDING_DELAYED_FUTURES.add(future);
            long delayNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, delayMs));
            DelayedTask<T> delayedTask = new DelayedTask<>(System.nanoTime() + delayNanos, taskName, supplier, priority, threadSafe, future);
            if (!DELAYED_TASKS.offer(delayedTask)) {
                PENDING_DELAYED_FUTURES.remove(future);
                future.completeExceptionally(new RejectedExecutionException("SoundAttract delayed queue is full"));
            }
        }
        return future;
    }

    public static boolean syncToMain(Runnable action) {
        if (action == null) return false;
        synchronized (WORK_LIFECYCLE_LOCK) {
            if (!ACCEPTING_WORK.get()) return false;
            synchronized (MAIN_THREAD_QUEUE) {
                if (!MAIN_THREAD_QUEUE.offer(action)) {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.WORKERS)) {
                        SoundAttractMod.LOGGER.debug("[AsyncManager] Main-thread queue is full; dropping deferred action");
                    }
                    return false;
                }
            }
            return true;
        }
    }

    public static <T> CompletableFuture<T> callOnMain(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        PENDING_MAIN_FUTURES.add(future);
        boolean accepted = syncToMain(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                PENDING_MAIN_FUTURES.remove(future);
            }
        });
        if (!accepted) {
            PENDING_MAIN_FUTURES.remove(future);
            future.completeExceptionally(new RejectedExecutionException("SoundAttract main-thread queue is full"));
        }
        return future;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Set<CompletableFuture<?>> pendingMain;
        Set<CompletableFuture<?>> pendingDelayed;
        Set<CompletableFuture<?>> pendingCompute;
        synchronized (WORK_LIFECYCLE_LOCK) {
            stoppedServer = event == null ? serverRef : event.getServer();
            ACCEPTING_WORK.set(false);
            synchronized (MAIN_THREAD_QUEUE) {
                MAIN_THREAD_QUEUE.clear();
            }
            pendingMain = Set.copyOf(PENDING_MAIN_FUTURES);
            PENDING_MAIN_FUTURES.clear();
            pendingDelayed = Set.copyOf(PENDING_DELAYED_FUTURES);
            PENDING_DELAYED_FUTURES.clear();
            pendingCompute = Set.copyOf(PENDING_COMPUTE_FUTURES);
            PENDING_COMPUTE_FUTURES.clear();
        }
        DELAYED_TASKS.clear();
        serverRef = null;
        RejectedExecutionException stopped = new RejectedExecutionException("SoundAttract server stopped");
        pendingMain.forEach(future -> future.completeExceptionally(stopped));
        pendingDelayed.forEach(future -> future.completeExceptionally(stopped));
        pendingCompute.forEach(future -> future.cancel(false));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer tickServer = event == null ? null : event.getServer();
        if (tickServer == null) return;
        synchronized (WORK_LIFECYCLE_LOCK) {
            MinecraftServer knownServer = serverRef;
            if (stoppedServer == tickServer || (knownServer != null && knownServer != tickServer)) {
                return;
            }
            stoppedServer = null;
            ACCEPTING_WORK.set(true);
            serverRef = tickServer;
        }
        if (event.phase != TickEvent.Phase.END) return;
        processDelayedTasks();
        if (MAIN_THREAD_QUEUE.isEmpty()) return;
        drainBudgeted(event.getServer());
    }

    private static void processDelayedTasks() {
        long now = System.nanoTime();
        DELAYED_TASKS.removeIf(task -> {
            if (task.future().isCancelled()) {
                PENDING_DELAYED_FUTURES.remove(task.future());
                return true;
            }
            if (task.dueNanos() > now) {
                return false;
            }
            task.start();
            return true;
        });
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

    private static final class DelayedFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<CompletableFuture<T>> compute = new AtomicReference<>();

        private void setCompute(CompletableFuture<T> task) {
            if (!compute.compareAndSet(null, task) || isCancelled()) {
                task.cancel(false);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                CompletableFuture<T> computeTask = compute.get();
                if (computeTask != null) {
                    computeTask.cancel(mayInterruptIfRunning);
                }
                PENDING_DELAYED_FUTURES.remove(this);
            }
            return cancelled;
        }
    }

    private record DelayedTask<T>(long dueNanos, String taskName, Supplier<T> supplier,
                                  Priority priority, boolean threadSafe, DelayedFuture<T> future) {
        private void start() {
            if (future.isCancelled()) {
                PENDING_DELAYED_FUTURES.remove(future);
                return;
            }
            try {
                CompletableFuture<T> compute = submit(taskName, supplier, priority, threadSafe);
                future.setCompute(compute);
                compute.whenComplete((value, throwable) -> {
                    PENDING_DELAYED_FUTURES.remove(future);
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(value);
                    }
                });
            } catch (Throwable t) {
                PENDING_DELAYED_FUTURES.remove(future);
                future.completeExceptionally(t);
            }
        }
    }
}
