package com.example.soundattract.config.separate;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StealthConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue DEFAULT_HORIZONTAL_FOV;
    public static final ModConfigSpec.DoubleValue DEFAULT_VERTICAL_FOV;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_FOV_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOV_EXCLUSION_LIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NON_BLOCKING_VISION_ALLOW_LIST;

    public static final ModConfigSpec.BooleanValue ENABLE_STEALTH_MECHANICS;
    public static final ModConfigSpec.IntValue STEALTH_CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue STEALTH_GRACE_PERIOD_TICKS;
    public static final ModConfigSpec.DoubleValue MIN_STEALTH_DETECTION_RANGE;
    public static final ModConfigSpec.DoubleValue MAX_STEALTH_DETECTION_RANGE;

    public static final ModConfigSpec.DoubleValue STANDING_DETECTION_RANGE_PLAYER;
    public static final ModConfigSpec.DoubleValue SNEAKING_DETECTION_RANGE_PLAYER;
    public static final ModConfigSpec.DoubleValue CRAWLING_DETECTION_RANGE_PLAYER;

    public static final ModConfigSpec.IntValue NEUTRAL_LIGHT_LEVEL;
    public static final ModConfigSpec.DoubleValue LIGHT_LEVEL_SENSITIVITY;
    public static final ModConfigSpec.DoubleValue MIN_LIGHT_FACTOR;
    public static final ModConfigSpec.DoubleValue MAX_LIGHT_FACTOR;
    public static final ModConfigSpec.IntValue LIGHT_SAMPLE_RADIUS_HORIZONTAL;
    public static final ModConfigSpec.IntValue LIGHT_SAMPLE_RADIUS_VERTICAL;
    public static final ModConfigSpec.DoubleValue RAIN_STEALTH_FACTOR;
    public static final ModConfigSpec.DoubleValue THUNDER_STEALTH_FACTOR;

    public static final ModConfigSpec.DoubleValue MOVEMENT_STEALTH_PENALTY;
    public static final ModConfigSpec.DoubleValue STATIONARY_STEALTH_BONUS_FACTOR;
    public static final ModConfigSpec.DoubleValue MOVEMENT_THRESHOLD;
    public static final ModConfigSpec.DoubleValue INVISIBILITY_STEALTH_FACTOR;

    public static final ModConfigSpec.BooleanValue ENABLE_CAMOUFLAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_HELD_ITEM_PENALTY;
    public static final ModConfigSpec.DoubleValue HELD_ITEM_PENALTY_FACTOR;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTMENT_PENALTY;
    public static final ModConfigSpec.DoubleValue ARMOR_ENCHANTMENT_PENALTY_FACTOR;
    public static final ModConfigSpec.DoubleValue HELD_ITEM_ENCHANTMENT_PENALTY_FACTOR;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CAMOUFLAGE_ARMOR_ITEMS;
    public static final ModConfigSpec.BooleanValue REQUIRE_FULL_SET_FOR_CAMOUFLAGE_BONUS;
    public static final ModConfigSpec.DoubleValue FULL_ARMOR_STEALTH_BONUS;
    public static final ModConfigSpec.DoubleValue HELMET_CAMOUFLAGE_EFFECTIVENESS;
    public static final ModConfigSpec.DoubleValue CHESTPLATE_CAMOUFLAGE_EFFECTIVENESS;
    public static final ModConfigSpec.DoubleValue LEGGINGS_CAMOUFLAGE_EFFECTIVENESS;
    public static final ModConfigSpec.DoubleValue BOOTS_CAMOUFLAGE_EFFECTIVENESS;
    public static final ModConfigSpec.DoubleValue MAX_CAMOUFLAGE_EFFECTIVENESS_CAP;
    public static final ModConfigSpec.BooleanValue ALLOW_PARTIAL_BONUS_IF_FULL_SET_REQUIRED;

    public static final ModConfigSpec.BooleanValue ENABLE_ENVIRONMENTAL_CAMOUFLAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_ENVIRONMENTAL_MISMATCH_PENALTY;
    public static final ModConfigSpec.DoubleValue ENVIRONMENTAL_CAMOUFLAGE_MAX_EFFECTIVENESS;
    public static final ModConfigSpec.IntValue ENVIRONMENTAL_CAMOUFLAGE_COLOR_MATCH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue ENVIRONMENTAL_MISMATCH_PENALTY_FACTOR;
    public static final ModConfigSpec.IntValue ENVIRONMENTAL_MISMATCH_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENVIRONMENTAL_CAMOUFLAGE_ONLY_DYED_LEATHER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_ARMOR_COLORS;
    public static final ModConfigSpec.IntValue ENV_COLOR_SAMPLE_RADIUS;
    public static final ModConfigSpec.IntValue ENV_COLOR_SAMPLE_Y_OFFSET_START;
    public static final ModConfigSpec.IntValue ENV_COLOR_SAMPLE_Y_OFFSET_END;
    public static final ModConfigSpec.ConfigValue<String> ENVIRONMENTAL_CAMOUFLAGE_SAMPLING_MODE;
    public static final ModConfigSpec.IntValue ENV_BACKDROP_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue ENV_BACKDROP_SAMPLE_RAYS;
    public static final ModConfigSpec.IntValue ENV_BACKDROP_MIN_SAMPLES;
    public static final ModConfigSpec.IntValue ENV_BACKDROP_VIEWER_CELL_SIZE;
    public static final ModConfigSpec.IntValue ENV_BACKDROP_CACHE_TTL_TICKS;
    public static final ModConfigSpec.DoubleValue ENV_BACKDROP_MIN_TPS;
    public static final ModConfigSpec.DoubleValue ENV_BACKDROP_RECOVERY_TPS;
    public static final ModConfigSpec.BooleanValue ENV_BACKDROP_USE_SKY_COLOR;
    public static final ModConfigSpec.ConfigValue<String> ENV_BACKDROP_DAY_SKY_COLOR;
    public static final ModConfigSpec.ConfigValue<String> ENV_BACKDROP_NIGHT_SKY_COLOR;
    public static final ModConfigSpec.ConfigValue<String> ENV_BACKDROP_RAIN_SKY_COLOR;

    public static final ModConfigSpec.BooleanValue ENABLE_LAYERED_CAMOUFLAGE;
    public static final ModConfigSpec.IntValue MAX_CAMO_LAYERS;
    public static final ModConfigSpec.IntValue BASE_CAMO_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue WATER_DEGRADATION_RATE;
    public static final ModConfigSpec.DoubleValue SPRINT_DEGRADATION_RATE;
    public static final ModConfigSpec.DoubleValue DAMAGE_DEGRADATION_RATE;
    public static final ModConfigSpec.DoubleValue BIOME_MISMATCH_DEGRADATION_RATE;
    public static final ModConfigSpec.DoubleValue SCENT_MISMATCH_PENALTY_FACTOR;
    public static final ModConfigSpec.DoubleValue CAMO_APPLIED_SCENT_BLOCK_STRENGTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CAMOUFLAGE_MATERIALS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CAMO_CATEGORY_RULES;
    public static final ModConfigSpec.IntValue CAMO_TEXTURE_RESOLUTION;

    static {
        BUILDER.comment("Sound Attract Mod - Stealth & Detection Configuration").push("sound_attract_stealth");

        BUILDER.comment("--- Field of View Settings ---").push("fov");
        DEFAULT_HORIZONTAL_FOV = BUILDER.comment("Default horizontal Field of View (degrees). Mobs can detect entities within this horizontal arc.")
                .defineInRange("defaultHorizontalFov", 200.0, 0.0, 360.0);
        DEFAULT_VERTICAL_FOV = BUILDER.comment("Default vertical Field of View (degrees). Mobs can detect entities within this vertical arc.")
                .defineInRange("defaultVerticalFov", 135.0, 0.0, 360.0);
        CUSTOM_FOV_OVERRIDES = BUILDER.comment("List of custom FOV overrides. Format: 'modid:mob_id, horizontal_fov, vertical_fov'", "Example: 'minecraft:spider, 360.0, 360.0'")
                .defineList("customFovOverrides", Arrays.asList(
                        "minecraft:spider, 360.0, 360.0",
                        "minecraft:cave_spider, 360.0, 360.0",
                        "minecraft:phantom, 200.0, 280.0",
                        "minecraft:vex, 200.0, 280.0",
                        "minecraft:allay, 200.0, 280.0",
                        "minecraft:bat, 20.0, 20.0",
                        "minecraft:parrot, 200.0, 280.0",
                        "minecraft:ghast, 200.0, 280.0",
                        "minecraft:blaze, 200.0, 280.0",
                        "minecraft:axolotl, 270.0, 90.0",
                        "minecraft:camel, 270.0, 90.0",
                        "minecraft:chicken, 270.0, 90.0",
                        "minecraft:cow, 270.0, 90.0",
                        "minecraft:donkey, 270.0, 90.0",
                        "minecraft:goat, 270.0, 90.0",
                        "minecraft:horse, 270.0, 90.0",
                        "minecraft:mule, 270.0, 90.0",
                        "minecraft:mooshroom, 270.0, 90.0",
                        "minecraft:panda, 270.0, 90.0",
                        "minecraft:pig, 270.0, 90.0",
                        "minecraft:rabbit, 270.0, 90.0",
                        "minecraft:sheep, 270.0, 90.0",
                        "minecraft:sniffer, 270.0, 90.0",
                        "minecraft:strider, 270.0, 90.0",
                        "minecraft:turtle, 270.0, 90.0",
                        "minecraft:villager, 270.0, 90.0",
                        "minecraft:wandering_trader, 270.0, 90.0",
                        "minecraft:slime, 270.0, 120.0",
                        "minecraft:magma_cube, 270.0, 120.0",
                        "minecraft:cod, 300.0, 100.0",
                        "minecraft:pufferfish, 300.0, 100.0",
                        "minecraft:salmon, 300.0, 100.0",
                        "minecraft:squid, 300.0, 100.0",
                        "minecraft:glow_squid, 300.0, 100.0",
                        "minecraft:tadpole, 300.0, 100.0",
                        "minecraft:tropical_fish, 300.0, 100.0",
                        "minecraft:cat, 140.0, 140.0",
                        "minecraft:ocelot, 140.0, 140.0",
                        "minecraft:wolf, 140.0, 140.0",
                        "minecraft:polar_bear, 140.0, 140.0",
                        "minecraft:fox, 140.0, 140.0",
                        "minecraft:frog, 140.0, 140.0",
                        "minecraft:zombie, 200.0, 135.0",
                        "minecraft:husk, 200.0, 135.0",
                        "minecraft:drowned, 200.0, 135.0",
                        "minecraft:skeleton, 200.0, 135.0",
                        "minecraft:stray, 200.0, 135.0",
                        "minecraft:pillager, 200.0, 135.0",
                        "minecraft:vindicator, 200.0, 135.0",
                        "minecraft:evoker, 200.0, 135.0",
                        "minecraft:witch, 200.0, 135.0",
                        "minecraft:piglin, 200.0, 135.0",
                        "minecraft:piglin_brute, 200.0, 135.0",
                        "minecraft:iron_golem, 200.0, 135.0",
                        "minecraft:creeper, 90.0, 90.0",
                        "minecraft:enderman, 180.0, 240.0",
                        "minecraft:guardian, 320.0, 180.0",
                        "minecraft:elder_guardian, 320.0, 180.0",
                        "minecraft:ravager, 160.0, 100.0",
                        "minecraft:hoglin, 160.0, 100.0",
                        "minecraft:zoglin, 160.0, 100.0",
                        "minecraft:shulker, 270.0, 45.0"
                ), obj -> obj instanceof String && ((String) obj).split(",").length == 3);
        FOV_EXCLUSION_LIST = BUILDER.comment("List of mobs that will COMPLETELY IGNORE the FOV system (e.g. Warden). They will have 360-degree vision.")
                .defineList("fovExclusionList", Collections.singletonList("minecraft:warden"), obj -> obj instanceof String && net.minecraft.resources.ResourceLocation.tryParse((String) obj) != null);
        NON_BLOCKING_VISION_ALLOW_LIST = BUILDER.comment("List of blocks that DO NOT block line-of-sight checks (e.g. glass, bars).")
                .defineList("nonBlockingVisionAllowList", Collections.emptyList(), obj -> obj instanceof String && net.minecraft.resources.ResourceLocation.tryParse((String) obj) != null);
        BUILDER.pop();

        BUILDER.comment("General Stealth System Settings").push("general_stealth_settings");
        ENABLE_STEALTH_MECHANICS = BUILDER.comment("Master switch for all custom stealth mechanics.")
                .define("enableStealthMechanics", true);
        STEALTH_CHECK_INTERVAL = BUILDER.comment("How often (in ticks) to check stealth logic.")
                .defineInRange("stealthCheckInterval", 40, 5, 100);
        STEALTH_GRACE_PERIOD_TICKS = BUILDER.comment("Grace period (ticks) before losing aggro after stealth check fails.")
                .defineInRange("stealthGracePeriodTicks", 100, 0, 200);
        BUILDER.pop();

        BUILDER.comment("Base detection ranges for players based on stance.").push("player_stance_detection_ranges");
        STANDING_DETECTION_RANGE_PLAYER = BUILDER.defineInRange("standingDetectionRangePlayer", 32.0, 0.0, 512.0);
        SNEAKING_DETECTION_RANGE_PLAYER = BUILDER.defineInRange("sneakingDetectionRangePlayer", 12.0, 0.0, 512.0);
        CRAWLING_DETECTION_RANGE_PLAYER = BUILDER.defineInRange("crawlingDetectionRangePlayer", 4.0, 0.0, 512.0);
        BUILDER.pop();

        BUILDER.comment("Environmental Factors").push("environmental_factors");

        BUILDER.push("light_level");
        NEUTRAL_LIGHT_LEVEL = BUILDER.defineInRange("neutralLightLevel", 7, 0, 15);
        LIGHT_LEVEL_SENSITIVITY = BUILDER.defineInRange("lightLevelSensitivity", 0.3, 0.0, 0.5);
        MIN_LIGHT_FACTOR = BUILDER.defineInRange("minLightFactor", 0.2, 0.01, 1.0);
        MAX_LIGHT_FACTOR = BUILDER.defineInRange("maxLightFactor", 3.0, 1.0, 5.0);
        LIGHT_SAMPLE_RADIUS_HORIZONTAL = BUILDER.defineInRange("lightSampleRadiusHorizontal", 2, 0, 5);
        LIGHT_SAMPLE_RADIUS_VERTICAL = BUILDER.defineInRange("lightSampleRadiusVertical", 1, 0, 3);
        BUILDER.pop();

        BUILDER.push("weather");
        RAIN_STEALTH_FACTOR = BUILDER.defineInRange("rainStealthFactor", 0.8, 0.1, 1.0);
        THUNDER_STEALTH_FACTOR = BUILDER.defineInRange("thunderStealthFactor", 0.6, 0.1, 1.0);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.comment("Player Actions").push("player_actions");
        BUILDER.push("movement");
        MOVEMENT_STEALTH_PENALTY = BUILDER.defineInRange("movementStealthPenalty", 1.2, 1.0, 3.0);
        STATIONARY_STEALTH_BONUS_FACTOR = BUILDER.defineInRange("stationaryStealthBonusFactor", 0.8, 0.1, 1.0);
        MOVEMENT_THRESHOLD = BUILDER.defineInRange("movementThreshold", 0.003, 0.0001, 0.1);
        BUILDER.pop();

        BUILDER.push("invisibility");
        INVISIBILITY_STEALTH_FACTOR = BUILDER.defineInRange("invisibilityStealthFactor", 0.1, 0.0, 1.0);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.comment("Camouflage System").push("camouflage_system");
        ENABLE_CAMOUFLAGE = BUILDER.define("enableCamouflage", true);
        ENABLE_HELD_ITEM_PENALTY = BUILDER.define("enableHeldItemPenalty", true);
        HELD_ITEM_PENALTY_FACTOR = BUILDER.defineInRange("heldItemPenaltyFactor", 1.1, 1.0, 2.0);
        ENABLE_ENCHANTMENT_PENALTY = BUILDER.define("enableEnchantmentPenalty", true);
        ARMOR_ENCHANTMENT_PENALTY_FACTOR = BUILDER.defineInRange("armorEnchantmentPenaltyFactor", 1.15, 1.0, 2.0);
        HELD_ITEM_ENCHANTMENT_PENALTY_FACTOR = BUILDER.defineInRange("heldItemEnchantmentPenaltyFactor", 1.15, 1.0, 2.0);

        BUILDER.push("item_camouflage");
        CAMOUFLAGE_ARMOR_ITEMS = BUILDER.defineList("camouflageArmorItems", Collections.emptyList(), obj -> obj instanceof String);
        REQUIRE_FULL_SET_FOR_CAMOUFLAGE_BONUS = BUILDER.define("requireFullSetForCamouflageBonus", false);
        FULL_ARMOR_STEALTH_BONUS = BUILDER.defineInRange("fullArmorStealthBonus", 0.85, 0.0, 1.0);
        HELMET_CAMOUFLAGE_EFFECTIVENESS = BUILDER.defineInRange("helmetCamouflageEffectiveness", 0.15, 0.0, 1.0);
        CHESTPLATE_CAMOUFLAGE_EFFECTIVENESS = BUILDER.defineInRange("chestplateCamouflageEffectiveness", 0.30, 0.0, 1.0);
        LEGGINGS_CAMOUFLAGE_EFFECTIVENESS = BUILDER.defineInRange("leggingsCamouflageEffectiveness", 0.25, 0.0, 1.0);
        BOOTS_CAMOUFLAGE_EFFECTIVENESS = BUILDER.defineInRange("bootsCamouflageEffectiveness", 0.15, 0.0, 1.0);
        MAX_CAMOUFLAGE_EFFECTIVENESS_CAP = BUILDER.defineInRange("maxCamouflageEffectivenessCap", 0.85, 0.0, 0.99);
        ALLOW_PARTIAL_BONUS_IF_FULL_SET_REQUIRED = BUILDER.define("allowPartialBonusIfFullSetRequired", true);
        BUILDER.pop();

        BUILDER.push("environmental_camouflage");
        ENABLE_ENVIRONMENTAL_CAMOUFLAGE = BUILDER.define("enableEnvironmentalCamouflage", true);
        ENABLE_ENVIRONMENTAL_MISMATCH_PENALTY = BUILDER.define("enableEnvironmentalMismatchPenalty", true);
        ENVIRONMENTAL_CAMOUFLAGE_MAX_EFFECTIVENESS = BUILDER.defineInRange("environmentalCamouflageMaxEffectiveness", 0.70, 0.0, 1.0);
        ENVIRONMENTAL_CAMOUFLAGE_COLOR_MATCH_THRESHOLD = BUILDER.defineInRange("environmentalCamouflageColorMatchThreshold", 90, 0, 765);
        ENVIRONMENTAL_MISMATCH_PENALTY_FACTOR = BUILDER.defineInRange("environmentalMismatchPenaltyFactor", 1.3, 1.0, 3.0);
        ENVIRONMENTAL_MISMATCH_THRESHOLD = BUILDER.defineInRange("environmentalMismatchThreshold", 100, 0, 765);
        ENVIRONMENTAL_CAMOUFLAGE_ONLY_DYED_LEATHER = BUILDER.define("environmentalCamouflageOnlyDyedLeather", false);
        CUSTOM_ARMOR_COLORS = BUILDER.comment("Custom armor colors for camouflage mismatch calculations. Format: 'modid:item_id;#RRGGBB'")
                .defineList("customArmorColors", Arrays.asList(
                "minecraft:leather_helmet;#804F27",
                "minecraft:leather_chestplate;#804F27",
                "minecraft:leather_leggings;#804F27",
                "minecraft:leather_boots;#804F27",
                "minecraft:chainmail_helmet;#58585A",
                "minecraft:chainmail_chestplate;#58585A",
                "minecraft:chainmail_leggings;#58585A",
                "minecraft:chainmail_boots;#58585A",
                "minecraft:iron_helmet;#CACACA",
                "minecraft:iron_chestplate;#CACACA",
                "minecraft:iron_leggings;#CACACA",
                "minecraft:iron_boots;#CACACA",
                "minecraft:golden_helmet;#F5E54C",
                "minecraft:golden_chestplate;#F5E54C",
                "minecraft:golden_leggings;#F5E54C",
                "minecraft:golden_boots;#F5E54C",
                "minecraft:diamond_helmet;#39D5CD",
                "minecraft:diamond_chestplate;#39D5CD",
                "minecraft:diamond_leggings;#39D5CD",
                "minecraft:diamond_boots;#39D5CD",
                "minecraft:netherite_helmet;#403B3B",
                "minecraft:netherite_chestplate;#403B3B",
                "minecraft:netherite_leggings;#403B3B",
                "minecraft:netherite_boots;#403B3B",
                "minecraft:turtle_helmet;#7B8834",
                "toughasnails:leaf_helmet;#48B518",
                "toughasnails:leaf_chestplate;#48B518",
                "toughasnails:leaf_leggings;#48B518",
                "toughasnails:leaf_boots;#48B518",
                "toughasnails:wool_helmet;#FFFFFF",
                "toughasnails:wool_chestplate;#FFFFFF",
                "toughasnails:wool_leggings;#FFFFFF",
                "toughasnails:wool_boots;#FFFFFF"
        ), obj -> obj instanceof String && ((String) obj).split(";").length == 2);
        ENV_COLOR_SAMPLE_RADIUS = BUILDER.defineInRange("envColorSampleRadius", 1, 0, 3);
        ENV_COLOR_SAMPLE_Y_OFFSET_START = BUILDER.defineInRange("envColorSampleYOffsetStart", 0, -2, 2);
        ENV_COLOR_SAMPLE_Y_OFFSET_END = BUILDER.defineInRange("envColorSampleYOffsetEnd", -1, -2, 2);
        ENVIRONMENTAL_CAMOUFLAGE_SAMPLING_MODE = BUILDER.comment("Environmental color sampler: average_area, viewer_backdrop, or hybrid.")
                .define("environmentalCamouflageSamplingMode", "hybrid");
        ENV_BACKDROP_MAX_DISTANCE = BUILDER.comment("How far behind the target to look for backdrop blocks.")
                .defineInRange("envBackdropMaxDistance", 4, 1, 16);
        ENV_BACKDROP_SAMPLE_RAYS = BUILDER.comment("How many target silhouette rays to sample.")
                .defineInRange("envBackdropSampleRays", 5, 1, 5);
        ENV_BACKDROP_MIN_SAMPLES = BUILDER.comment("Minimum backdrop hits needed for a color.")
                .defineInRange("envBackdropMinSamples", 2, 1, 5);
        ENV_BACKDROP_VIEWER_CELL_SIZE = BUILDER.comment("Nearby mobs in the same cell share backdrop results.")
                .defineInRange("envBackdropViewerCellSize", 6, 1, 64);
        ENV_BACKDROP_CACHE_TTL_TICKS = BUILDER.comment("How long backdrop color results stay fresh.")
                .defineInRange("envBackdropCacheTtlTicks", 2, 1, 200);
        ENV_BACKDROP_MIN_TPS = BUILDER.comment("Below this TPS, use the cheaper sampler.")
                .defineInRange("envBackdropMinTps", 17.0, 1.0, 20.0);
        ENV_BACKDROP_RECOVERY_TPS = BUILDER.comment("TPS needed before backdrop sampling resumes.")
                .defineInRange("envBackdropRecoveryTps", 18.5, 1.0, 20.0);
        ENV_BACKDROP_USE_SKY_COLOR = BUILDER.comment("Use sky color when the backdrop is open sky.")
                .define("envBackdropUseSkyColor", true);
        ENV_BACKDROP_DAY_SKY_COLOR = BUILDER.comment("Backdrop color for clear daytime sky.")
                .define("envBackdropDaySkyColor", "#77ADFF");
        ENV_BACKDROP_NIGHT_SKY_COLOR = BUILDER.comment("Backdrop color for night sky.")
                .define("envBackdropNightSkyColor", "#0B1026");
        ENV_BACKDROP_RAIN_SKY_COLOR = BUILDER.comment("Backdrop color for rain and thunder sky.")
                .define("envBackdropRainSkyColor", "#596772");
        BUILDER.pop();

        BUILDER.push("layered_camouflage");
        ENABLE_LAYERED_CAMOUFLAGE = BUILDER.define("enableLayeredCamouflage", true);
        MAX_CAMO_LAYERS = BUILDER.defineInRange("maxCamoLayers", 3, 1, 10);
        BASE_CAMO_DURATION_TICKS = BUILDER.defineInRange("baseCamoDurationTicks", 12000, 100, 1000000);
        WATER_DEGRADATION_RATE = BUILDER.defineInRange("waterDegradationRate", 0.02, 0.0, 1.0);
        SPRINT_DEGRADATION_RATE = BUILDER.defineInRange("sprintDegradationRate", 0.02, 0.0, 1.0);
        DAMAGE_DEGRADATION_RATE = BUILDER.defineInRange("damageDegradationRate", 0.1, 0.0, 1.0);
        BIOME_MISMATCH_DEGRADATION_RATE = BUILDER.defineInRange("biomeMismatchDegradationRate", 0.01, 0.0, 1.0);
        SCENT_MISMATCH_PENALTY_FACTOR = BUILDER.defineInRange("scentMismatchPenaltyFactor", 0.3, 0.0, 1.0);
        CAMO_APPLIED_SCENT_BLOCK_STRENGTH = BUILDER.defineInRange("camoAppliedScentBlockStrength", 1.0, 0.0, 1.0);

        CAMOUFLAGE_MATERIALS = BUILDER.comment("Format: namespace:item_id ; #HexColor ; category ; blocksScent")
                .defineList("camouflageMaterials", Arrays.asList(
                    "minecraft:snowball;#CACACA;snow;true",
                    "minecraft:mud;#5C3A1E;mud;true",
                    "minecraft:coarse_dirt;#6B5A4A;earth;false",
                    "minecraft:sand;#D4C98A;earth;false",
                    "minecraft:red_sand;#B86434;earth;false",
                    "minecraft:oak_sapling;#2D6B1B;foliage;false",
                    "minecraft:spruce_sapling;#1A3D14;foliage;false",
                    "minecraft:moss_block;#4A7A3C;foliage;false"
                ), obj -> obj instanceof String);

        CAMO_CATEGORY_RULES = BUILDER.comment("Format: category ; preferredTempRange(low-high) ; waterSensitive")
                .defineList("camoCategoryRules", Arrays.asList(
                    "snow;0.0-0.3;true",
                    "mud;0.0-1.5;true",
                    "earth;0.0-2.0;false",
                    "foliage;0.6-0.95;false"
                ), obj -> obj instanceof String);

        CAMO_TEXTURE_RESOLUTION = BUILDER.comment("Resolution of the procedurally generated camouflage smudge textures. Higher values look smoother but use more memory.")
                .defineInRange("camoTextureResolution", 256, 16, 1024);
        BUILDER.pop();

        BUILDER.pop();

        BUILDER.comment("Detection Range Limits").push("detection_range_limits");
        MIN_STEALTH_DETECTION_RANGE = BUILDER.defineInRange("minStealthDetectionRange", 0.5, 0.0, 64.0);
        MAX_STEALTH_DETECTION_RANGE = BUILDER.defineInRange("maxStealthDetectionRange", 64.0, 1.0, 1024.0);
        BUILDER.pop();

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}