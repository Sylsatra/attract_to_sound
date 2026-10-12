package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class IntegrationConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_QUANTIFIED_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WEATHER2_WIND_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SMART_BRAIN_LIB_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CUSTOM_NPCS_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUANTIFIED_CACHE_INTEGRATION;
    public static final ForgeConfigSpec.IntValue QUANTIFIED_CACHE_MEMORY_LIMIT_MB;
    public static final ForgeConfigSpec.BooleanValue DISABLE_QUANTIFIED_CACHE_ON_MEMORY_PRESSURE;
    public static final ForgeConfigSpec.BooleanValue TRIGGER_QUANTIFIED_CACHE_CLEANUP_ON_MEMORY_PRESSURE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUANTIFIED_SOUND_SCORE_SLICE_CACHE;
    public static final ForgeConfigSpec.BooleanValue QUANTIFIED_SOUND_SCORE_SLICE_CACHE_PERSISTENT;
    public static final ForgeConfigSpec.IntValue QUANTIFIED_SOUND_SCORE_SLICE_CACHE_TTL_TICKS;
    public static final ForgeConfigSpec.IntValue QUANTIFIED_SOUND_SCORE_SLICE_CACHE_MAX_ENTRIES;

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ForgeConfigSpec.DoubleValue BLOCK_BREAKING_TIME_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue BLOCK_BREAKING_TOOL_ONLY;
    public static final ForgeConfigSpec.BooleanValue BLOCK_BREAKING_PROPER_TOOL_ONLY;

    public static final ForgeConfigSpec.BooleanValue ENABLE_TELEPORT_TO_SOUND;
    public static final ForgeConfigSpec.DoubleValue TELEPORT_CHANCE;
    public static final ForgeConfigSpec.IntValue TELEPORT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> TELEPORT_CAN_TELEPORT_TAG;
    public static final ForgeConfigSpec.ConfigValue<String> TELEPORT_CAN_BE_TELEPORTED_TAG;

    public static final ForgeConfigSpec.BooleanValue ENABLE_PICK_UP_AND_THROW_TO_SOUND;
    public static final ForgeConfigSpec.DoubleValue PICK_UP_CHANCE;
    public static final ForgeConfigSpec.IntValue PICK_UP_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PICK_UP_MIN_DISTANCE_TO_PICK_UP;
    public static final ForgeConfigSpec.IntValue PICK_UP_MAX_DISTANCE_TO_THROW;
    public static final ForgeConfigSpec.DoubleValue PICK_UP_SPEED_MODIFIER;
    public static final ForgeConfigSpec.ConfigValue<String> PICK_UP_CAN_PICK_UP_TAG;
    public static final ForgeConfigSpec.ConfigValue<String> PICK_UP_CAN_BE_PICKED_UP_TAG;

    public static final ForgeConfigSpec.BooleanValue ENABLE_XRAY_TARGETING;
    public static final ForgeConfigSpec.ConfigValue<String> XRAY_APPLY_TAG;
    public static final ForgeConfigSpec.BooleanValue XRAY_REQUIRE_BETTER_NEARBY;
    public static final ForgeConfigSpec.ConfigValue<String> XRAY_BETTER_NEARBY_TAG;
    public static final ForgeConfigSpec.IntValue XRAY_MIN_RANGE;
    public static final ForgeConfigSpec.IntValue XRAY_MAX_RANGE;
    public static final ForgeConfigSpec.DoubleValue XRAY_CHANCE;

    public static final ForgeConfigSpec.BooleanValue ENABLE_RELENTLESS_CLIMBING;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RELENTLESS_ELIGIBLE_MOBS;
    public static final ForgeConfigSpec.BooleanValue ZOMBIES_IGNORE_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue ZOMBIES_CAN_STACK;
    public static final ForgeConfigSpec.DoubleValue ZOMBIE_FALL_DAMAGE_MULTIPLIER;

    public static final ForgeConfigSpec.BooleanValue ENABLE_IMMERSIVE_MELODIES_INTEGRATION;
    public static final ForgeConfigSpec.IntValue IMMERSIVE_MELODIES_POLL_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue IMMERSIVE_MELODIES_DEFAULT_RANGE;
    public static final ForgeConfigSpec.DoubleValue IMMERSIVE_MELODIES_DEFAULT_WEIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IMMERSIVE_MELODIES_INSTRUMENT_MULTIPLIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IMMERSIVE_MELODIES_MELODY_OVERRIDES;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SPORE_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SPORE_SCENT_TRAIL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STEALTH_MARKER_BRIDGE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_BLOCK_SUPPRESSION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROTO_SOUND_DEPLOYMENT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SOUND_RELAY;
    public static final ForgeConfigSpec.BooleanValue SMART_SPREAD_TO_SOUND;
    public static final ForgeConfigSpec.DoubleValue STEALTH_MARKER_CAMO_THRESHOLD;
    public static final ForgeConfigSpec.IntValue STEALTH_BRIDGE_CHECK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue SCENT_ENTITY_DISSIPATION_ACCEL;
    public static final ForgeConfigSpec.DoubleValue PROTO_SOUND_WEIGHT_THRESHOLD;
    public static final ForgeConfigSpec.IntValue PROTO_SOUND_DEPLOYMENT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PROTO_SOUND_BIOMASS_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue PROTO_SPREAD_MAX_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue PROTO_SPREAD_LERP_FACTOR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROTO_PREDATORY_CREEP;
    public static final ForgeConfigSpec.DoubleValue PROTO_CREEP_ADVANCE_DISTANCE;
    public static final ForgeConfigSpec.IntValue PROTO_CREEP_INTERVAL_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> VIGIL_SIGHTED_SOUND;
    public static final ForgeConfigSpec.ConfigValue<String> PROTO_HUNT_BEGIN_SOUND;
    public static final ForgeConfigSpec.ConfigValue<String> PROTO_HUNT_BEGIN_PARTICLE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROTO_NEURAL_INFLUENCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HARMONIC_FEAR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DELUGE_OF_SOUND;
    public static final ForgeConfigSpec.DoubleValue HARMONIC_FEAR_RANGE;
    public static final ForgeConfigSpec.DoubleValue DELUGE_OF_SOUND_WEIGHT_THRESHOLD;

    public static final ForgeConfigSpec.DoubleValue SOUND_RELAY_RANGE;

    public static final ForgeConfigSpec.BooleanValue ENABLE_CSGRENADES_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TACZ_NPC_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FLASHBANG_BLINDING;
    public static final ForgeConfigSpec.DoubleValue FLASHBANG_BLIND_RADIUS;
    public static final ForgeConfigSpec.IntValue FLASHBANG_BLIND_DURATION_TICKS;
    public static final ForgeConfigSpec.BooleanValue FLASHBANG_REQUIRE_LINE_OF_SIGHT;
    public static final ForgeConfigSpec.BooleanValue FLASHBANG_REQUIRE_FOV;
    public static final ForgeConfigSpec.IntValue FLASHBANG_MOB_SCAN_BUDGET;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SMOKE_LOS_BLOCKING;
    public static final ForgeConfigSpec.DoubleValue SMOKE_CLOUD_RADIUS;
    public static final ForgeConfigSpec.IntValue SMOKE_LIFETIME_MAX_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FIRE_FLEE;
    public static final ForgeConfigSpec.DoubleValue FIRE_FLEE_DANGER_RADIUS;
    public static final ForgeConfigSpec.DoubleValue FIRE_FLEE_AWAY_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FIRE_FLEE_SPEED_MODIFIER;
    public static final ForgeConfigSpec.IntValue FIRE_FLEE_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLEE_FROM_FIRE_ELIGIBLE_MOBS;
    public static final ForgeConfigSpec.IntValue CSGRENADES_TRACKER_SCAN_INTERVAL_TICKS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_HOTBATH_INTEGRATION;
    public static final ForgeConfigSpec.IntValue HOTBATH_POLL_INTERVAL_TICKS;
    public static final ForgeConfigSpec.DoubleValue HOTBATH_MAX_DIRTY_SCENT_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue HOTBATH_DEFAULT_BATH_AROMA_DURATION_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOTBATH_FLUID_SCENT_MODIFIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOTBATH_BIOME_SCENT_MODIFIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOTBATH_CAMO_WASH_RATES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOTBATH_SPLASH_CAMO_WASH_RATES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOTBATH_CUSTOM_FLUID_SCENT_MODIFIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HOTBATH_CUSTOM_FLUID_CAMO_WASH_RATES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SPOILED_FOOD_SCENT;
    public static final ForgeConfigSpec.DoubleValue SPOILED_FOOD_ODOR_PER_ITEM;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TOUGH_AS_NAILS_INTEGRATION;
    public static final ForgeConfigSpec.IntValue TOUGH_AS_NAILS_POLL_INTERVAL_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TOUGH_AS_NAILS_TEMPERATURE_SCENT_MULTIPLIERS;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_HYPERTHERMIA_SCENT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_HYPERTHERMIA_SOUND_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_THIRST_SCENT_MULTIPLIER_AT_EMPTY;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_THIRST_SOUND_MULTIPLIER_AT_EMPTY;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_WOOL_ARMOR_SOUND_MULTIPLIER_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_WOOL_ARMOR_MINIMUM_SOUND_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_WOOL_WARM_SCENT_MULTIPLIER_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_WOOL_HOT_SCENT_MULTIPLIER_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_WOOL_HEAT_MAXIMUM_SCENT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_LEAF_ARMOR_SCENT_MULTIPLIER_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue TOUGH_AS_NAILS_LEAF_ARMOR_MINIMUM_SCENT_MULTIPLIER;

    static {
        BUILDER.comment("Settings for other mod integrations").push("integration");

        ENABLE_QUANTIFIED_INTEGRATION = BUILDER.define("enableQuantifiedIntegration", true);
        ENABLE_WEATHER2_WIND_INTEGRATION = BUILDER.comment("Use Weather2's wind field for wind scent and windshield calculations when Weather2 is installed.")
                .define("enableWeather2WindIntegration", true);
        ENABLE_SMART_BRAIN_LIB_INTEGRATION = BUILDER.define("enableSmartBrainLibIntegration", true);
        ENABLE_CUSTOM_NPCS_INTEGRATION = BUILDER.define("enableCustomNpcsIntegration", true);
        ENABLE_TACZ_NPC_INTEGRATION = BUILDER.comment("Enable optional flashbang and smoke targeting behavior for TACZ NPCs when TACZ NPC is installed.")
                .define("enableTaczNpcIntegration", true);

        BUILDER.push("quantified_cache");
        ENABLE_QUANTIFIED_CACHE_INTEGRATION = BUILDER.define("enableQuantifiedCacheIntegration", true);
        QUANTIFIED_CACHE_MEMORY_LIMIT_MB = BUILDER.defineInRange("quantifiedCacheMemoryLimitMB", 256, 0, 65536);
        DISABLE_QUANTIFIED_CACHE_ON_MEMORY_PRESSURE = BUILDER.define("disableQuantifiedCacheOnMemoryPressure", true);
        TRIGGER_QUANTIFIED_CACHE_CLEANUP_ON_MEMORY_PRESSURE = BUILDER.define("triggerQuantifiedCacheCleanupOnMemoryPressure", true);
        ENABLE_QUANTIFIED_SOUND_SCORE_SLICE_CACHE = BUILDER.define("enableQuantifiedSoundScoreSliceCache", true);
        QUANTIFIED_SOUND_SCORE_SLICE_CACHE_PERSISTENT = BUILDER.define("quantifiedSoundScoreSliceCachePersistent", false);
        QUANTIFIED_SOUND_SCORE_SLICE_CACHE_TTL_TICKS = BUILDER.defineInRange("quantifiedSoundScoreSliceCacheTtlTicks", 20, 1, 72000);
        QUANTIFIED_SOUND_SCORE_SLICE_CACHE_MAX_ENTRIES = BUILDER.defineInRange("quantifiedSoundScoreSliceCacheMaxEntries", 4096, 64, 1_000_000);
        BUILDER.pop();

        BUILDER.push("enhanced_ai_inspired");
        ENABLE_BLOCK_BREAKING = BUILDER.define("enableBlockBreaking", false);
        BLOCK_BREAKING_TIME_MULTIPLIER = BUILDER.defineInRange("blockBreakingTimeMultiplier", 1.5, -1.0, 100.0);
        BLOCK_BREAKING_TOOL_ONLY = BUILDER.define("blockBreakingToolOnly", false);
        BLOCK_BREAKING_PROPER_TOOL_ONLY = BUILDER.define("blockBreakingProperToolOnly", false);

        ENABLE_TELEPORT_TO_SOUND = BUILDER.define("enableTeleportToSound", false);
        TELEPORT_CHANCE = BUILDER.defineInRange("teleportChance", 0.35, 0.0, 1.0);
        TELEPORT_COOLDOWN_TICKS = BUILDER.defineInRange("teleportCooldownTicks", 300, 0, 72000);
        TELEPORT_CAN_TELEPORT_TAG = BUILDER.define("teleportCanTeleportTag", "enhancedai:mobs/teleport_to_target/can_teleport");
        TELEPORT_CAN_BE_TELEPORTED_TAG = BUILDER.comment("EntityType tag used to decide which mobs can be teleported. Defaults to EnhancedAI's tag.")
                .define("teleportCanBeTeleportedTag", "enhancedai:mobs/teleport_to_target/can_be_teleported");

        ENABLE_PICK_UP_AND_THROW_TO_SOUND = BUILDER.comment("Enable special AI: mobs with the specified tag can pick up another mob and throw it toward the sound location.")
                .define("enablePickUpAndThrowToSound", false);
        PICK_UP_CHANCE = BUILDER.comment("Chance [0..1] for a thrower mob to attempt the pick-up-and-throw behavior. If EnhancedAI is installed, its difficulty-based chance is used instead.")
                .defineInRange("pickUpChance", 0.05, 0.0, 1.0);
        PICK_UP_COOLDOWN_TICKS = BUILDER.comment("Cooldown (in ticks) after a throw action. If EnhancedAI is installed, its value is used instead.")
                .defineInRange("pickUpCooldownTicks", 600, 0, 72000);
        PICK_UP_MIN_DISTANCE_TO_PICK_UP = BUILDER.comment("Minimum distance from the sound for the mob to consider picking up a target.")
                .defineInRange("pickUpMinDistanceToPickUp", 5, 0, 1024);
        PICK_UP_MAX_DISTANCE_TO_THROW = BUILDER.comment("Max distance to the sound within which the mob will release/throw the picked-up mob.")
                .defineInRange("pickUpMaxDistanceToThrow", 24, 0, 1024);
        PICK_UP_SPEED_MODIFIER = BUILDER.comment("Speed modifier applied to the mob while approaching the pick-up target.")
                .defineInRange("pickUpSpeedModifier", 1.25, 0.0, 10.0);
        PICK_UP_CAN_PICK_UP_TAG = BUILDER.comment("EntityType tag used to decide which mobs can perform pick-up-and-throw. Defaults to EnhancedAI's tag.")
                .define("pickUpCanPickUpTag", "enhancedai:mobs/pick_up_and_throw/can_pick_up");
        PICK_UP_CAN_BE_PICKED_UP_TAG = BUILDER.comment("EntityType tag used to decide which mobs can be picked up. Defaults to EnhancedAI's tag.")
                .define("pickUpCanBePickedUpTag", "enhancedai:mobs/pick_up_and_throw/can_be_picked_up");

        ENABLE_XRAY_TARGETING = BUILDER.comment("Enable XRAY targeting compat: mobs in the apply_xray tag can detect players through walls up to a configured range.")
                .define("enableXrayTargeting", false);
        XRAY_APPLY_TAG = BUILDER.comment("EntityType tag used to decide which mobs can have XRAY detection. Defaults to EnhancedAI's tag.")
                .define("xrayApplyTag", "enhancedai:mobs/targeting/apply_xray");
        XRAY_REQUIRE_BETTER_NEARBY = BUILDER.comment("Require the mob to also be in the Better Nearby Targeting tag to apply XRAY (mirrors EnhancedAI behavior).")
                .define("xrayRequireBetterNearby", false);
        XRAY_BETTER_NEARBY_TAG = BUILDER.comment("EntityType tag for Better Nearby Targeting. Used only if xrayRequireBetterNearby is true.")
                .define("xrayBetterNearbyTag", "enhancedai:mobs/targeting/better_nearby_targeting");
        XRAY_MIN_RANGE = BUILDER.comment("Minimum XRAY follow range (blocks) for fallback when EnhancedAI is not present. 0..128")
                .defineInRange("xrayMinRange", 16, 0, 128);
        XRAY_MAX_RANGE = BUILDER.comment("Maximum XRAY follow range (blocks). 0 disables XRAY fallback.")
                .defineInRange("xrayMaxRange", 24, 0, 128);
        XRAY_CHANCE = BUILDER.comment("Chance [0..1] for a mob in the XRAY tag to get the XRAY range (fallback when EnhancedAI is not present).")
                .defineInRange("xrayChance", 0.5, 0.0, 1.0);

        BUILDER.pop();

        BUILDER.push("relentless_undead_inspired");
        ENABLE_RELENTLESS_CLIMBING = BUILDER.define("enableRelentlessClimbing", false);
        RELENTLESS_ELIGIBLE_MOBS = BUILDER.defineList("relentlessEligibleMobs", Arrays.asList("minecraft:zombie", "minecraft:husk", "minecraft:drowned"), obj -> obj instanceof String);
        ZOMBIES_IGNORE_HEIGHT = BUILDER.define("zombiesIgnoreHeight", false);
        ZOMBIES_CAN_STACK = BUILDER.define("zombiesCanStack", true);
        ZOMBIE_FALL_DAMAGE_MULTIPLIER = BUILDER.defineInRange("zombieFallDamageMultiplier", 0.5, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("immersive_melodies");
        ENABLE_IMMERSIVE_MELODIES_INTEGRATION = BUILDER.comment("Enable integration with Immersive Melodies: playing instruments attracts mobs.")
                .define("enableImmersiveMelodiesIntegration", true);
        IMMERSIVE_MELODIES_POLL_INTERVAL = BUILDER.comment("How often (in ticks) to check if a player is playing an instrument. A higher value reduces overhead and increases the gap in the sound trail.")
                .defineInRange("immersiveMelodiesPollInterval", 40, 1, 1200);
        IMMERSIVE_MELODIES_DEFAULT_RANGE = BUILDER.comment("Default attraction range for instruments.")
                .defineInRange("immersiveMelodiesDefaultRange", 48.0, 0.0, 512.0);
        IMMERSIVE_MELODIES_DEFAULT_WEIGHT = BUILDER.comment("Default weight for instruments.")
                .defineInRange("immersiveMelodiesDefaultWeight", 30.0, 0.0, 100.0);
        IMMERSIVE_MELODIES_INSTRUMENT_MULTIPLIERS = BUILDER.comment("List of instrument item IDs and their weight multipliers (e.g. 'immersive_melodies:drum:2.0').")
                .defineList("immersiveMelodiesInstrumentMultipliers", Arrays.asList("immersive_melodies:drum:1.5"), obj -> obj instanceof String);
        IMMERSIVE_MELODIES_MELODY_OVERRIDES = BUILDER.comment("List of melody IDs and their custom range:weight (e.g. 'immersive_melodies:test_melody:32.0:3.0').")
                .defineList("immersiveMelodiesMelodyOverrides", Arrays.asList(), obj -> obj instanceof String);
        BUILDER.pop();

        BUILDER.push("spore");
        ENABLE_SPORE_INTEGRATION = BUILDER.comment("Master toggle for Spore mod integration. Enables sound attraction, stealth bridge, and Proto deployment for Spore entities.")
                .define("enableSporeIntegration", true);
        ENABLE_SPORE_SCENT_TRAIL = BUILDER.comment("Inject FollowScentGoal into Spore Infected entities so they can follow player scent trails.")
                .define("enableSporeScentTrail", true);
        ENABLE_STEALTH_MARKER_BRIDGE = BUILDER.comment("Bridge Sound Attract's camouflage system with Spore's MARKER effect. Degraded camo applies MARKER, making the entity visible to all Infected through walls.")
                .define("enableStealthMarkerBridge", true);
        ENABLE_SCENT_BLOCK_SUPPRESSION = BUILDER.comment("When a player with high scent-blocking camo is near a Spore ScentEntity, accelerate its dissipation.")
                .define("enableScentBlockSuppression", true);
        ENABLE_PROTO_SOUND_DEPLOYMENT = BUILDER.comment("Allow Proto to react to high-weight sounds by deploying Vigil scouts toward the sound source.")
                .define("enableProtoSoundDeployment", true);
        ENABLE_SOUND_RELAY = BUILDER.comment("Allow linked Infected to propagate sound positions to nearby linked Infected via their searchPos.")
                .define("enableSoundRelay", true);
        SMART_SPREAD_TO_SOUND = BUILDER.comment("Proto waits for scout confirmation before spreading biomass toward sound. If false, spreads immediately.")
                .define("smartSpreadToSound", true);

        STEALTH_MARKER_CAMO_THRESHOLD = BUILDER.comment("Visual camo strength below which MARKER is applied when degrading from above.")
                .defineInRange("stealthMarkerCamoThreshold", 0.3, 0.0, 1.0);
        STEALTH_BRIDGE_CHECK_INTERVAL_TICKS = BUILDER.comment("How often (in ticks) the stealth bridge scans for camo transitions and ScentEntity suppression.")
                .defineInRange("stealthBridgeCheckIntervalTicks", 40, 1, 1200);
        SCENT_ENTITY_DISSIPATION_ACCEL = BUILDER.comment("Extra dissipation ticks added per bridge cycle to ScentEntities near camo'd entities.")
                .defineInRange("scentEntityDissipationAccel", 40, 1, 600);

        PROTO_SOUND_WEIGHT_THRESHOLD = BUILDER.comment("Minimum sound weight for Proto to consider deploying scouts.")
                .defineInRange("protoSoundWeightThreshold", 10.0, 0.0, 100.0);
        PROTO_SOUND_DEPLOYMENT_COOLDOWN_TICKS = BUILDER.comment("Cooldown (in ticks) between Proto scout deployments.")
                .defineInRange("protoSoundDeploymentCooldownTicks", 200, 0, 72000);
        PROTO_SOUND_BIOMASS_THRESHOLD = BUILDER.comment("Minimum biomass for Proto to deploy scouts.")
                .defineInRange("protoSoundBiomassThreshold", 20, 0, 10000);
        PROTO_SPREAD_MAX_DISTANCE = BUILDER.comment("Maximum distance from Proto's NODE for biased biomass spread toward sound. Beyond this, no spread occurs.")
                .defineInRange("protoSpreadMaxDistance", 256.0, 0.0, 512.0);
        PROTO_SPREAD_LERP_FACTOR = BUILDER.comment("How far toward the sound source to bias the spread center (0.0=at NODE, 1.0=at sound).")
                .defineInRange("protoSpreadLerpFactor", 0.35, 0.0, 1.0);

        BUILDER.push("predatory_creep");
        ENABLE_PROTO_PREDATORY_CREEP = BUILDER.comment("If true, when a Vigil spots a player, the Proto will creep biomass toward their location.")
                .define("enableProtoPredatoryCreep", true);
        PROTO_CREEP_ADVANCE_DISTANCE = BUILDER.comment("Distance (blocks) the biomass advances per creeping step.")
                .defineInRange("protoCreepAdvanceDistance", 8.0, 1.0, 64.0);
        PROTO_CREEP_INTERVAL_TICKS = BUILDER.comment("Ticks between creeping steps. Higher values reduce CPU usage.")
                .defineInRange("protoCreepIntervalTicks", 200, 20, 12000);
        VIGIL_SIGHTED_SOUND = BUILDER.comment("Sound played by the Vigil when spotting a player to alert the Proto. Default is Spore's eye stare sound.")
                .define("vigilSightedSound", "spore:vigil_eye_use");
        PROTO_HUNT_BEGIN_SOUND = BUILDER.comment("Sound played by the Proto when it begins a predatory hunt.")
                .define("protoHuntBeginSound", "spore:proto_ambient");
        PROTO_HUNT_BEGIN_PARTICLE = BUILDER.comment("Particle played at the Proto's NODE when a hunt begins.")
                .define("protoHuntBeginParticle", "minecraft:sculk_soul");

        ENABLE_PROTO_NEURAL_INFLUENCE = BUILDER.comment("Neural-Net Influence: Loud sounds bias Proto toward heavy units; quiet sounds bias it toward scouts.")
                .define("enableProtoNeuralInfluence", true);
        ENABLE_HARMONIC_FEAR = BUILDER.comment("Harmonic Fear: Hive Tumors panic if players make sound while standing on biomass.")
                .define("enableHarmonicFear", true);
        ENABLE_DELUGE_OF_SOUND = BUILDER.comment("Deluge of Sound: Delusionares warp nearby infected to noisy players.")
                .define("enableDelugeOfSound", true);
        HARMONIC_FEAR_RANGE = BUILDER.comment("Range (blocks) for Hive Tumor biomass sound detection.")
                .defineInRange("harmonicFearRange", 32.0, 1.0, 512.0);
        DELUGE_OF_SOUND_WEIGHT_THRESHOLD = BUILDER.comment("Minimum sound weight to trigger Delusionare's mass teleportation.")
                .defineInRange("delugeOfSoundWeightThreshold", 20.0, 1.0, 100.0);
        BUILDER.pop();

        SOUND_RELAY_RANGE = BUILDER.comment("Range (blocks) for linked Infected sound propagation to nearby Infected.")
                .defineInRange("soundRelayRange", 32.0, 0.0, 256.0);
        BUILDER.pop();

        BUILDER.comment("CS Grenades integration settings").push("csgrenades_integration");
        ENABLE_CSGRENADES_INTEGRATION = BUILDER
                .comment("Master switch for all CS Grenades integration behaviors.")
                .define("enableCsgrenadesIntegration", true);
        ENABLE_FLASHBANG_BLINDING = BUILDER
                .comment("Flashbang blinds mobs within range + LOS + FOV.")
                .define("enableFlashbangBlinding", true);
        FLASHBANG_BLIND_RADIUS = BUILDER
                .comment("Maximum blocks from flash at which a mob can still be blinded.")
                .defineInRange("flashbangBlindRadius", 16.0, 2.0, 64.0);
        FLASHBANG_BLIND_DURATION_TICKS = BUILDER
                .comment("Base blind duration; actual = base * (1 - dist/radius).")
                .defineInRange("flashbangBlindDurationTicks", 300, 1, 6000);
        FLASHBANG_REQUIRE_LINE_OF_SIGHT = BUILDER
                .comment("If true, mob must have LOS to the flash to be blinded.")
                .define("flashbangRequireLineOfSight", true);
        FLASHBANG_REQUIRE_FOV = BUILDER
                .comment("If true, mob must have the flash inside its FOV to be blinded.")
                .define("flashbangRequireFov", true);
        FLASHBANG_MOB_SCAN_BUDGET = BUILDER
                .comment("Max mobs evaluated per flashbang activation (hard cap).")
                .defineInRange("flashbangMobScanBudget", 64, 8, 512);
        ENABLE_SMOKE_LOS_BLOCKING = BUILDER
                .comment("Active smoke grenades block line-of-sight through their cloud.")
                .define("enableSmokeLosBlocking", true);
        SMOKE_CLOUD_RADIUS = BUILDER
                .comment("Radius of the smoke sphere used for LOS intersection.")
                .defineInRange("smokeCloudRadius", 4.0, 1.0, 16.0);
        SMOKE_LIFETIME_MAX_TICKS = BUILDER
                .comment("Safety cap: ignore smoke entities older than this many ticks since activation.")
                .defineInRange("smokeLifetimeMaxTicks", 400, 20, 6000);
        ENABLE_FIRE_FLEE = BUILDER
                .comment("Eligible non-fire-immune mobs flee from active incendiary/molotov grenades.")
                .define("enableFireFlee", true);
        FIRE_FLEE_DANGER_RADIUS = BUILDER
                .comment("A mob this close to an active fire grenade is considered in danger.")
                .defineInRange("fireFleeDangerRadius", 6.0, 1.0, 32.0);
        FIRE_FLEE_AWAY_DISTANCE = BUILDER
                .comment("Blocks the mob tries to sprint away from fire.")
                .defineInRange("fireFleeAwayDistance", 16.0, 4.0, 64.0);
        FIRE_FLEE_SPEED_MODIFIER = BUILDER
                .comment("Speed multiplier applied while fleeing fire.")
                .defineInRange("fireFleeSpeedModifier", 1.4, 0.5, 3.0);
        FIRE_FLEE_COOLDOWN_TICKS = BUILDER
                .comment("Minimum ticks between re-triggering flee for the same mob.")
                .defineInRange("fireFleeCooldownTicks", 40, 1, 600);
        FLEE_FROM_FIRE_ELIGIBLE_MOBS = BUILDER
                .comment("Entity IDs eligible to receive FleeFromFireGoal. TACZ Hostiles and TACZ NPC entities are also included automatically. mob.fireImmune()==true is always excluded.")
                .defineList("fleeFromFireEligibleMobs", java.util.Arrays.asList(
                        "minecraft:zombie","minecraft:husk","minecraft:drowned","minecraft:skeleton",
                        "minecraft:stray","minecraft:creeper","minecraft:spider","minecraft:cave_spider",
                        "minecraft:wolf","minecraft:villager","minecraft:iron_golem","minecraft:piglin",
                        "minecraft:piglin_brute","minecraft:zoglin","minecraft:hoglin","minecraft:witch",
                        "minecraft:pillager","minecraft:vindicator","minecraft:evoker","minecraft:ravager"
                ), obj -> obj instanceof String);
        CSGRENADES_TRACKER_SCAN_INTERVAL_TICKS = BUILDER
                .comment("How often the active-grenade cache rebuilds (smoke/fire).")
                .defineInRange("csgrenadesTrackerScanIntervalTicks", 5, 1, 40);
        BUILDER.pop();

        BUILDER.comment("HotBath integration settings").push("hotbath");
        ENABLE_HOTBATH_INTEGRATION = BUILDER.comment("Enable optional HotBath integration for scent and camouflage wash behavior.")
                .define("enableHotBathIntegration", true);
        HOTBATH_POLL_INTERVAL_TICKS = BUILDER.comment("Target interval for refreshing each online player's HotBath scent/camo state. Work is spread across ticks.")
                .defineInRange("hotBathPollIntervalTicks", 80, 1, 24000);
        HOTBATH_MAX_DIRTY_SCENT_MULTIPLIER = BUILDER.comment("Scent multiplier at 100% HotBath dirtiness. Interpolates linearly from 1.0 when clean.")
                .defineInRange("maxDirtyScentMultiplier", 2.0, 0.0, 100.0);
        HOTBATH_DEFAULT_BATH_AROMA_DURATION_TICKS = BUILDER.comment("Fallback duration for bath aroma scent modifiers after bathing or splash cleanup.")
                .defineInRange("defaultBathAromaDurationTicks", 2400, 0, 72000);
        HOTBATH_FLUID_SCENT_MODIFIERS = BUILDER.comment("Built-in HotBath fluid scent modifiers. Format: 'fluid_id;scent_multiplier;duration_ticks'.")
                .defineList("hotBathFluidScentModifiers", Arrays.asList(
                        "hotbath:hot_water_fluid;0.70;2400",
                        "hotbath:milk_bath_fluid;0.45;3000",
                        "hotbath:herbal_bath_fluid;0.55;3000",
                        "hotbath:honey_bath_fluid;0.85;2400",
                        "hotbath:peony_bath_fluid;0.80;2400",
                        "hotbath:rose_bath_fluid;1.10;2400"
                ), obj -> obj instanceof String);
        HOTBATH_BIOME_SCENT_MODIFIERS = BUILDER.comment("Bath aroma biome modifiers. Format: 'fluid_id;biome_id_or_#biome_tag;multiplier'.")
                .defineList("hotBathBiomeScentModifiers", Arrays.asList(
                        "hotbath:herbal_bath_fluid;#minecraft:is_desert;1.35"
                ), obj -> obj instanceof String);
        HOTBATH_CAMO_WASH_RATES = BUILDER.comment("Gradual camo wash while standing in HotBath fluids. Format: 'fluid_id;skin_rate;armor_rate'. Values remove that fraction per poll.")
                .defineList("hotBathCamoWashRates", Arrays.asList(
                        "hotbath:hot_water_fluid;0.08;0.08",
                        "hotbath:milk_bath_fluid;0.05;0.05",
                        "hotbath:herbal_bath_fluid;0.06;0.06",
                        "hotbath:honey_bath_fluid;0.03;0.03",
                        "hotbath:peony_bath_fluid;0.04;0.04",
                        "hotbath:rose_bath_fluid;0.04;0.04"
                ), obj -> obj instanceof String);
        HOTBATH_SPLASH_CAMO_WASH_RATES = BUILDER.comment("Camo wash when hit by HotBath splash bottles. Format: 'fluid_id;skin_fraction;armor_fraction'. Values remove that fraction instantly.")
                .defineList("hotBathSplashCamoWashRates", Arrays.asList(
                        "hotbath:hot_water_fluid;0.50;0.50",
                        "hotbath:milk_bath_fluid;0.50;0.50",
                        "hotbath:herbal_bath_fluid;0.50;0.50",
                        "hotbath:honey_bath_fluid;0.40;0.40",
                        "hotbath:peony_bath_fluid;0.45;0.45",
                        "hotbath:rose_bath_fluid;0.45;0.45"
                ), obj -> obj instanceof String);
        HOTBATH_CUSTOM_FLUID_SCENT_MODIFIERS = BUILDER.comment("Custom HotBath datapack fluid scent modifiers. Format: 'fluid_id;scent_multiplier;duration_ticks'.")
                .defineList("hotBathCustomFluidScentModifiers", Arrays.asList(), obj -> obj instanceof String);
        HOTBATH_CUSTOM_FLUID_CAMO_WASH_RATES = BUILDER.comment("Custom HotBath datapack fluid camo wash rates. Format: 'fluid_id;skin_rate;armor_rate'.")
                .defineList("hotBathCustomFluidCamoWashRates", Arrays.asList(), obj -> obj instanceof String);
        BUILDER.pop();

        BUILDER.comment("Spoiled food scent settings").push("spoiled");
        ENABLE_SPOILED_FOOD_SCENT = BUILDER.comment("Add odor from Spoiled-tagged items based on their spoilage progress.")
                .define("enableSpoiledFoodScent", true);
        SPOILED_FOOD_ODOR_PER_ITEM = BUILDER.comment("Additional odor per item at full spoilage progress; scales linearly with the Spoiled timer.")
                .defineInRange("spoiledFoodOdorPerItem", 1.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.comment("ToughAsNails integration settings").push("tough_as_nails");
        ENABLE_TOUGH_AS_NAILS_INTEGRATION = BUILDER.comment("Enable ToughAsNails scent and sound integration.")
                .define("enableToughAsNailsIntegration", true);
        TOUGH_AS_NAILS_POLL_INTERVAL_TICKS = BUILDER.comment("How often each player's ToughAsNails state is refreshed.")
                .defineInRange("toughAsNailsPollIntervalTicks", 40, 1, 24000);
        TOUGH_AS_NAILS_TEMPERATURE_SCENT_MULTIPLIERS = BUILDER.comment("Scent multipliers by ToughAsNails temperature. Format: 'LEVEL;multiplier'.")
                .defineList("temperatureScentMultipliers", Arrays.asList(
                        "ICY;0.75",
                        "COLD;0.90",
                        "NEUTRAL;1.00",
                        "WARM;1.20",
                        "HOT;1.50"
                ), obj -> obj instanceof String);
        TOUGH_AS_NAILS_HYPERTHERMIA_SCENT_MULTIPLIER = BUILDER.comment("Scent multiplier while hyperthermia is active.")
                .defineInRange("hyperthermiaScentMultiplier", 1.40, 0.0, 100.0);
        TOUGH_AS_NAILS_HYPERTHERMIA_SOUND_MULTIPLIER = BUILDER.comment("Player-action sound multiplier while hyperthermia is active.")
                .defineInRange("hyperthermiaSoundMultiplier", 1.15, 0.0, 100.0);
        TOUGH_AS_NAILS_THIRST_SCENT_MULTIPLIER_AT_EMPTY = BUILDER.comment("Scent multiplier when thirst is empty.")
                .defineInRange("thirstScentMultiplierAtEmpty", 1.25, 0.0, 100.0);
        TOUGH_AS_NAILS_THIRST_SOUND_MULTIPLIER_AT_EMPTY = BUILDER.comment("Player-action sound multiplier when thirst is empty.")
                .defineInRange("thirstSoundMultiplierAtEmpty", 1.10, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_ARMOR_SOUND_MULTIPLIER_PER_PIECE = BUILDER.comment("Sound multiplier for each worn wool armor piece.")
                .defineInRange("woolArmorSoundMultiplierPerPiece", 0.92, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_ARMOR_MINIMUM_SOUND_MULTIPLIER = BUILDER.comment("Lowest sound multiplier from wool armor.")
                .defineInRange("woolArmorMinimumSoundMultiplier", 0.70, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_WARM_SCENT_MULTIPLIER_PER_PIECE = BUILDER.comment("Scent multiplier for each wool piece while warm.")
                .defineInRange("woolWarmScentMultiplierPerPiece", 1.05, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_HOT_SCENT_MULTIPLIER_PER_PIECE = BUILDER.comment("Scent multiplier for each wool piece while hot.")
                .defineInRange("woolHotScentMultiplierPerPiece", 1.10, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_HEAT_MAXIMUM_SCENT_MULTIPLIER = BUILDER.comment("Highest scent multiplier from hot wool armor.")
                .defineInRange("woolHeatMaximumScentMultiplier", 1.60, 0.0, 100.0);
        TOUGH_AS_NAILS_LEAF_ARMOR_SCENT_MULTIPLIER_PER_PIECE = BUILDER.comment("Scent multiplier for each worn leaf armor piece.")
                .defineInRange("leafArmorScentMultiplierPerPiece", 0.97, 0.0, 100.0);
        TOUGH_AS_NAILS_LEAF_ARMOR_MINIMUM_SCENT_MULTIPLIER = BUILDER.comment("Lowest scent multiplier from leaf armor.")
                .defineInRange("leafArmorMinimumScentMultiplier", 0.90, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
