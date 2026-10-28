package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;

public class PathfindingConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_FLOW_FIELD;
    public static final ForgeConfigSpec.IntValue FLOW_FIELD_MOB_THRESHOLD;
    public static final ForgeConfigSpec.IntValue MOVE_TO_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue MOVE_TO_MIN_DELTA;
    public static final ForgeConfigSpec.IntValue MOVE_TO_TEAM_BUDGET_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_PATH_ATTEMPTS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_NODE_ROUTER;

    static {
        BUILDER.comment("Sound Attract Mod - Pathfinding Configuration").push("pathfinding");

        BUILDER.push("flow_field");
        ENABLE_FLOW_FIELD = BUILDER.comment("Enable collaborative diffusion flow field for large group movement (raids and normal hordes).")
                .define("enableFlowField", true);
        FLOW_FIELD_MOB_THRESHOLD = BUILDER.comment("Minimum number of mobs in a group to activate flow field pathfinding instead of individual A*.")
                .defineInRange("flowFieldMobThreshold", 15, 1, 200);
        BUILDER.pop();

        BUILDER.push("nav_limiter");
        MOVE_TO_COOLDOWN_TICKS = BUILDER.comment("Minimum ticks between moveTo calls per mob. Prevents excessive pathfinding recalculations.")
                .defineInRange("moveToCooldownTicks", 5, 0, 100);
        MOVE_TO_MIN_DELTA = BUILDER.comment("Minimum distance change before issuing a new moveTo. Prevents redundant path updates.")
                .defineInRange("moveToMinDelta", 0.5, 0.0, 10.0);
        MOVE_TO_TEAM_BUDGET_PER_TICK = BUILDER.comment("Maximum moveTo calls per group per tick. 0 = unlimited.")
                .defineInRange("moveToTeamBudgetPerTick", 8, 0, 200);
        BUILDER.pop();

        BUILDER.push("scheduling");
        MAX_PATH_ATTEMPTS_PER_TICK = BUILDER.comment("Maximum pathfinding attempts per dimension per tick. Caps total path computation cost.")
                .defineInRange("maxPathAttemptsPerTick", 10, 0, 200);
        BUILDER.pop();

        BUILDER.push("node_router");
        ENABLE_NODE_ROUTER = BUILDER.comment("Enable NodeRouter waypoint selection for smarter pathfinding with ray penalties and visibility bonuses.")
                .define("enableNodeRouter", true);
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
