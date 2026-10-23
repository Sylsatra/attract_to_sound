package com.example.soundattract.config.separate;

import java.util.Arrays;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class LivingWallConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_LIVING_WALL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MOTION_DIAGNOSTICS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ELIGIBLE_MOB_ENTITY_IDS;
    public static final ForgeConfigSpec.IntValue MAX_HEIGHT;
    public static final ForgeConfigSpec.IntValue MAX_GAP;
    public static final ForgeConfigSpec.IntValue MAX_BODIES;
    public static final ForgeConfigSpec.IntValue ASSEMBLY_SLOT_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_ASSEMBLY_SLOTS;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_ASSEMBLY_RELEASES;
    public static final ForgeConfigSpec.IntValue MAX_SAFE_HOLDING_DROP;
    public static final ForgeConfigSpec.DoubleValue CLIMB_SPEED_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue TILT_SPEED_RADIANS_PER_UPDATE;
    public static final ForgeConfigSpec.DoubleValue MAX_DROP_BRIDGE_LENGTH;
    public static final ForgeConfigSpec.DoubleValue MAX_ROPE_BRIDGE_LENGTH;
    public static final ForgeConfigSpec.DoubleValue MAX_RECRUITMENT_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue MAX_PILLAR_TILT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ARTICULATED_BRIDGES;
    public static final ForgeConfigSpec.IntValue MAX_BRIDGE_BENDS;
    public static final ForgeConfigSpec.DoubleValue MIN_BEND_ANGLE_DEGREES;
    public static final ForgeConfigSpec.IntValue OBSTACLE_DEFLECTION_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CLUSTER_SPATIAL_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CLUSTER_VERTICAL_DELTA;
    public static final ForgeConfigSpec.IntValue MAX_CANDIDATES_PER_SCAN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BASIN_EVACUATION;
    public static final ForgeConfigSpec.IntValue BASIN_FAILURE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue BASIN_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue ASSEMBLY_TIMEOUT_TICKS;

    static {
        BUILDER.comment("Living wall siege traversal").push("living_wall");
        ENABLE_LIVING_WALL = BUILDER.comment(
                "Master switch for the living wall bridge system.",
                "Build automatically when enough eligible mobs cluster near a wall or hazard.")
            .define("enableLivingWall", true);
        ENABLE_MOTION_DIAGNOSTICS = BUILDER.comment(
                "Enable bounded server-side diagnostics for structural members that fail to reach the bridge foundation.",
                "Logs movement/navigation samples and push requests; observations do not prove causation.")
            .define("enableMotionDiagnostics", false);
        ELIGIBLE_MOB_ENTITY_IDS = BUILDER.comment(
                "Entity ids allowed to participate in the living wall.",
                "Use exact ids like 'minecraft:zombie' or namespace wildcards like 'minecraft:*'.")
            .defineList("eligibleMobEntityIds", Arrays.asList("minecraft:zombie", "minecraft:husk", "minecraft:drowned"), value -> value instanceof String);
        MAX_HEIGHT = BUILDER.comment(
                "Maximum height of the living wall.",
                "The scan remains bounded even when this value is increased.")
            .defineInRange("maxHeight", 12, 1, 64);
        MAX_GAP = BUILDER.comment(
                "Maximum horizontal distance the living wall may cover.",
                "The wall scanner stops at this distance and never searches the wider world.")
            .defineInRange("maxGap", 12, 1, 48);
        MAX_BODIES = BUILDER.comment("Maximum mobs committed to one living wall.")
            .defineInRange("maxBodies", 48, 3, 128);
        ASSEMBLY_SLOT_RADIUS = BUILDER.comment(
                "Radius around the pivot used to generate broad assembly slots.",
                "These slots stay separate from the narrow intake staging cells.")
            .defineInRange("assemblySlotRadius", 8, 2, 24);
        MAX_ASSEMBLY_SLOTS = BUILDER.comment(
                "Maximum number of broad assembly slots the allocator may keep per formation.",
                "The allocator is bounded so path probing stays predictable.")
            .defineInRange("maxAssemblySlots", 16, 1, 64);
        MAX_ACTIVE_ASSEMBLY_RELEASES = BUILDER.comment(
                "Maximum workers that may be actively pulled toward assembly slots at the same time.",
                "Higher values build faster but can increase clumping, collision pressure, and pathfinding cost.",
                "Default 2 preserves the old conservative behavior; 4 is a reasonable faster test value.")
            .defineInRange("maxActiveAssemblyReleases", 2, 1, 8);
        MAX_SAFE_HOLDING_DROP = BUILDER.comment(
                "Maximum vertical drop between consecutive navigation nodes while a mob walks to a holding cell.",
                "A value of 5 permits short drops while still rejecting paths that approach deep cliffs.",
                "This does not override lava, hazard, unsupported-ground, or unsafe-intermediate-cell checks.")
            .defineInRange("maxSafeHoldingDrop", 5, 0, 32);
        CLIMB_SPEED_BLOCKS_PER_TICK = BUILDER.comment(
                "Speed used while a recruited mob is raised into its construction slot.",
                "Measured in blocks per server tick; lower values look slower and reduce sudden pulls.")
            .defineInRange("climbSpeedBlocksPerTick", 0.18D, 0.01D, 1.0D);
        TILT_SPEED_RADIANS_PER_UPDATE = BUILDER.comment(
                "Angular progress per tilt update while each layer shifts toward the bridge endpoint.",
                "Lower values make both ordinary tilts and drop bridges take longer.")
            .defineInRange("tiltSpeedRadiansPerUpdate", 0.025D, 0.001D, 0.25D);
        MAX_DROP_BRIDGE_LENGTH = BUILDER.comment(
                "Maximum three-dimensional length of a drop bridge when the lower side has no touching exit.",
                "Measured in blocks and bounded by the scanner maximum gap and height.")
            .defineInRange("maxDropBridgeLength", 16.0D, 1.0D, 64.0D);
        MAX_ROPE_BRIDGE_LENGTH = BUILDER.comment(
                "Maximum length of a bridge that reaches a touching ledge or short drop exit.",
                "Measured in blocks and bounded by the scanner maximum gap.")
            .defineInRange("maxRopeBridgeLength", 12.0D, 1.0D, 64.0D);
        MAX_RECRUITMENT_DISTANCE = BUILDER.comment(
                "Maximum distance from the committed bridge roster origin for recruitment.",
                "Captured when admission begins and not changed during execution.")
            .defineInRange("maxRecruitmentDistance", 64.0D, 1.0D, 128.0D);
        MAX_PILLAR_TILT_DISTANCE = BUILDER.comment(
                "Maximum horizontal distance in blocks from pivot to wall anchor for pillar tilt living wall construction.",
                "Mobs within this distance can initiate a pillar tilt living wall.")
            .defineInRange("maxPillarTiltDistance", 6.25D, 1.0D, 32.0D);
        ENABLE_ARTICULATED_BRIDGES = BUILDER.comment(
                "Allow living bridges to articulate and bend around obstacle corners instead of collapsing on collision.")
            .define("enableArticulatedBridges", true);
        MAX_BRIDGE_BENDS = BUILDER.comment(
                "Maximum number of articulated bends (elbow joints) permitted in a single living bridge.")
            .defineInRange("maxBridgeBends", 2, 1, 6);
        MIN_BEND_ANGLE_DEGREES = BUILDER.comment(
                "Minimum interior angle (in degrees) at a bridge bend to prevent sharp folds and self-intersection.")
            .defineInRange("minBendAngleDegrees", 75.0D, 45.0D, 160.0D);
        OBSTACLE_DEFLECTION_RADIUS = BUILDER.comment(
                "Maximum block radius to search for a tangent clear air slot when deflecting around an obstacle.")
            .defineInRange("obstacleDeflectionRadius", 2, 1, 4);
        CLUSTER_SPATIAL_RADIUS = BUILDER.comment(
                "Horizontal distance in blocks used to link neighboring mobs into distinct spatial clusters.")
            .defineInRange("clusterSpatialRadius", 12.0D, 4.0D, 32.0D);
        CLUSTER_VERTICAL_DELTA = BUILDER.comment(
                "Maximum vertical elevation difference in blocks to group mobs into the same cluster.",
                "Separates cave mobs from ledge or cliff mobs above.")
            .defineInRange("clusterVerticalDelta", 5.0D, 2.0D, 16.0D);
        MAX_CANDIDATES_PER_SCAN = BUILDER.comment(
                "Maximum candidate mobs sampled across players before spatial clustering partitioning.")
            .defineInRange("maxCandidatesPerScan", 128, 32, 256);
        ENABLE_BASIN_EVACUATION = BUILDER.comment(
                "Evacuate mobs from dead-end basins and caves when bridge admission repeatedly fails.")
            .define("enableBasinEvacuation", true);
        BASIN_FAILURE_THRESHOLD = BUILDER.comment(
                "Consecutive failed bridge admission attempts before a basin is marked unviable.")
            .defineInRange("basinFailureThreshold", 3, 1, 10);
        BASIN_COOLDOWN_TICKS = BUILDER.comment(
                "Ticks a failed basin remains in evacuation cooldown before allowing new bridge attempts.")
            .defineInRange("basinCooldownTicks", 400, 100, 2400);
        ASSEMBLY_TIMEOUT_TICKS = BUILDER.comment(
                "Timeout in server ticks before an assembling bridge collapses if workers do not arrive in time.",
                "Default is 2400 ticks (120 seconds). Increase if mobs take longer to navigate over rough terrain.")
            .defineInRange("assemblyTimeoutTicks", 2400, 100, 7200);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private LivingWallConfig() {
    }
}