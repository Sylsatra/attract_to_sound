package com.example.soundattract.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AWARENESS_HUD_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MOB_ALERTS_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    public static final ForgeConfigSpec.IntValue HUD_OPACITY;
    public static final ForgeConfigSpec.ConfigValue<String> HUD_ANCHOR;
    public static final ForgeConfigSpec.EnumValue<HudPosition> HUD_POSITION;
    public static final ForgeConfigSpec.IntValue HUD_OFFSET_X;
    public static final ForgeConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ForgeConfigSpec.BooleanValue REDUCED_ANIMATION;
    public static final ForgeConfigSpec.BooleanValue SHOW_VISIBILITY;
    public static final ForgeConfigSpec.BooleanValue SHOW_ENVIRONMENTAL_NOISE;
    public static final ForgeConfigSpec.BooleanValue SHOW_SCENT;
    public static final ForgeConfigSpec.BooleanValue SHOW_WIND_SHIELD;
    public static final ForgeConfigSpec.DoubleValue MOB_ALERT_SCALE;
    public static final ForgeConfigSpec.DoubleValue MOB_ALERT_HEIGHT_OFFSET;
    public static final ForgeConfigSpec.DoubleValue MOB_ALERT_MAX_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue SHOW_PROXIMITY_ALERT;
    public static final ForgeConfigSpec.BooleanValue SHOW_DETECTED_ALERT;
    public static final ForgeConfigSpec.BooleanValue SHOW_NEAR_MISS_ALERT;
    public static final ForgeConfigSpec.BooleanValue SHOW_SCENT_ALERT;
    public static final ForgeConfigSpec.BooleanValue SHOW_RAID_ALERT;
    public static final ForgeConfigSpec.BooleanValue SHOW_OTHER_ALERT;

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
        SHOW_VISIBILITY = BUILDER.define("showVisibility", true);
        SHOW_ENVIRONMENTAL_NOISE = BUILDER.define("showEnvironmentalNoise", true);
        SHOW_SCENT = BUILDER.define("showScent", true);
        SHOW_WIND_SHIELD = BUILDER.define("showWindShield", true);
        BUILDER.pop();
        BUILDER.push("mob_alerts");
        MOB_ALERTS_ENABLED = BUILDER.define("enabled", true);
        MOB_ALERT_SCALE = BUILDER.defineInRange("scale", 1.0, 0.25, 4.0);
        MOB_ALERT_HEIGHT_OFFSET = BUILDER.defineInRange("heightOffset", 0.45, 0.0, 4.0);
        MOB_ALERT_MAX_DISTANCE = BUILDER.defineInRange("maxDistance", 48.0, 1.0, 48.0);
        SHOW_PROXIMITY_ALERT = BUILDER.define("showProximity", true);
        SHOW_DETECTED_ALERT = BUILDER.define("showDetected", true);
        SHOW_NEAR_MISS_ALERT = BUILDER.define("showNearMiss", true);
        SHOW_SCENT_ALERT = BUILDER.define("showScent", true);
        SHOW_RAID_ALERT = BUILDER.define("showRaid", true);
        SHOW_OTHER_ALERT = BUILDER.define("showOther", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private ClientConfig() {
    }
}
