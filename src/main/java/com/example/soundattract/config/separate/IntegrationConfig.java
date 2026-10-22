package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class IntegrationConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_QUANTIFIED_INTEGRATION;
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
        ENABLE_SMART_BRAIN_LIB_INTEGRATION = BUILDER.define("enableSmartBrainLibIntegration", true);
        ENABLE_CUSTOM_NPCS_INTEGRATION = BUILDER.define("enableCustomNpcsIntegration", true);
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
        TELEPORT_CAN_TELEPORT_TAG = BUILDER.comment("Entity type tag for mobs that can teleport another mob toward a sound.")
                .define("teleportCanTeleportTag", "soundattract:mobs/teleport_to_target/can_teleport");
        TELEPORT_CAN_BE_TELEPORTED_TAG = BUILDER.comment("Entity type tag for mobs that may be teleported toward a sound.")
                .define("teleportCanBeTeleportedTag", "soundattract:mobs/teleport_to_target/can_be_teleported");

        ENABLE_PICK_UP_AND_THROW_TO_SOUND = BUILDER.comment("Enable special AI: mobs with the specified tag can pick up another mob and throw it toward the sound location.")
                .define("enablePickUpAndThrowToSound", false);
        PICK_UP_CHANCE = BUILDER.comment("Chance [0..1] for a thrower mob to attempt the pick-up-and-throw behavior.")
                .defineInRange("pickUpChance", 0.05, 0.0, 1.0);
        PICK_UP_COOLDOWN_TICKS = BUILDER.comment("Cooldown (in ticks) after a throw action.")
                .defineInRange("pickUpCooldownTicks", 600, 0, 72000);
        PICK_UP_MIN_DISTANCE_TO_PICK_UP = BUILDER.comment("Minimum distance from the sound for the mob to consider picking up a target.")
                .defineInRange("pickUpMinDistanceToPickUp", 5, 0, 1024);
        PICK_UP_MAX_DISTANCE_TO_THROW = BUILDER.comment("Max distance to the sound within which the mob will release/throw the picked-up mob.")
                .defineInRange("pickUpMaxDistanceToThrow", 24, 0, 1024);
        PICK_UP_SPEED_MODIFIER = BUILDER.comment("Speed modifier applied to the mob while approaching the pick-up target.")
                .defineInRange("pickUpSpeedModifier", 1.25, 0.0, 10.0);
        PICK_UP_CAN_PICK_UP_TAG = BUILDER.comment("Entity type tag for mobs that can pick up and throw another mob.")
                .define("pickUpCanPickUpTag", "soundattract:mobs/pick_up_and_throw/can_pick_up");
        PICK_UP_CAN_BE_PICKED_UP_TAG = BUILDER.comment("Entity type tag for mobs that may be picked up and thrown.")
                .define("pickUpCanBePickedUpTag", "soundattract:mobs/pick_up_and_throw/can_be_picked_up");

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

        BUILDER.push("spoiled");
        ENABLE_SPOILED_FOOD_SCENT = BUILDER.define("enableSpoiledFoodScent", true);
        SPOILED_FOOD_ODOR_PER_ITEM = BUILDER.defineInRange("spoiledFoodOdorPerItem", 1.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.push("tough_as_nails");
        ENABLE_TOUGH_AS_NAILS_INTEGRATION = BUILDER.define("enableToughAsNailsIntegration", true);
        TOUGH_AS_NAILS_POLL_INTERVAL_TICKS = BUILDER.defineInRange("toughAsNailsPollIntervalTicks", 40, 1, 24000);
        TOUGH_AS_NAILS_TEMPERATURE_SCENT_MULTIPLIERS = BUILDER.defineList("temperatureScentMultipliers", Arrays.asList(
                "ICY;0.75", "COLD;0.90", "NEUTRAL;1.00", "WARM;1.20", "HOT;1.50"), obj -> obj instanceof String);
        TOUGH_AS_NAILS_HYPERTHERMIA_SCENT_MULTIPLIER = BUILDER.defineInRange("hyperthermiaScentMultiplier", 1.40, 0.0, 100.0);
        TOUGH_AS_NAILS_HYPERTHERMIA_SOUND_MULTIPLIER = BUILDER.defineInRange("hyperthermiaSoundMultiplier", 1.15, 0.0, 100.0);
        TOUGH_AS_NAILS_THIRST_SCENT_MULTIPLIER_AT_EMPTY = BUILDER.defineInRange("thirstScentMultiplierAtEmpty", 1.25, 0.0, 100.0);
        TOUGH_AS_NAILS_THIRST_SOUND_MULTIPLIER_AT_EMPTY = BUILDER.defineInRange("thirstSoundMultiplierAtEmpty", 1.10, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_ARMOR_SOUND_MULTIPLIER_PER_PIECE = BUILDER.defineInRange("woolArmorSoundMultiplierPerPiece", 0.92, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_ARMOR_MINIMUM_SOUND_MULTIPLIER = BUILDER.defineInRange("woolArmorMinimumSoundMultiplier", 0.70, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_WARM_SCENT_MULTIPLIER_PER_PIECE = BUILDER.defineInRange("woolWarmScentMultiplierPerPiece", 1.05, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_HOT_SCENT_MULTIPLIER_PER_PIECE = BUILDER.defineInRange("woolHotScentMultiplierPerPiece", 1.10, 0.0, 100.0);
        TOUGH_AS_NAILS_WOOL_HEAT_MAXIMUM_SCENT_MULTIPLIER = BUILDER.defineInRange("woolHeatMaximumScentMultiplier", 1.60, 0.0, 100.0);
        TOUGH_AS_NAILS_LEAF_ARMOR_SCENT_MULTIPLIER_PER_PIECE = BUILDER.defineInRange("leafArmorScentMultiplierPerPiece", 0.97, 0.0, 100.0);
        TOUGH_AS_NAILS_LEAF_ARMOR_MINIMUM_SCENT_MULTIPLIER = BUILDER.defineInRange("leafArmorMinimumScentMultiplier", 0.90, 0.0, 100.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
