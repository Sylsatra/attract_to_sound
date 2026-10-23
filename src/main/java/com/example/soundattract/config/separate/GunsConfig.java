package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public class GunsConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;


    public static final ForgeConfigSpec.BooleanValue ENABLE_TACZ_INTEGRATION;
    public static final ForgeConfigSpec.DoubleValue TACZ_RELOAD_RANGE;
    public static final ForgeConfigSpec.DoubleValue TACZ_RELOAD_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue TACZ_SHOOT_RANGE;
    public static final ForgeConfigSpec.DoubleValue TACZ_SHOOT_WEIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TACZ_GUN_SHOOT_DECIBELS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TACZ_ATTACHMENT_REDUCTIONS;
    public static final ForgeConfigSpec.DoubleValue TACZ_ATTACHMENT_REDUCTION_DEFAULT;
    public static final ForgeConfigSpec.DoubleValue GUNSHOT_BASE_DETECTION_RANGE;
    public static final ForgeConfigSpec.IntValue GUNSHOT_DETECTION_DURATION_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TACZ_MUZZLE_FLASH_REDUCTIONS;
    public static final ForgeConfigSpec.DoubleValue TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT;


    public static final ForgeConfigSpec.BooleanValue ENABLE_POINT_BLANK_INTEGRATION;
    public static final ForgeConfigSpec.DoubleValue POINT_BLANK_RELOAD_RANGE;
    public static final ForgeConfigSpec.DoubleValue POINT_BLANK_RELOAD_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue POINT_BLANK_SHOOT_RANGE;
    public static final ForgeConfigSpec.DoubleValue POINT_BLANK_SHOOT_WEIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> POINT_BLANK_GUN_SHOOT_RANGES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> POINT_BLANK_ATTACHMENT_SOUND_REDUCTIONS;
    public static final ForgeConfigSpec.DoubleValue POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> POINT_BLANK_MUZZLE_FLASH_REDUCTIONS;


    static {
        BUILDER.comment("Sound Attract Mod - Guns Configuration").push("guns");

        BUILDER.comment("Tacz Integration Configuration").push("tacz");
        ENABLE_TACZ_INTEGRATION = BUILDER.comment(
                        "[DEPRECATED as of 6.3.3] Moved to soundattract/server-rules.toml (SERVER config).",
                        "The value here is no longer read at runtime; kept only so existing config files do not error.")
                .define("enableTaczIntegration", true);
        TACZ_RELOAD_RANGE = BUILDER.defineInRange("taczReloadRange", 9, 1.0, 128.0);
        TACZ_RELOAD_WEIGHT = BUILDER.defineInRange("taczReloadWeight", 1.0, 0.0, 10.0);
        TACZ_SHOOT_RANGE = BUILDER.defineInRange("taczShootRange", 140.0, 1.0, 256.0);
        TACZ_SHOOT_WEIGHT = BUILDER.defineInRange("taczShootWeight", 15.0, 0.0, 10.0);
        TACZ_GUN_SHOOT_DECIBELS = BUILDER.comment("Tacz gun shoot decibels. Format: 'modid:item;decibels'")
                .defineList("taczGunShootDecibels", java.util.Arrays.asList(
                        "suffuse:aks74u;157.0", "suffuse:python;155.0", "suffuse:tec9;160.0",
                        "suffuse:tt33;158.0", "tacz:deagle_golder;164.0", "suffuse:tti2011;158.0",
                        "tacz:m1911;157.0", "suffuse:trapper50cal;172.0", "tacz:deagle;164.0",
                        "tacz:cz75;157.0", "tacz:p320;157.0", "suffuse:viper2011;158.0",
                        "tacz:m700;160.0", "tacz:m107;171.0", "tacz:m95;172.0",
                        "tacz:ai_awp;170.0", "suffuse:aw50;173.0", "suffuse:gm6;172.0",
                        "suffuse:m200;173.0", "suffuse:xm7;165.0", "suffuse:qbu191;164.0",
                        "suffuse:n4;161.0", "suffuse:qbz951;160.0", "suffuse:ash12;165.0",
                        "suffuse:qbz951s;160.0", "suffuse:qbz192;159.0",
                        "suffuse:an94;161.0", "tacz:sks_tactical;159.0", "tacz:ak47;159.0",
                        "tacz:type_81;158.0", "tacz:qbz_95;160.0", "tacz:hk416d;161.0",
                        "tacz:m4a1;159.0", "tacz:m16a1;159.0", "tacz:hk_g3;161.0",
                        "tacz:m16a4;159.0", "tacz:mk14;162.0", "tacz:scar_l;161.0",
                        "tacz:scar_h;162.0", "tacz:aug;160.0", "tacz:db_short;165.0",
                        "tacz:db_long;166.0", "tacz:m870;165.0", "tacz:aa12;161.0",
                        "tacz:ump45;158.0", "tacz:hk_mp5a5;158.0",
                        "tacz:uzi;157.0", "suffuse:pp19;157.0", "tacz:vector45;158.0",
                        "tacz:p90;156.0", "tacz:rpg7;180.0", "tacz:m320;172.0",
                        "suffuse:m79;172.0", "suffuse:pkp;165.0", "tacz:m249;165.0",
                        "tacz:rpk;164.0", "tacz:minigun:165.0", "tacz:g36k;135.0",
                        "tacz:spr15hb;140.0", "tacz:springfield1873:161.0", "tacz:b93r;125.0",
                        "tacz:glock_17;125.0"
                ), obj -> obj instanceof String && ((String) obj).contains(";"));
        TACZ_ATTACHMENT_REDUCTIONS = BUILDER.comment("Tacz attachment sound reduction. Positive values decrease range (silence), negative values increase range. Format: 'modid:item;reduction'")
                .defineList("taczAttachmentReductions", java.util.Arrays.asList(
                        "tacz:muzzle_brake_cthulhu;-3.0",
                        "tacz:muzzle_brake_pioneer;-3.0",
                        "tacz:muzzle_brake_cyclone_d2;-3.0",
                        "tacz:muzzle_brake_trex;-5.0",
                        "tacz:muzzle_silencer_mirage;35.0",
                        "tacz:muzzle_silencer_vulture;45.0",
                        "tacz:muzzle_silencer_knight_qd;40.0",
                        "tacz:muzzle_silencer_ursus;30.0",
                        "tacz:muzzle_silencer_ptilopsis;30.0",
                        "tacz:muzzle_silencer_phantom_s1;30.0",
                        "tacz:muzzle_compensator_trident;-2.0",
                        "tacz:deagle_golden_long_barrel;20.0"
                ), obj -> obj instanceof String && ((String) obj).contains(";"));
        TACZ_ATTACHMENT_REDUCTION_DEFAULT = BUILDER.defineInRange("taczAttachmentReductionDefault", 20.0, -300.0, 300.0);
        
        GUNSHOT_BASE_DETECTION_RANGE = BUILDER.comment("Base visual detection range when gunshot occurs.")
                .defineInRange("gunshotBaseDetectionRange", 128.0, 16.0, 512.0);
        GUNSHOT_DETECTION_DURATION_TICKS = BUILDER.comment("Duration of increased detection from gunshot (ticks).")
                .defineInRange("gunshotDetectionDurationTicks", 60, 1, 200);
        
        TACZ_MUZZLE_FLASH_REDUCTIONS = BUILDER.comment("Tacz attachment VISUAL FLASH reduction. Positive values decrease detection distance, negative values increase it. Format: 'modid:item;reduction'")
                .defineList("taczMuzzleFlashReductions", java.util.Arrays.asList(
                        "tacz:muzzle_silencer_mirage;100.0",
                        "tacz:muzzle_silencer_vulture;110.0",
                        "tacz:muzzle_silencer_knight_qd;105.0",
                        "tacz:muzzle_silencer_ursus;90.0",
                        "tacz:muzzle_silencer_ptilopsis;90.0",
                        "tacz:muzzle_silencer_phantom_s1;90.0",
                        "tacz:muzzle_brake_cthulhu;-10.0",
                        "tacz:muzzle_brake_pioneer;-10.0",
                        "tacz:muzzle_brake_cyclone_d2;-10.0",
                        "tacz:muzzle_brake_trex;-15.0",
                        "tacz:muzzle_compensator_trident;-5.0"
                ), obj -> obj instanceof String && ((String) obj).contains(";"));
        TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT = BUILDER.defineInRange("taczAttachmentFlashReductionDefault", 0.0, -300.0, 300.0);
        BUILDER.pop();

        BUILDER.comment("Point Blank Integration Configuration").push("pointblank");
        ENABLE_POINT_BLANK_INTEGRATION = BUILDER.comment(
                        "[DEPRECATED as of 6.3.3] Moved to soundattract/server-rules.toml (SERVER config).",
                        "The value here is no longer read at runtime; kept only so existing config files do not error.")
                .define("enablePointBlankIntegration", true);
        POINT_BLANK_RELOAD_RANGE = BUILDER.defineInRange("pointBlankReloadRange", 9, 1.0, 128.0);
        POINT_BLANK_RELOAD_WEIGHT = BUILDER.defineInRange("pointBlankReloadWeight", 1.0, 0.0, 10.0);
        POINT_BLANK_SHOOT_RANGE = BUILDER.defineInRange("pointBlankShootRange", 140.0, 1.0, 256.0);
        POINT_BLANK_SHOOT_WEIGHT = BUILDER.defineInRange("pointBlankShootWeight", 15.0, 0.0, 10.0);
        POINT_BLANK_GUN_SHOOT_RANGES = BUILDER.comment("Point Blank gun shoot ranges. Format: 'modid:item;range'")
                .defineList("pointBlankGunShootRanges", java.util.Arrays.asList(
                        "pointblank:glock17;128.0",
                        "pointblank:glock18;128.0",
                        "pointblank:m9;128.0",
                        "pointblank:m1911a1;128.0",
                        "pointblank:tti_viper;128.0",
                        "pointblank:p30l;128.0",
                        "pointblank:mk23;128.0",
                        "pointblank:deserteagle;140.0",
                        "pointblank:rhino;138.0",
                        "pointblank:m4a1;118.0",
                        "pointblank:m4a1mod1;118.0",
                        "pointblank:star15;90.0",
                        "pointblank:m4sopmodii;118.0",
                        "pointblank:m16a1;90.0",
                        "pointblank:hk416;118.0",
                        "pointblank:scarl;72.0",
                        "pointblank:xm7;118.0",
                        "pointblank:g36c;132.0",
                        "pointblank:g36k;132.0",
                        "pointblank:aug;118.0",
                        "pointblank:g41;118.0",
                        "pointblank:ak74;128.0",
                        "pointblank:ak12;128.0",
                        "pointblank:an94;128.0",
                        "pointblank:ar57;128.0",
                        "pointblank:xm29;128.0",
                        "pointblank:mp5;128.0",
                        "pointblank:mp7;128.0",
                        "pointblank:ro635;119.0",
                        "pointblank:ump45;128.0",
                        "pointblank:vector;90.0",
                        "pointblank:p90;128.0",
                        "pointblank:m950;128.0",
                        "pointblank:tmp;128.0",
                        "pointblank:sl8;128.0",
                        "pointblank:mk14ebr;128.0",
                        "pointblank:uar10;156.0",
                        "pointblank:g3;128.0",
                        "pointblank:wa2000;128.0",
                        "pointblank:xm3;128.0",
                        "pointblank:c14;128.0",
                        "pointblank:l96a1;128.0",
                        "pointblank:ballista;128.0",
                        "pointblank:gm6lynx;128.0",
                        "pointblank:m590;128.0",
                        "pointblank:m870;128.0",
                        "pointblank:spas12;128.0",
                        "pointblank:m1014;128.0",
                        "pointblank:citoricxs;128.0",
                        "pointblank:hs12;128.0",
                        "pointblank:lamg;128.0",
                        "pointblank:mk48;128.0",
                        "pointblank:m249;128.0",
                        "pointblank:m32mgl;128.0",
                        "pointblank:smaw;128.0",
                        "pointblank:at4;128.0",
                        "pointblank:javelin;200.0",
                        "pointblank:m134minigun;156.0",
                        "pointblank:aughbar;128.0",
                        "pointblank:aa12;128.0",
                        "pointblank:ak47;128.0"
                ), obj -> obj instanceof String && ((String) obj).contains(";"));
        POINT_BLANK_ATTACHMENT_SOUND_REDUCTIONS = BUILDER.comment("Point Blank attachment sound reduction. Positive values decrease range (silence), negative values increase range. Format: 'modid:item;reduction'")
                .defineList("pointBlankAttachmentSoundReductions", java.util.Arrays.asList(
                        "pointblank:ar_suppressor;40.0",
                        "pointblank:ar_suppressor_tan;40.0",
                        "pointblank:xm7_suppressor;40.0",
                        "pointblank:ak_suppressor;40.0",
                        "pointblank:smg_suppressor;40.0",
                        "pointblank:rf_suppressor;40.0",
                        "pointblank:hp_suppressor;40.0",
                        "pointblank:sg_suppressor;40.0"
                ), obj -> obj instanceof String && ((String) obj).contains(";"));
        POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT = BUILDER.defineInRange("pointBlankAttachmentReductionDefault", 20.0, -300.0, 300.0);
        POINT_BLANK_MUZZLE_FLASH_REDUCTIONS = BUILDER.comment("Point Blank attachment VISUAL FLASH reduction. Positive values decrease detection distance, negative values increase it. Format: 'modid:item;reduction'")
                .defineList("pointBlankMuzzleFlashReductions", java.util.Arrays.asList(
                        "pointblank:ar_suppressor;90.0",
                        "pointblank:ar_suppressor_tan;90.0",
                        "pointblank:xm7_suppressor;90.0",
                        "pointblank:ak_suppressor;90.0",
                        "pointblank:smg_suppressor;90.0",
                        "pointblank:rf_suppressor;90.0",
                        "pointblank:hp_suppressor;90.0",
                        "pointblank:sg_suppressor;90.0"
                ), obj -> obj instanceof String && ((String) obj).contains(";"));
        BUILDER.pop();

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
