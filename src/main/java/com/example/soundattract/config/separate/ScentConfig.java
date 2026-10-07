package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class ScentConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_SYSTEM;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCENT_ELIGIBLE_MOBS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_PARTICLES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GAMEPLAY_SCENT_PARTICLES;
    public static final ForgeConfigSpec.IntValue SCENT_PARTICLE_SPAWN_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue SCENT_PARTICLE_RENDER_DISTANCE;
    public static final ForgeConfigSpec.IntValue SCENT_NODE_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue SCENT_CREATION_INTERVAL_BLOCKS;

    public static final ForgeConfigSpec.DoubleValue TEMP_HEAVY_DECAY_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue TEMP_FREEZE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue HUMIDITY_PRESERVE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue RAIN_DECAY_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue WATER_STOPS_SCENT;

    public static final ForgeConfigSpec.IntValue SCENT_AMBUSH_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue SCENT_UPDATE_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue SCENT_STRENGTH_DECAY;
    public static final ForgeConfigSpec.DoubleValue SCENT_DETECTION_RADIUS;
    public static final ForgeConfigSpec.BooleanValue RAIN_WASHES_SCENTS;

    public static final ForgeConfigSpec.IntValue SCENT_SCAN_BUDGET_PER_TICK;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ARROW_SCENT_TRAIL;
    public static final ForgeConfigSpec.DoubleValue ARROW_SCENT_EMISSION_INTERVAL_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue ARROW_SCENT_STRENGTH;
    public static final ForgeConfigSpec.IntValue ARROW_SCENT_NODE_DURATION_TICKS;
    public static final ForgeConfigSpec.BooleanValue SCENT_OVERRIDE_SOUND_PRIORITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_PARTICLES_FOR_ARROWS;
    public static final ForgeConfigSpec.IntValue ARROW_SCENT_RATE_LIMIT_PER_SHOOTER_PER_MINUTE;
    public static final ForgeConfigSpec.DoubleValue SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue SCENT_VS_SOUND_HYBRID_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue SCENT_QUERY_CACHE_TTL_TICKS;
    public static final ForgeConfigSpec.IntValue SCENT_QUERY_CACHE_MAX_ENTRIES;
    public static final ForgeConfigSpec.IntValue PLAYER_ODOR_SCAN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAYER_ODOR_ITEM_VALUES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WIND_SCENT;
    public static final ForgeConfigSpec.DoubleValue WIND_SCENT_ACTIVATION_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue WIND_SCENT_DEACTIVATION_THRESHOLD;
    public static final ForgeConfigSpec.IntValue WIND_SCENT_UPDATE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue WIND_SCENT_MAXIMUM_DISTANCE;
    public static final ForgeConfigSpec.IntValue WIND_SCENT_MAXIMUM_PARCELS;
    public static final ForgeConfigSpec.DoubleValue WINDSHIELD_SCENT_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue WIND_SCENT_SPEED_SCALE;
    public static final ForgeConfigSpec.BooleanValue WIND_SCENT_RAIN_DECAY;
    public static final ForgeConfigSpec.DoubleValue WIND_SCENT_SPREAD_FACTOR;
    public static final ForgeConfigSpec.DoubleValue WIND_SCENT_BARRIER_LEAKAGE;
    public static final ForgeConfigSpec.IntValue WINDSHIELD_SCAN_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue WINDSHIELD_MINIMUM_WIND_SPEED;
    public static final ForgeConfigSpec.DoubleValue WINDSHIELD_ACTIVATION_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue WINDSHIELD_DEACTIVATION_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WIND_SCENT_PARTICLES;
    public static final ForgeConfigSpec.IntValue WIND_SCENT_PARTICLE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue WIND_SCENT_PARTICLE_MAX_PER_VIEWER;

    static {
        BUILDER.comment("Sound Attract Mod - Scent System Configuration").push("scent_system");

        ENABLE_SCENT_SYSTEM = BUILDER.comment("Enable the scent tracking system for mobs.")
                .define("enableScentSystem", true);

        SCENT_ELIGIBLE_MOBS = BUILDER.comment("List of mobs that can smell and track scent trails.")
                .defineList("scentEligibleMobs", Arrays.asList("minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:wolf"), obj -> obj instanceof String);

        ENABLE_SCENT_PARTICLES = BUILDER.comment("Render debug particles for scent nodes (debug only, one-shot).")
                .define("enableScentParticles", false);

        ENABLE_GAMEPLAY_SCENT_PARTICLES = BUILDER.comment("Enable persistent gameplay scent particles that last as long as the scent node. Per-player colored.")
                .define("enableGameplayScentParticles", false);

        SCENT_PARTICLE_SPAWN_INTERVAL = BUILDER.comment("Ticks between re-spawning particles at each living scent node (lower = more frequent, higher = better performance).")
                .defineInRange("scentParticleSpawnInterval", 40, 5, 200);

        SCENT_PARTICLE_RENDER_DISTANCE = BUILDER.comment("Maximum distance from a player to render scent particles.")
                .defineInRange("scentParticleRenderDistance", 32.0, 8.0, 128.0);

        SCENT_NODE_DURATION_TICKS = BUILDER.comment("How long a scent node lasts in ticks (default 6000 = 5 minutes).")
                .defineInRange("scentNodeDurationTicks", 6000, 100, 72000);

        SCENT_CREATION_INTERVAL_BLOCKS = BUILDER.comment("Distance in blocks the player must move to create a new scent node.")
                .defineInRange("scentCreationIntervalBlocks", 5.0, 1.0, 64.0);

        BUILDER.push("environmental_logic");
        TEMP_HEAVY_DECAY_THRESHOLD = BUILDER.comment("Biome temperature above which scent decays faster (Hot).")
                .defineInRange("tempHeavyDecayThreshold", 1.0, 0.0, 2.0);
        TEMP_FREEZE_THRESHOLD = BUILDER.comment("Biome temperature below which scent is preserved but has reduced range (Cold).")
                .defineInRange("tempFreezeThreshold", 0.15, -1.0, 2.0);
        HUMIDITY_PRESERVE_THRESHOLD = BUILDER.comment("Biome rainfall/humidity above which scent lasts longer (High Humidity).")
                .defineInRange("humidityPreserveThreshold", 0.8, 0.0, 1.0);
        RAIN_DECAY_MULTIPLIER = BUILDER.comment("Multiplier for scent decay rate when raining.")
                .defineInRange("rainDecayMultiplier", 4.0, 1.0, 100.0);
        WATER_STOPS_SCENT = BUILDER.comment("If true, players in water will not leave scent trails.")
                .define("waterStopsScent", true);
        RAIN_WASHES_SCENTS = BUILDER.comment("If true, rain will wash away scents faster.")
                .define("rainWashesScents", true);
        BUILDER.pop();

        BUILDER.push("ai_logic");
        SCENT_AMBUSH_DURATION_TICKS = BUILDER.comment("How long a mob will 'camp' at the last known location of an offline player.")
                .defineInRange("scentAmbushDurationTicks", 400, 0, 72000);
        SCENT_UPDATE_INTERVAL = BUILDER.comment("Interval (in ticks) for updating scent logic.")
                .defineInRange("scentUpdateInterval", 20, 1, 100);
        SCENT_STRENGTH_DECAY = BUILDER.comment("Amount of scent strength lost per tick.")
                .defineInRange("scentStrengthDecay", 0.01, 0.0, 1.0);
        SCENT_DETECTION_RADIUS = BUILDER.comment("Radius within which mobs can detect scents.")
                .defineInRange("scentDetectionRadius", 16.0, 1.0, 64.0);
        SCENT_SCAN_BUDGET_PER_TICK = BUILDER.comment("Maximum number of mobs that can compute a new scent path per tick (prevents lag spikes during horde scent tracking).")
                .defineInRange("scentScanBudgetPerTick", 5, 1, 100);
        BUILDER.pop();

        BUILDER.push("arrow_scent");
        ENABLE_ARROW_SCENT_TRAIL = BUILDER.comment("Enable scent trails from arrows and projectiles.")
                .define("enableArrowScentTrail", true);
        ARROW_SCENT_EMISSION_INTERVAL_BLOCKS = BUILDER.comment("Distance in blocks an arrow must travel to emit a new scent node.")
                .defineInRange("arrowScentEmissionIntervalBlocks", 3.0, 0.5, 16.0);
        ARROW_SCENT_STRENGTH = BUILDER.comment("Base strength of arrow scent nodes.")
                .defineInRange("arrowScentStrength", 0.8, 0.1, 2.0);
        ARROW_SCENT_NODE_DURATION_TICKS = BUILDER.comment("How long arrow scent nodes last in ticks (default 500 = 25 seconds).")
                .defineInRange("arrowScentNodeDurationTicks", 500, 100, 72000);
        SCENT_OVERRIDE_SOUND_PRIORITY = BUILDER.comment("If true, fresh scent trails override sound attraction priority.")
                .define("scentOverrideSoundPriority", true);
        ENABLE_SCENT_PARTICLES_FOR_ARROWS = BUILDER.comment("Render particles for arrow scent trails.")
                .define("enableScentParticlesForArrows", false);
        ARROW_SCENT_RATE_LIMIT_PER_SHOOTER_PER_MINUTE = BUILDER.comment("Max arrow scent nodes a single shooter can create per minute.")
                .defineInRange("arrowScentRateLimitPerShooterPerMinute", 20, 10, 500);
        SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD = BUILDER
                .comment("When scentOverrideSoundPriority=true: sounds with weight strictly below this value always lose to fresh scent.")
                .defineInRange("scentOverrideSoundWeightThreshold", 10.0, 0.0, 1000.0);
        SCENT_VS_SOUND_HYBRID_MULTIPLIER = BUILDER
                .comment("Above the threshold: soundWeight is compared against (scentStrength * multiplier). Higher = scent wins more often.")
                .defineInRange("scentVsSoundHybridMultiplier", 1.0, 0.0, 100.0);
        SCENT_QUERY_CACHE_TTL_TICKS = BUILDER
                .comment("How long scent-query results are cached per mob (ticks). Lower = fresher but more CPU.")
                .defineInRange("scentQueryCacheTtlTicks", 5, 1, 200);
        SCENT_QUERY_CACHE_MAX_ENTRIES = BUILDER
                .comment("Maximum mobs tracked in the scent-query cache (memory cap).")
                .defineInRange("scentQueryCacheMaxEntries", 4096, 128, 65536);
        BUILDER.pop();

        BUILDER.push("player_odor");
        PLAYER_ODOR_SCAN_INTERVAL_TICKS = BUILDER.comment("Ticks between player inventory odor scans.")
                .defineInRange("scanIntervalTicks", 1200, 20, 12000);
        PLAYER_ODOR_ITEM_VALUES = BUILDER.comment("Base food-item odor values in the form namespace:item=value; Spoiled adds a separate spoilage-progress bonus.")
                .defineList("itemValues", Arrays.asList(
                        "minecraft:rotten_flesh=0.25",
                        "minecraft:beef=0.1",
                        "minecraft:porkchop=0.1",
                        "minecraft:chicken=0.1",
                        "minecraft:mutton=0.1",
                        "minecraft:rabbit=0.1",
                        "minecraft:cod=0.05",
                        "minecraft:salmon=0.05"
                ), obj -> obj instanceof String && ((String) obj).contains("="));
        BUILDER.pop();

        BUILDER.push("wind_scent");
        ENABLE_WIND_SCENT = BUILDER.define("enableWindScent", true);
        WIND_SCENT_ACTIVATION_THRESHOLD = BUILDER.defineInRange("activationThreshold", 0.80, 0.50, 1.0);
        WIND_SCENT_DEACTIVATION_THRESHOLD = BUILDER.defineInRange("deactivationThreshold", 0.65, 0.0, 0.95);
        WIND_SCENT_UPDATE_INTERVAL_TICKS = BUILDER.defineInRange("updateIntervalTicks", 30, 1, 100);
        WIND_SCENT_MAXIMUM_DISTANCE = BUILDER.defineInRange("maximumDistance", 32, 4, 128);
        WIND_SCENT_MAXIMUM_PARCELS = BUILDER.defineInRange("maximumParcels", 16, 1, 64);
        WINDSHIELD_SCENT_REDUCTION = BUILDER.defineInRange("windshieldScentReduction", 0.90, 0.0, 1.0);
        WIND_SCENT_SPEED_SCALE = BUILDER.comment("Scale Weather2's wind speed into blocks traveled per scent update.")
                .defineInRange("windSpeedScale", 2.0, 0.0, 4.0);
        WIND_SCENT_RAIN_DECAY = BUILDER.comment("Apply the generic rain decay multiplier to wind scent; rain effects can vary by odorant.")
                .define("rainDecay", true);
        WIND_SCENT_SPREAD_FACTOR = BUILDER.comment("Controls lateral scent spread as a fraction of wind movement; the per-update spread is capped at half a block.")
                .defineInRange("spreadFactor", 0.2, 0.0, 1.0);
        WIND_SCENT_BARRIER_LEAKAGE = BUILDER.comment("Fraction of scent strength that leaks around a solid obstruction.")
                .defineInRange("barrierLeakage", 0.35, 0.0, 1.0);
        WINDSHIELD_SCAN_DISTANCE = BUILDER.comment("Maximum upwind distance in blocks used to detect a windshield.")
                .defineInRange("windshieldScanDistance", 4, 1, 16);
        WINDSHIELD_MINIMUM_WIND_SPEED = BUILDER.comment("Minimum Weather2 wind speed for windshield protection to activate.")
                .defineInRange("windshieldMinimumWindSpeed", 0.1, 0.0, 1.0);
        WINDSHIELD_ACTIVATION_THRESHOLD = BUILDER.comment("Windward block coverage needed to activate the windshield indicator.")
                .defineInRange("windshieldActivationThreshold", 0.34, 0.0, 1.0);
        WINDSHIELD_DEACTIVATION_THRESHOLD = BUILDER.comment("Windward block coverage below which an active windshield indicator turns off; keep below windshieldActivationThreshold to avoid flicker.")
                .defineInRange("windshieldDeactivationThreshold", 0.25, 0.0, 1.0);
        ENABLE_WIND_SCENT_PARTICLES = BUILDER.comment("Render wind-scent wisps for players whose profile enables showWindScent.")
                .define("enableWindScentParticles", true);
        WIND_SCENT_PARTICLE_INTERVAL_TICKS = BUILDER.comment("Ticks between wind-scent wisp bursts; higher values reduce particle and packet work.")
                .defineInRange("windScentParticleIntervalTicks", 20, 5, 100);
        WIND_SCENT_PARTICLE_MAX_PER_VIEWER = BUILDER.comment("Maximum wind-scent wisps sent to each viewer per burst.")
                .defineInRange("windScentParticleMaxPerViewer", 32, 1, 128);
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
