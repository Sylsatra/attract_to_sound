package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;

public class PerformanceConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue INITIAL_GROUP_COMPUTATION_DELAY;
    public static final ForgeConfigSpec.IntValue WORKER_THREADS;
    public static final ForgeConfigSpec.IntValue WORKER_TASK_BUDGET_MS;
    public static final ForgeConfigSpec.IntValue SCAN_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue COOLDOWN_TICKS_PER_MOB;
    public static final ForgeConfigSpec.DoubleValue MIN_TPS_FOR_SCAN_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue MAX_TPS_FOR_SCAN_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TIERED_STEALTH_PERFORMANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SPATIAL_SCHEDULER;
    public static final ForgeConfigSpec.DoubleValue STEALTH_TIER_SKIP_EXPENSIVE_CHECKS_TPS;
    public static final ForgeConfigSpec.DoubleValue STEALTH_TIER_CURRENT_TARGETS_ONLY_TPS;
    public static final ForgeConfigSpec.DoubleValue STEALTH_TIER_SHARED_RANGE_TPS;
    public static final ForgeConfigSpec.DoubleValue STEALTH_TIER_VANILLA_TPS;
    public static final ForgeConfigSpec.DoubleValue STEALTH_SHARE_TARGET_TO_NEARBY_MOBS_RADIUS;
    public static final ForgeConfigSpec.IntValue SOUND_SCORING_SUBMIT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue ASYNC_RESULT_TTL_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_RAYCAST_CACHE;
    public static final ForgeConfigSpec.IntValue RAYCAST_CACHE_TTL_TICKS;
    public static final ForgeConfigSpec.IntValue RAYCAST_CACHE_MAX_ENTRIES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_OPTIMIZED_LOS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_OPTIMIZED_LOS_PAIR_CACHE;
    public static final ForgeConfigSpec.IntValue OPTIMIZED_LOS_PAIR_CACHE_MAX_ENTRIES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_OPTIMIZED_LOS_VANILLA_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LOS_BATCHING;
    public static final ForgeConfigSpec.IntValue LOS_BATCH_BUDGET_PER_TICK;
    public static final ForgeConfigSpec.IntValue LOS_BATCH_QUEUE_MAX_SIZE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LIVING_ENTITY_LOS_OVERRIDE;
    public static final ForgeConfigSpec.IntValue MAX_MUFFLING_BLOCKS_TO_CHECK;
    public static final ForgeConfigSpec.IntValue MAX_SOUNDS_TRACKED;
    public static final ForgeConfigSpec.IntValue GLOBAL_CACHE_MAX_SIZE;
    public static final ForgeConfigSpec.IntValue GLOBAL_CACHE_EXPIRE_MINS;

    static {
        BUILDER.comment("Performance and optimization settings").push("performance");

        MAX_SOUNDS_TRACKED = BUILDER.comment("Maximum number of sound sources tracked per world.")
                .defineInRange("maxSoundsTracked", 2048, 1, 1000000);

        INITIAL_GROUP_COMPUTATION_DELAY = BUILDER.comment("Delay in ticks before the first mob group computation is run after server startup.")
                .defineInRange("initialGroupComputationDelay", 50, 0, 72000);

        WORKER_THREADS = BUILDER.comment("Number of background worker threads used for off-thread computations (e.g., group building).")
                .defineInRange("workerThreads", 2, 1, 64);

        WORKER_TASK_BUDGET_MS = BUILDER.comment("Soft per-task time budget in milliseconds for worker computations before yielding.")
                .defineInRange("workerTaskBudgetMs", 10, 1, 1000);

        SCAN_COOLDOWN_TICKS = BUILDER.comment("Minimum time in ticks between mob scans for new sounds.")
                .defineInRange("scanCooldownTicks", 25, 1, 1000000);

        COOLDOWN_TICKS_PER_MOB = BUILDER.comment("How many ticks to add to the base scan cooldown for each active mob.")
                .defineInRange("cooldownTicksPerMob", 0.15, 0.0, 10.0);

        MIN_TPS_FOR_SCAN_COOLDOWN = BUILDER.comment("TPS below which scanCooldownTicks is dynamically increased. 0 to disable.")
                .defineInRange("minTpsForScanCooldown", 15.0, 0.0, 20.0);

        MAX_TPS_FOR_SCAN_COOLDOWN = BUILDER.comment("TPS above which scanCooldownTicks is dynamically decreased. 21 to disable.")
                .defineInRange("maxTpsForScanCooldown", 19.0, 0.0, 21.0);

        ENABLE_TIERED_STEALTH_PERFORMANCE = BUILDER.comment("Enable tiered stealth performance scaling based on TPS.")
                .define("enableTieredStealthPerformance", true);
        ENABLE_SPATIAL_SCHEDULER = BUILDER.comment("Maintain the optional server-side spatial entity index used by group and interest queries.")
                .define("enableSpatialScheduler", true);

        STEALTH_TIER_SKIP_EXPENSIVE_CHECKS_TPS = BUILDER.defineInRange("stealthTierSkipExpensiveChecksTps", 19.0, 0.0, 21.0);
        STEALTH_TIER_CURRENT_TARGETS_ONLY_TPS = BUILDER.defineInRange("stealthTierCurrentTargetsOnlyTps", 18.0, 0.0, 21.0);
        STEALTH_TIER_SHARED_RANGE_TPS = BUILDER.defineInRange("stealthTierSharedRangeTps", 17.0, 0.0, 21.0);
        STEALTH_TIER_VANILLA_TPS = BUILDER.defineInRange("stealthTierVanillaTps", 15.0, 0.0, 21.0);
        STEALTH_SHARE_TARGET_TO_NEARBY_MOBS_RADIUS = BUILDER.defineInRange("stealthShareTargetToNearbyMobsRadius", 16.0, 0.0, 256.0);

        SOUND_SCORING_SUBMIT_COOLDOWN_TICKS = BUILDER.comment("Cooldown per mob between async sound scoring submissions.")
                .defineInRange("soundScoringSubmitCooldownTicks", 1, 0, 10000);

        ASYNC_RESULT_TTL_TICKS = BUILDER.comment("Time-to-live for cached async sound scoring results.")
                .defineInRange("asyncResultTtlTicks", 10, 1, 10000);

        ENABLE_RAYCAST_CACHE = BUILDER.comment("Enable caching for raycast results.")
                .define("enableRaycastCache", true);

        RAYCAST_CACHE_TTL_TICKS = BUILDER.defineInRange("raycastCacheTtlTicks", 200, 1, 1000000);
        RAYCAST_CACHE_MAX_ENTRIES = BUILDER.defineInRange("raycastCacheMaxEntries", 5000, 100, 1000000);

        ENABLE_OPTIMIZED_LOS = BUILDER.define("enableOptimizedLos", true);
        ENABLE_OPTIMIZED_LOS_PAIR_CACHE = BUILDER.define("enableOptimizedLosPairCache", true);
        OPTIMIZED_LOS_PAIR_CACHE_MAX_ENTRIES = BUILDER.defineInRange("optimizedLosPairCacheMaxEntries", 8192, 256, 1000000);
        ENABLE_OPTIMIZED_LOS_VANILLA_FALLBACK = BUILDER.define("enableOptimizedLosVanillaFallback", true);

        ENABLE_LOS_BATCHING = BUILDER.define("enableLosBatching", true);
        LOS_BATCH_BUDGET_PER_TICK = BUILDER.defineInRange("losBatchBudgetPerTick", 64, 1, 1000000);
        LOS_BATCH_QUEUE_MAX_SIZE = BUILDER.defineInRange("losBatchQueueMaxSize", 4096, 64, 1000000);

        ENABLE_LIVING_ENTITY_LOS_OVERRIDE = BUILDER.define("enableLivingEntityLosOverride", true);
        
        MAX_MUFFLING_BLOCKS_TO_CHECK = BUILDER.comment("Maximum number of blocks to check for muffling between sound and mob.")
                .defineInRange("maxMufflingBlocksToCheck", 16, 8, 256);

        GLOBAL_CACHE_MAX_SIZE = BUILDER.comment("Maximum number of entries in internal tracking caches (e.g. stealth/scent memory) before oldest are evicted. Protects server RAM.")
                .defineInRange("globalCacheMaxSize", 10000, 100, 1000000);

        GLOBAL_CACHE_EXPIRE_MINS = BUILDER.comment("Time in minutes before an offline player or dead mob's data is naturally purged from memory.")
                .defineInRange("globalCacheExpireMins", 5, 1, 1440);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
