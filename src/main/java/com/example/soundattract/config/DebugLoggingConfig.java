package com.example.soundattract.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class DebugLoggingConfig {
    public enum Feature {
        SOUND_ATTRACTION("ai", "soundAttraction"),
        MOB_GROUPS("ai", "mobGroups"),
        BLOCK_BREAKING("ai", "blockBreaking"),
        RAIDS("ai", "raids"),
        LIVING_WALL_ADMISSION("livingWall", "admission"),
        LIVING_WALL_CONSTRUCTION("livingWall", "construction"),
        LIVING_WALL_ROUTING("livingWall", "routing"),
        LIVING_WALL_FORMATION("livingWall", "formation"),
        LIVING_WALL_RECOVERY("livingWall", "recovery"),
        STEALTH_DETECTION("perception", "stealthDetection"),
        SCENT("perception", "scent"),
        SOUND_TRACKING("perception", "soundTracking"),
        CAMOUFLAGE("perception", "camouflage"),
        ARROW_INVESTIGATION("perception", "arrowInvestigation"),
        FLOOR_CREEKS("perception", "floorCreeks"),
        PATHFINDING("navigation", "pathfinding"),
        WORKERS("runtime", "workers"),
        CONFIG_RELOAD("runtime", "configReload"),
        MOD_LIFECYCLE("runtime", "modLifecycle"),
        CUSTOM_NPCS("integrations", "customNpcs"),
        TACZ("integrations", "tacz"),
        POINT_BLANK("integrations", "pointBlank"),
        CS_GRENADES("integrations", "csGrenades"),
        SPORE("integrations", "spore"),
        SMART_BRAIN_LIB("integrations", "smartBrainLib"),
        HOT_BATH("integrations", "hotBath"),
        TOUGH_AS_NAILS("integrations", "toughAsNails"),
        VOICE_CHAT("integrations", "voiceChat"),
        IMMERSIVE_MELODIES("integrations", "immersiveMelodies"),
        WEATHER2_WIND("integrations", "weather2Wind"),
        SPOILED("integrations", "spoiled"),
        TACZ_HOSTILES("integrations", "taczHostiles"),
        TACZ_NPCS("integrations", "taczNpcs"),
        VANILLA("integrations", "vanilla"),
        DATAPACK_RELOAD("data", "datapackReload");

        private final String section;
        private final String key;

        Feature(String section, String key) {
            this.section = section;
            this.key = key;
        }
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final Map<Feature, ForgeConfigSpec.BooleanValue> FEATURES = new EnumMap<>(Feature.class);
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("debug_logging");
        for (String section : new String[]{"ai", "livingWall", "perception", "navigation", "runtime", "integrations", "data"}) {
            BUILDER.push(section);
            for (Feature feature : Feature.values()) {
                if (feature.section.equals(section)) {
                    FEATURES.put(feature, BUILDER.define(feature.key, false));
                }
            }
            BUILDER.pop();
        }
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private DebugLoggingConfig() {
    }

    public static boolean isEnabled(Feature feature) {
        if (feature == null) {
            return false;
        }
        ForgeConfigSpec.BooleanValue value = FEATURES.get(feature);
        if (value == null) {
            return false;
        }
        try {
            return value.get();
        } catch (IllegalStateException | NullPointerException ignored) {
            return false;
        }
    }
}
