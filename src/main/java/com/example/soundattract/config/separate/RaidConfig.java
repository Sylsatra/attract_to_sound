package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class RaidConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue EDGE_MOB_SMART_BEHAVIOR;
    public static final ForgeConfigSpec.IntValue EDGE_MOBS_PER_SECTOR;
    public static final ForgeConfigSpec.DoubleValue GROUP_SPRINT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue LEADER_RETURN_ARRIVAL_DISTANCE;
    public static final ForgeConfigSpec.IntValue RAID_COUNTDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_LEADER_SPREAD_OUT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue RAID_LEADER_ONLY_SOUND_SCAN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SOUND_RAID;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_RAID;
    public static final ForgeConfigSpec.BooleanValue ENABLE_RAID_REINFORCEMENTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RAID_REINFORCEMENT_CAPS;
    public static final ForgeConfigSpec.IntValue RAID_MAX_TOTAL_MOBS;
    public static final ForgeConfigSpec.DoubleValue RAID_REINFORCEMENT_DIFFICULTY_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue RAID_REINFORCEMENT_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<String> RAID_EDGE_DETECTION_ACTION;
    public static final ForgeConfigSpec.BooleanValue RAID_FOLLOWER_INHERIT_TARGET;

    static {
        BUILDER.comment("Sound Attract Mod - Raid & Group Behavior Configuration").push("raid");

        BUILDER.push("group_roles");
        EDGE_MOB_SMART_BEHAVIOR = BUILDER.comment(
                        "Enable the leader/edge/follower role system for mob groups.",
                        "ON: Mobs are assigned roles: leaders attract to sounds, edge mobs scout and relay info, followers follow the leader.",
                        "     Edge mobs suppress their own detection to relay data back to the leader instead of attacking directly.",
                        "     During raids, only edge mobs can trigger scent-based raids and edge detection actions apply.",
                        "OFF: All edge and leader mobs chase sounds using AttractionGoal. Any mob can trigger scent raids.")
                .define("edgeMobSmartBehavior", false);
        EDGE_MOBS_PER_SECTOR = BUILDER.comment("Number of edge mobs per sector around the leader.")
                .defineInRange("edgeMobsPerSector", 1, 1, 64);
        BUILDER.pop();

        BUILDER.push("movement");
        GROUP_SPRINT_MULTIPLIER = BUILDER.comment("Speed multiplier when mobs sprint to rally to their leader during raid countdown.")
                .defineInRange("sprintMultiplier", 1.1, 1.0, 5.0);
        LEADER_RETURN_ARRIVAL_DISTANCE = BUILDER.comment("Distance at which an edge mob considers itself 'arrived' back at the leader.")
                .defineInRange("leaderReturnArrivalDistance", 2.0, 0.5, 16.0);
        FOLLOW_LEADER_SPREAD_OUT_DISTANCE = BUILDER.comment("Distance followers spread out from the sound target after arriving.")
                .defineInRange("followLeaderSpreadOutDistance", 24.0, 0.0, 256.0);
        BUILDER.pop();

        BUILDER.push("sound_trigger");
        ENABLE_SOUND_RAID = BUILDER.comment("Allow sounds detected by mobs to trigger raids (rally group and advance).")
                .define("enableSoundRaid", true);
        BUILDER.pop();

        BUILDER.push("scent_trigger");
        ENABLE_SCENT_RAID = BUILDER.comment("Allow scent trails to trigger raids when a mob reaches the trail end.")
                .define("enableScentRaid", false);
        BUILDER.pop();

        BUILDER.push("countdown");
        RAID_COUNTDOWN_TICKS = BUILDER.comment("Ticks between raid scheduling and raid advance (countdown / rally phase).")
                .defineInRange("raidCountdownTicks", 100, 20, 72000);
        BUILDER.pop();

        BUILDER.push("optimization");
        RAID_LEADER_ONLY_SOUND_SCAN = BUILDER.comment("During raid advance, only the leader scans for new sounds. Followers skip sound scanning entirely.")
                .define("raidLeaderOnlySoundScan", true);
        RAID_FOLLOWER_INHERIT_TARGET = BUILDER.comment("During raid advance, non-edge followers skip full detection and inherit target from edge mob or leader.")
                .define("raidFollowerInheritTarget", true);
        RAID_EDGE_DETECTION_ACTION = BUILDER.comment("Action when an edge mob detects a player during raid advance. 'redirect' = update raid target, 'share' = only set target for nearby mobs.")
                .define("raidEdgeDetectionAction", "redirect");
        BUILDER.pop();

        BUILDER.push("reinforcements");
        ENABLE_RAID_REINFORCEMENTS = BUILDER.comment("Enable reinforcement spawning during raid countdown phase.")
                .define("enableRaidReinforcements", false);
        RAID_REINFORCEMENT_CAPS = BUILDER.comment("Per-entity-type reinforcement caps. Format: \"modid:entity,maxCount\". Example: [\"minecraft:zombie,50\", \"minecraft:skeleton,20\"]")
                .defineList("raidReinforcementCaps", Arrays.asList("minecraft:zombie,50", "minecraft:skeleton,20"), e -> e instanceof String);
        RAID_MAX_TOTAL_MOBS = BUILDER.comment("Maximum total reinforcement mobs per raid.")
                .defineInRange("raidMaxTotalMobs", 80, 0, 500);
        RAID_REINFORCEMENT_DIFFICULTY_MULTIPLIER = BUILDER.comment("Multiplier applied to per-type caps based on difficulty. E.g. Easy=0.5, Normal=1.0, Hard=1.5.")
                .defineInRange("raidReinforcementDifficultyMultiplier", 1.0, 0.0, 10.0);
        RAID_REINFORCEMENT_INTERVAL = BUILDER.comment("Ticks between reinforcement spawn attempts during a raid.")
                .defineInRange("raidReinforcementInterval", 1, 1, 1200);
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
