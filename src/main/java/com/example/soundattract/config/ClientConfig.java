package com.example.soundattract.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue AWARENESS_HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue MOB_ALERTS_ENABLED;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.IntValue HUD_OPACITY;
    public static final ModConfigSpec.ConfigValue<String> HUD_ANCHOR;
    public static final ModConfigSpec.EnumValue<HudPosition> HUD_POSITION;
    public static final ModConfigSpec.IntValue HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ModConfigSpec.BooleanValue REDUCED_ANIMATION;
    public static final ModConfigSpec.BooleanValue SHOW_VISIBILITY;
    public static final ModConfigSpec.BooleanValue SHOW_ENVIRONMENTAL_NOISE;
    public static final ModConfigSpec.BooleanValue SHOW_SCENT;
    public static final ModConfigSpec.BooleanValue SHOW_WIND_SHIELD;
    public static final ModConfigSpec.DoubleValue MOB_ALERT_SCALE;
    public static final ModConfigSpec.DoubleValue MOB_ALERT_HEIGHT_OFFSET;
    public static final ModConfigSpec.DoubleValue MOB_ALERT_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue SHOW_PROXIMITY_ALERT;
    public static final ModConfigSpec.BooleanValue SHOW_DETECTED_ALERT;
    public static final ModConfigSpec.BooleanValue SHOW_NEAR_MISS_ALERT;
    public static final ModConfigSpec.BooleanValue SHOW_SCENT_ALERT;
    public static final ModConfigSpec.BooleanValue SHOW_RAID_ALERT;
    public static final ModConfigSpec.BooleanValue SHOW_OTHER_ALERT;

    public enum HudPosition {
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    static {
        BUILDER.push("awareness_hud");
        AWARENESS_HUD_ENABLED = BUILDER.define("enabled", true);
        HUD_SCALE = BUILDER.defineInRange("scale", 1.0, 0.5, 2.0);
        HUD_OPACITY = BUILDER.defineInRange("opacity", 190, 40, 255);
        HUD_ANCHOR = BUILDER.define("anchor", "AIR_LEVEL");
        HUD_POSITION = BUILDER.defineEnum("position", HudPosition.BOTTOM_LEFT);
        HUD_OFFSET_X = BUILDER.defineInRange("offsetX", 0, 0, 4096);
        HUD_OFFSET_Y = BUILDER.defineInRange("offsetY", 0, 0, 4096);
        REDUCED_ANIMATION = BUILDER.define("reducedAnimation", false);
        SHOW_VISIBILITY = BUILDER.comment("Show the visibility awareness icon.").define("showVisibility", true);
        SHOW_ENVIRONMENTAL_NOISE = BUILDER.comment("Show the environmental noise awareness icon.")
                .define("showEnvironmentalNoise", true);
        SHOW_SCENT = BUILDER.comment("Show the scent awareness icon.").define("showScent", true);
        SHOW_WIND_SHIELD = BUILDER.comment("Show the wind shield awareness icon.").define("showWindShield", true);
        BUILDER.pop();
        BUILDER.push("mob_alerts");
        MOB_ALERTS_ENABLED = BUILDER.define("enabled", true);
        MOB_ALERT_SCALE = BUILDER.defineInRange("scale", 1.0, 0.25, 4.0);
        MOB_ALERT_HEIGHT_OFFSET = BUILDER.defineInRange("heightOffset", 0.45, 0.0, 4.0);
        MOB_ALERT_MAX_DISTANCE = BUILDER.defineInRange("maxDistance", 48.0, 1.0, 48.0);
        SHOW_PROXIMITY_ALERT = BUILDER.comment("Show proximity mob alert icons.").define("showProximity", true);
        SHOW_DETECTED_ALERT = BUILDER.comment("Show detected mob alert icons.").define("showDetected", true);
        SHOW_NEAR_MISS_ALERT = BUILDER.comment("Show near-miss mob alert icons.").define("showNearMiss", true);
        SHOW_SCENT_ALERT = BUILDER.comment("Show scent mob alert icons.").define("showScent", true);
        SHOW_RAID_ALERT = BUILDER.comment("Show raid mob alert icons.").define("showRaid", true);
        SHOW_OTHER_ALERT = BUILDER.comment("Show fallback icons for unrecognized mob alert types.")
                .define("showOther", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private ClientConfig() {
    }
}
