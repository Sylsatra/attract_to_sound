package com.example.soundattract.quantified.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuantifiedOptionalBridge {
    @FunctionalInterface
    public interface ClassResolver {
        Class<?> resolve(String className) throws Throwable;
    }

    private static final Object INIT_LOCK = new Object();
    private static volatile Handles handles;
    private static volatile ClassResolver classResolver = Class::forName;

    private QuantifiedOptionalBridge() {
    }

    public static boolean isAvailable() {
        Handles resolved = resolveHandles();
        return resolved != null && resolved.submitTaskMethod != null;
    }

    public static boolean registerV2(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.registerV2Method == null) {
            return false;
        }
        try {
            return (boolean) resolved.registerV2Method.invoke(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> trySubmitTask(String modId,
                                                          String taskName,
                                                          Supplier<T> work,
                                                          boolean threadSafe,
                                                          boolean foreground,
                                                          Duration timeout,
                                                          String batchKey) {
        if (work == null || modId == null || modId.isBlank() || taskName == null || taskName.isBlank()) {
            return null;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.taskBuilderFactory == null || resolved.submitTaskMethod == null) {
            return null;
        }
        try {
            Object builder = resolved.taskBuilderFactory.invoke(modId, taskName, work);
            if (resolved.threadSafeMethod != null) {
                resolved.threadSafeMethod.invoke(builder, threadSafe);
            }
            if (foreground) {
                if (resolved.priorityForegroundMethod != null) {
                    resolved.priorityForegroundMethod.invoke(builder);
                }
            } else if (resolved.priorityBackgroundMethod != null) {
                resolved.priorityBackgroundMethod.invoke(builder);
            }
            if (timeout != null && resolved.timeoutMethod != null) {
                resolved.timeoutMethod.invoke(builder, timeout);
            }
            if (batchKey != null && !batchKey.isBlank() && resolved.batchKeyMethod != null) {
                resolved.batchKeyMethod.invoke(builder, batchKey);
            }
            return (CompletableFuture<T>) resolved.submitTaskMethod.invoke(builder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object tryCreateParallelBuilder(String modId, String taskName, long taskKey) {
        if (modId == null || modId.isBlank() || taskName == null || taskName.isBlank()) {
            return null;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.parallelBuilderFactory == null) {
            return null;
        }
        try {
            return resolved.parallelBuilderFactory.invoke(modId, taskName, taskKey);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean trySetParallelSlices(Object builder, Supplier<?> supplier) {
        Handles resolved = resolveHandles();
        if (builder == null || supplier == null || resolved == null || resolved.parallelSlicesMethod == null) {
            return false;
        }
        try {
            resolved.parallelSlicesMethod.invoke(builder, supplier);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean trySetParallelSliceExecutor(Object builder, Function<?, ?> executor) {
        Handles resolved = resolveHandles();
        if (builder == null || executor == null || resolved == null || resolved.parallelSliceExecutorMethod == null) {
            return false;
        }
        try {
            resolved.parallelSliceExecutorMethod.invoke(builder, executor);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean trySetParallelMaxParallelism(Object builder, int maxParallelism) {
        Handles resolved = resolveHandles();
        if (builder == null || maxParallelism <= 0 || resolved == null || resolved.parallelMaxParallelismMethod == null) {
            return false;
        }
        try {
            resolved.parallelMaxParallelismMethod.invoke(builder, maxParallelism);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean trySetParallelBestEffort(Object builder) {
        Handles resolved = resolveHandles();
        if (builder == null || resolved == null || resolved.parallelFailurePolicyMethod == null || resolved.failurePolicyBestEffort == null) {
            return false;
        }
        try {
            resolved.parallelFailurePolicyMethod.invoke(builder, resolved.failurePolicyBestEffort);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static <S, R> boolean trySetParallelMemorySliceCache(Object builder,
                                                                String cacheName,
                                                                Function<S, String> keyFunction,
                                                                Function<R, byte[]> serializer,
                                                                Function<byte[], R> deserializer,
                                                                Duration ttl,
                                                                long maxEntries) {
        Handles resolved = resolveHandles();
        if (builder == null || cacheName == null || keyFunction == null || serializer == null || deserializer == null || resolved == null || resolved.parallelMemorySliceCacheMethod == null) {
            return false;
        }
        try {
            resolved.parallelMemorySliceCacheMethod.invoke(builder, cacheName, keyFunction, serializer, deserializer, ttl, maxEntries);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static <S, R> boolean trySetParallelPersistentSliceCache(Object builder,
                                                                    String cacheName,
                                                                    Function<S, String> keyFunction,
                                                                    Function<R, byte[]> serializer,
                                                                    Function<byte[], R> deserializer,
                                                                    Duration ttl,
                                                                    long maxEntries,
                                                                    boolean compression) {
        Handles resolved = resolveHandles();
        if (builder == null || cacheName == null || keyFunction == null || serializer == null || deserializer == null || resolved == null || resolved.parallelPersistentSliceCacheMethod == null) {
            return false;
        }
        try {
            resolved.parallelPersistentSliceCacheMethod.invoke(builder, cacheName, keyFunction, serializer, deserializer, ttl, maxEntries, compression);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> trySubmitParallel(Object builder) {
        Handles resolved = resolveHandles();
        if (builder == null || resolved == null || resolved.parallelSubmitMethod == null) {
            return null;
        }
        try {
            return (CompletableFuture<T>) resolved.parallelSubmitMethod.invoke(builder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object tryFetchCacheManager(String modId) {
        if (modId == null) {
            return null;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.getCacheManagerMethod == null) {
            return null;
        }
        try {
            return resolved.getCacheManagerMethod.invoke(modId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T tryGetCached(String modId,
                                     String cacheName,
                                     String key,
                                     Supplier<T> loader,
                                     Duration ttl,
                                     long maxSize,
                                     boolean persistence) {
        if (loader == null || modId == null || cacheName == null || key == null) {
            return null;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.cacheRequestFactory == null || resolved.cacheGetMethod == null) {
            return null;
        }
        try {
            Object request = resolved.cacheRequestFactory.invoke(modId, cacheName);
            if (request == null) return null;
            if (ttl != null && resolved.cacheTtlMethod != null) resolved.cacheTtlMethod.invoke(request, ttl);
            if (maxSize > 0L && resolved.cacheMaxEntriesMethod != null) resolved.cacheMaxEntriesMethod.invoke(request, maxSize);
            if (persistence && resolved.cachePersistMethod != null) resolved.cachePersistMethod.invoke(request);
            return (T) resolved.cacheGetMethod.invoke(request, key, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean trySetCacheMemoryLimit(Object manager, long limitMb) {
        Handles resolved = resolveHandles();
        if (manager == null || resolved == null || resolved.setMemoryLimitMethod == null) {
            return false;
        }
        try {
            resolved.setMemoryLimitMethod.invoke(manager, limitMb);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Boolean tryIsCacheMemoryPressureHigh(Object manager) {
        Handles resolved = resolveHandles();
        if (manager == null || resolved == null || resolved.isMemoryPressureHighMethod == null) {
            return null;
        }
        try {
            return (Boolean) resolved.isMemoryPressureHighMethod.invoke(manager);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean tryTriggerCacheCleanup(Object manager) {
        Handles resolved = resolveHandles();
        if (manager == null || resolved == null || resolved.triggerCleanupMethod == null) {
            return false;
        }
        try {
            resolved.triggerCleanupMethod.invoke(manager);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setClassResolverForTests(ClassResolver resolver) {
        classResolver = resolver == null ? Class::forName : resolver;
        handles = null;
    }

    public static void resetForTests() {
        classResolver = Class::forName;
        handles = null;
    }

    private static Handles resolveHandles() {
        Handles current = handles;
        if (current != null) {
            return current.available ? current : null;
        }
        synchronized (INIT_LOCK) {
            current = handles;
            if (current != null) {
                return current.available ? current : null;
            }
            Handles loaded = loadHandles();
            handles = loaded;
            return loaded.available ? loaded : null;
        }
    }

    private static Handles loadHandles() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> apiClass = classResolver.resolve("org.admany.quantified.api.QuantifiedAPI");
            Class<?> taskClass = classResolver.resolve("org.admany.quantified.api.model.QuantifiedTask");
            Class<?> taskBuilderClass = classResolver.resolve("org.admany.quantified.api.model.QuantifiedTask$Builder");
            Class<?> cacheRequestClass = classResolver.resolve("org.admany.quantified.api.CacheRequest");
            Class<?> parallelComputeClass = classResolver.resolve("org.admany.quantified.api.parallel.ParallelCompute");
            Class<?> parallelBuilderClass = classResolver.resolve("org.admany.quantified.api.parallel.ParallelCompute$Builder");
            Class<?> managerInterface = classResolver.resolve("org.admany.quantified.api.interfaces.ModCacheManager");

            Handles loaded = new Handles();
            loaded.available = true;
            loaded.registerV2Method = lookup.findStatic(apiClass, "registerV2", MethodType.methodType(boolean.class, String.class));
            loaded.taskBuilderFactory = lookup.findStatic(taskClass, "builder", MethodType.methodType(taskBuilderClass, String.class, String.class, Supplier.class));
            loaded.submitTaskMethod = lookup.findStatic(apiClass, "submit", MethodType.methodType(CompletableFuture.class, taskBuilderClass));
            loaded.threadSafeMethod = lookup.findVirtual(taskBuilderClass, "threadSafe", MethodType.methodType(taskBuilderClass, boolean.class));
            loaded.priorityForegroundMethod = lookup.findVirtual(taskBuilderClass, "priorityForeground", MethodType.methodType(taskBuilderClass));
            loaded.priorityBackgroundMethod = lookup.findVirtual(taskBuilderClass, "priorityBackground", MethodType.methodType(taskBuilderClass));
            loaded.timeoutMethod = lookup.findVirtual(taskBuilderClass, "timeout", MethodType.methodType(taskBuilderClass, Duration.class));
            loaded.batchKeyMethod = lookup.findVirtual(taskBuilderClass, "batchKey", MethodType.methodType(taskBuilderClass, String.class));
            loaded.parallelBuilderFactory = lookup.findStatic(parallelComputeClass, "builder", MethodType.methodType(parallelBuilderClass, String.class, String.class, long.class));
            loaded.parallelSlicesMethod = lookup.findVirtual(parallelBuilderClass, "slices", MethodType.methodType(parallelBuilderClass, Supplier.class));
            loaded.parallelSliceExecutorMethod = lookup.findVirtual(parallelBuilderClass, "sliceExecutor", MethodType.methodType(parallelBuilderClass, Function.class));
            loaded.parallelMaxParallelismMethod = lookup.findVirtual(parallelBuilderClass, "maxParallelism", MethodType.methodType(parallelBuilderClass, int.class));
            loaded.parallelMemorySliceCacheMethod = lookup.findVirtual(parallelBuilderClass, "memorySliceCache", MethodType.methodType(parallelBuilderClass, String.class, Function.class, Function.class, Function.class, Duration.class, long.class));
            loaded.parallelPersistentSliceCacheMethod = lookup.findVirtual(parallelBuilderClass, "persistentSliceCache", MethodType.methodType(parallelBuilderClass, String.class, Function.class, Function.class, Function.class, Duration.class, long.class, boolean.class));
            loaded.parallelSubmitMethod = lookup.findVirtual(parallelBuilderClass, "submit", MethodType.methodType(CompletableFuture.class));
            loaded.getCacheManagerMethod = lookup.findStatic(apiClass, "getCacheManager", MethodType.methodType(managerInterface, String.class));
            loaded.cacheRequestFactory = lookup.findStatic(apiClass, "cache", MethodType.methodType(cacheRequestClass, String.class, String.class));
            loaded.cacheTtlMethod = lookup.findVirtual(cacheRequestClass, "ttl", MethodType.methodType(cacheRequestClass, Duration.class));
            loaded.cacheMaxEntriesMethod = lookup.findVirtual(cacheRequestClass, "maxEntries", MethodType.methodType(cacheRequestClass, long.class));
            loaded.cachePersistMethod = lookup.findVirtual(cacheRequestClass, "persistent", MethodType.methodType(cacheRequestClass));
            loaded.cacheGetMethod = lookup.findVirtual(cacheRequestClass, "get", MethodType.methodType(Object.class, String.class, Supplier.class));
            loaded.setMemoryLimitMethod = lookup.findVirtual(managerInterface, "setMemoryLimitMB", MethodType.methodType(void.class, long.class));
            loaded.isMemoryPressureHighMethod = lookup.findVirtual(managerInterface, "isMemoryPressureHigh", MethodType.methodType(boolean.class));
            loaded.triggerCleanupMethod = lookup.findVirtual(managerInterface, "triggerMemoryPressureCleanup", MethodType.methodType(void.class));
            try {
                Class<?> failurePolicyClass = classResolver.resolve("org.admany.quantified.core.common.parallel.policy.ParallelFailurePolicy");
                loaded.failurePolicyBestEffort = Enum.valueOf((Class<? extends Enum>) failurePolicyClass.asSubclass(Enum.class), "BEST_EFFORT");
                loaded.parallelFailurePolicyMethod = lookup.findVirtual(parallelBuilderClass, "failurePolicy", MethodType.methodType(parallelBuilderClass, failurePolicyClass));
            } catch (Throwable ignored) {
                loaded.failurePolicyBestEffort = null;
                loaded.parallelFailurePolicyMethod = null;
            }
            return loaded;
        } catch (Throwable ignored) {
            return new Handles();
        }
    }

    private static final class Handles {
        private boolean available;
        private MethodHandle registerV2Method;
        private MethodHandle taskBuilderFactory;
        private MethodHandle submitTaskMethod;
        private MethodHandle threadSafeMethod;
        private MethodHandle priorityForegroundMethod;
        private MethodHandle priorityBackgroundMethod;
        private MethodHandle timeoutMethod;
        private MethodHandle batchKeyMethod;
        private MethodHandle parallelBuilderFactory;
        private MethodHandle parallelSlicesMethod;
        private MethodHandle parallelSliceExecutorMethod;
        private MethodHandle parallelMaxParallelismMethod;
        private MethodHandle parallelFailurePolicyMethod;
        private Object failurePolicyBestEffort;
        private MethodHandle parallelMemorySliceCacheMethod;
        private MethodHandle parallelPersistentSliceCacheMethod;
        private MethodHandle parallelSubmitMethod;
        private MethodHandle getCacheManagerMethod;
        private MethodHandle cacheRequestFactory;
        private MethodHandle cacheTtlMethod;
        private MethodHandle cacheMaxEntriesMethod;
        private MethodHandle cachePersistMethod;
        private MethodHandle cacheGetMethod;
        private MethodHandle setMemoryLimitMethod;
        private MethodHandle isMemoryPressureHighMethod;
        private MethodHandle triggerCleanupMethod;
    }
}
