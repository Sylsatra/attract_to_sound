package com.example.soundattract.ai;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("soundattract")
public class LivingWallTraversalRegressionTest {
    @GameTest(template = "empty")
    public void ordinaryTerrainLipDoesNotClassifyAsDropToLedge(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 8, 0, 8);
        ServerLevel level = helper.getLevel();
        BlockPos pivot = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 0, 2));
        setBlock(helper, new BlockPos(3, 0, 2), Blocks.AIR);
        Object scenario = invoke("classifyScenario", new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, Set.class},
                level, pivot, anchor, Set.of(anchor.below()));
        require(!scenario.toString().equals("DROP_TO_LEDGE"),
                "ordinary terrain was classified as DROP_TO_LEDGE: " + scenario);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void randomFlatTerrainDoesNotTriggerBridgeNeed(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 10, 0, 10);
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        BlockPos candidate = helper.absolutePos(new BlockPos(5, 1, 2));

        boolean result = (boolean) invoke("hasBridgeNeed",
                new Class<?>[]{ServerLevel.class, Vec3.class, BlockPos.class},
                level, center, candidate);
        require(!result, "flat terrain incorrectly reported a bridge need");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void diagonalRavineStillTriggersBridgeNeed(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 12, 0, 12);
        ServerLevel level = helper.getLevel();
        for (int step = 3; step <= 6; step++) {
            helper.setBlock(new BlockPos(step, 0, step), Blocks.AIR.defaultBlockState());
            helper.setBlock(new BlockPos(step, -1, step), Blocks.AIR.defaultBlockState());
            helper.setBlock(new BlockPos(step, -2, step), Blocks.AIR.defaultBlockState());
        }
        Vec3 center = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        BlockPos candidate = helper.absolutePos(new BlockPos(7, 1, 7));

        boolean result = (boolean) invoke("hasBridgeNeed",
                new Class<?>[]{ServerLevel.class, Vec3.class, BlockPos.class},
                level, center, candidate);
        require(result, "diagonal ravine was not reported as a bridge need");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void exactSixteenLengthDropPlanRemainsAllowed(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 24, 0, 4);
        ServerLevel level = helper.getLevel();
        BlockPos pivot = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = helper.absolutePos(new BlockPos(18, 1, 2));
        Object scenario = invoke("classifyScenario", new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, Set.class},
                level, pivot, anchor, Set.of(anchor.below()));
        require("JUMP_BRIDGE".equals(scenario.toString()),
                "exact sixteen-block span classified as " + scenario);
        double length = (double) invoke("bridgeLength", new Class<?>[]{BlockPos.class, BlockPos.class}, pivot, anchor);
        require(Math.abs(16.0D - length) <= 1.0E-6D,
                "exact sixteen-block span measured as " + length);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void stalePivotEndpointWouldOvershootWhileCandidatePivotStaysBounded(GameTestHelper helper) throws Exception {
        prepareFlat(helper, -2, 18, -20, 2);
        ServerLevel level = helper.getLevel();
        BlockPos pivot = helper.absolutePos(new BlockPos(0, 1, -5));
        BlockPos staleEndpoint = helper.absolutePos(new BlockPos(7, 2, -18));
        BlockPos corrected = helper.absolutePos(new BlockPos(7, 2, -12));
        double stale = (double) invoke("horizontalDistance", new Class<?>[]{BlockPos.class, BlockPos.class}, pivot, staleEndpoint);
        double bounded = (double) invoke("horizontalDistance", new Class<?>[]{BlockPos.class, BlockPos.class}, pivot, corrected);
        require(stale > 12.0D, "stale endpoint was unexpectedly bounded: " + stale);
        require(bounded <= 12.0D, "candidate endpoint overshot the limit: " + bounded);
        require((boolean) invoke("hasThreatProgressingSpan",
                new Class<?>[]{LivingWallCoordinator.ConstructionScenario.class, BlockPos.class, BlockPos.class, BlockPos.class},
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, pivot, corrected, helper.absolutePos(new BlockPos(12, 1, -12))),
                "corrected endpoint no longer faces the threat");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void staleTicketAndTiltRetentionStayDeterministic(GameTestHelper helper) throws Exception {
        require(LivingWallCoordinator.isStaleRecruitmentCallback(null, 1L),
                "null recruitment callback was not stale");
        require(!LivingWallCoordinator.isStaleRecruitmentCallback(
                new LivingWallCoordinator.MobTicket(java.util.UUID.randomUUID(), new BlockPos(0, 0, 0), 1L), 1L),
                "current recruitment callback was treated as stale");
        require(LivingWallCoordinator.requiresTilt(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE),
                "WALL_LEDGE did not retain tilt behavior");
        require(!LivingWallCoordinator.isDirectSpan(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE),
                "WALL_LEDGE was treated as a direct span");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void crossersCaptureAtBaseBeforeTraversingOntoLiveSupport(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 10, 0, 10);
        Fixture fixture = createFixture(helper, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, true);
        Mob crosser = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 2);
        crosser.setNoGravity(false);
        crosser.noPhysics = false;
        LivingWallCoordinator.SitePlan plan = getField(fixture.formation, "plan");
        Object transit = newCrosserTransit(crosser.getUUID(), plan.intakeWindows().get(0));
        setEnum(transit, "state", "CAPTURE_ENTRY");
        getMap(fixture.formation, "crosserTransit").put(crosser.getUUID(), transit);
        getMap(fixture.formation, "crosserSlots").put(crosser.getUUID(), 0);
        getList(fixture.formation, "crosserOrder").add(crosser.getUUID());
        setInt(fixture.formation, "activeCrossers", 1);
        invoke("driveCrossers", new Class<?>[]{ServerLevel.class, fixture.formation.getClass(), long.class},
                fixture.level, fixture.formation, fixture.level.getGameTime());

        require(crosser.isNoGravity(), "crosser should remain captured: noGravity=false");
        require(crosser.noPhysics, "crosser should remain captured: noPhysics=false");
        double intakeX = Vec3.atBottomCenterOf(plan.intakeWindows().get(0).feederCell()).x;
        double pivotX = Vec3.atBottomCenterOf(getField(fixture.formation, "pivot")).x;
        require(crosser.getX() > intakeX && crosser.getX() < pivotX,
                "capture should make bounded progress toward the pivot instead of teleporting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void tiltExitRejectsAllAirColumnsAndTrapdoorColumns(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 14, 0, 14);
        Fixture fixture = createFixture(helper, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, true);
        BlockPos releaseCell = releaseCell(fixture);
        clearFallColumns(fixture.level, releaseCell);
        Object first = invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        require(first == null, "all-air column resolved: releaseCell=" + releaseCell
                + ", support1=" + fixture.level.getBlockState(releaseCell.below(1))
                + ", support5=" + fixture.level.getBlockState(releaseCell.below(5)));

        Object second;
        for (int z = releaseCell.getZ() - 2; z <= releaseCell.getZ() + 2; z++) {
            fixture.level.setBlock(new BlockPos(releaseCell.getX(), releaseCell.getY() - 5, z),
                    Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);
        }
        second = invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        require(second == null, "trapdoor column resolved: releaseCell=" + releaseCell
                + ", support5=" + fixture.level.getBlockState(releaseCell.below(5)));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void tiltExitCacheInvalidatesWhenLandingSupportChanges(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 14, 0, 14);
        Fixture fixture = createFixture(helper, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, true);
        BlockPos releaseCell = releaseCell(fixture);
        clearFallColumns(fixture.level, releaseCell);
        for (int z = releaseCell.getZ() - 2; z <= releaseCell.getZ() + 2; z++) {
            fixture.level.setBlock(new BlockPos(releaseCell.getX(), releaseCell.getY() - 5, z),
                    Blocks.STONE.defaultBlockState(), 3);
        }

        setLong(fixture.formation, "lastValidationTick", 10L);
        Object first = invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        Object second = invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        require(first != null, "expected first tilt projection: releaseCell=" + releaseCell
                + ", support5=" + fixture.level.getBlockState(releaseCell.below(5))
                + ", pivot=" + getField(fixture.formation, "pivot")
                + ", anchor=" + getField(fixture.formation, "anchor")
                + ", spanEnd=" + getField(fixture.formation, "spanEnd"));
        require(first == second, "expected cached identity: releaseCell=" + releaseCell
                + ", first=" + first + ", second=" + second
                + ", support5=" + fixture.level.getBlockState(releaseCell.below(5)));

        clearFallColumns(fixture.level, releaseCell);
        setLong(fixture.formation, "lastValidationTick", 20L);
        Object invalidated = invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        require(invalidated == null, "changed landing support should invalidate cached tilt projection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void crosserReleaseRestoresOriginalFlagsAndOwnership(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 8, 0, 8);
        Fixture fixture = createFixture(helper, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, false);
        Mob crosser = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        crosser.setNoGravity(true);
        crosser.noPhysics = true;
        installCanonicalCrosserRequest(fixture, crosser);
        boolean reserved = (Boolean) invoke("reserveCrosser", new Class<?>[]{ServerLevel.class, Mob.class, fixture.formation.getClass()},
                fixture.level, crosser, fixture.formation);
        require(reserved, "crosser reservation should succeed with canonical corridor evidence");
        crosser.setNoGravity(false);
        crosser.noPhysics = false;
        invoke("releaseCrosser", new Class<?>[]{ServerLevel.class, fixture.formation.getClass(), java.util.UUID.class},
                fixture.level, fixture.formation, crosser.getUUID());
        require(crosser.isNoGravity(), "release should restore original no-gravity flag");
        require(crosser.noPhysics, "release should restore original no-physics flag");
        require(!getMobOwners(fixture.level).containsKey(crosser.getUUID()),
                "release should clear crosser ownership");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void jumpAndDropScenariosNeverResolveTiltExit(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 14, 0, 14);
        Fixture jump = createFixture(helper, LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, true);
        Fixture drop = createFixture(helper, LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE, true);
        require(invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, jump.formation.getClass()}, jump.level, jump.formation) == null,
                "jump scenario resolved a tilt exit");
        require(invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, drop.formation.getClass()}, drop.level, drop.formation) == null,
                "drop scenario resolved a tilt exit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void farSideJumpDestinationSurvivesDisconnectedBackgroundMass(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 18, 0, 10);
        helper.setBlock(new BlockPos(12, 2, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(12, 3, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(15, 2, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(16, 2, 2), Blocks.STONE.defaultBlockState());
        Fixture jump = createFixture(helper, LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, true);
        Object scenario = invoke("classifyScenario",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, Set.class},
                jump.level, getField(jump.formation, "pivot"), getField(jump.formation, "anchor"), Set.of(new BlockPos(6, 4, 2)));
        require(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE == scenario,
                "far-side jump classified as " + scenario);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void farSideDropDestinationSurvivesDisconnectedBackgroundMass(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 18, 0, 10);
        helper.setBlock(new BlockPos(12, 2, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(12, 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(15, 2, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(16, 2, 2), Blocks.STONE.defaultBlockState());
        Fixture drop = createFixture(helper, LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE, true);
        Object scenario = invoke("classifyScenario",
                new Class<?>[]{ServerLevel.class, BlockPos.class, BlockPos.class, Set.class},
                drop.level, getField(drop.formation, "pivot"), getField(drop.formation, "anchor"), Set.of(new BlockPos(6, 2, 2)));
        require(LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE == scenario,
                "far-side drop classified as " + scenario);
        helper.succeed();
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void prepareFlat(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = -3; y <= 130; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void setBlock(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.Block block) {
        helper.setBlock(pos, block.defaultBlockState());
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    @SuppressWarnings("unchecked")
    private static <T> java.util.Map<java.util.UUID, T> getMap(Object instance, String name) throws Exception {
        return (java.util.Map<java.util.UUID, T>) getField(instance, name);
    }

    @SuppressWarnings("unchecked")
    private static <T> java.util.List<T> getList(Object instance, String name) throws Exception {
        return (java.util.List<T>) getField(instance, name);
    }

    private static void setInt(Object instance, String name, int value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(instance, value);
    }

    private static void setLong(Object instance, String name, long value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(instance, value);
    }

    private static double epsilon() throws Exception {
        Field field = LivingWallCoordinator.class.getDeclaredField("CONTACT_EPSILON");
        field.setAccessible(true);
        return field.getDouble(null);
    }

    private static BlockPos relative(GameTestHelper helper, BlockPos absolute) {
        return new BlockPos(absolute.getX() - helper.absolutePos(BlockPos.ZERO).getX(),
                absolute.getY() - helper.absolutePos(BlockPos.ZERO).getY(),
                absolute.getZ() - helper.absolutePos(BlockPos.ZERO).getZ());
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<java.util.UUID, java.util.UUID> getMobOwners(ServerLevel level) throws Exception {
        Method stateMethod = LivingWallCoordinator.class.getDeclaredMethod("state", ServerLevel.class);
        stateMethod.setAccessible(true);
        Object state = stateMethod.invoke(null, level);
        Field owners = state.getClass().getDeclaredField("mobOwners");
        owners.setAccessible(true);
        return (java.util.Map<java.util.UUID, java.util.UUID>) owners.get(state);
    }

    private static Fixture createFixture(GameTestHelper helper, LivingWallCoordinator.ConstructionScenario scenario,
                                         boolean threatBelow) throws Exception {
        ServerLevel level = helper.getLevel();
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos pivot = helper.absolutePos(new BlockPos(2, 1, 2));
        int anchorY = scenario == LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE
                ? -1 : (threatBelow ? 5 : 1);
        BlockPos anchor = helper.absolutePos(new BlockPos(8, anchorY, 2));
        BlockPos spanEnd = helper.absolutePos(new BlockPos(8, anchorY, 2));
        int threatY = scenario == LivingWallCoordinator.ConstructionScenario.WALL_LEDGE
                ? 1 : (threatBelow ? 0 : 1);
        BlockPos threat = helper.absolutePos(new BlockPos(6, threatY, 2));
        helper.setBlock(new BlockPos(8, anchorY - 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(8, anchorY, 2), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(8, anchorY + 1, 2), Blocks.AIR.defaultBlockState());
        if (scenario == LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE) {
            helper.setBlock(new BlockPos(9, anchorY - 1, 2), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(9, anchorY, 2), Blocks.AIR.defaultBlockState());
            helper.setBlock(new BlockPos(9, anchorY + 1, 2), Blocks.AIR.defaultBlockState());
        }
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                scenario, pivot, anchor, spanEnd, new Vec3(1.0D, 0.0D, 0.0D),
                List.of(pivot),
                List.of(new LivingWallCoordinator.IntakeWindow(pivot.west())),
                List.of(anchor),
                Set.of(pivot),
                List.of(spanEnd));
        Object request = newConstructionRequest(Vec3.atBottomCenterOf(pivot), pivot, threat, anchor, threat, 40L);
        Object formation = newFormation(leader, anchor, plan, request, level.getGameTime());
        setInt(formation, "requiredBodies", 4);
        setInt(formation, "assembledSlots", 4);
        setInt(formation, "columnCount", 1);
        setInt(formation, "layerCount", 4);
        setFieldValue(formation, "phase", enumValue(formation.getClass(), "TRAVERSE"));
        List<Mob> members = new ArrayList<>();
        Method slotMethod = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        slotMethod.setAccessible(true);
        for (int slot = 0; slot < 4; slot++) {
            Vec3 position = (Vec3) slotMethod.invoke(null, formation, slot, 1.0D);
            Mob member = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                    Mth.floor(position.x), Mth.floor(position.y), Mth.floor(position.z));
            member.setPos(position.x, position.y, position.z);
            member.setNoGravity(true);
            member.noPhysics = true;
            members.add(member);
            getList(formation, "members").add(member.getUUID());
            ((Set<java.util.UUID>) getField(formation, "structuralBuilderIds")).add(member.getUUID());
        }
        return new Fixture(level, formation, members);
    }

    private static Object newConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                                                 BlockPos selectedAnchor, BlockPos threatBucket, long revision) throws Exception {
        Class<?> type = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> ctor = type.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class,
                BlockPos.class, Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence"),
                long.class, long.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket, null,
                200L, 200L, revision);
    }

    private static Object newFormation(Mob leader, BlockPos target, LivingWallCoordinator.SitePlan plan, Object request,
                                       long now) throws Exception {
        Class<?> geometryType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$BodyGeometry");
        Constructor<?> geometryCtor = geometryType.getDeclaredConstructor(double.class, double.class);
        geometryCtor.setAccessible(true);
        Object geometry = geometryCtor.newInstance(leader.getBbWidth(), leader.getBbHeight());
        Class<?> formationType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        Constructor<?> ctor = formationType.getDeclaredConstructor(Mob.class, BlockPos.class,
                LivingWallCoordinator.SitePlan.class,
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest"),
                int.class, int.class, int.class, geometryType, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(leader, target, plan, request, 4, 4, 8, geometry, now);
    }

    private static Object enumValue(Class<?> formationType, String name) throws Exception {
        Class<?> phaseType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Phase");
        return java.lang.Enum.valueOf((Class<java.lang.Enum>) phaseType, name);
    }

    private static void setFieldValue(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static BlockPos releaseCell(Fixture fixture) throws Exception {
        Object projection = invoke("resolveTiltThreatExit",
                new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        if (projection == null) {
            Method slotMethod = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                    fixture.formation.getClass(), int.class, double.class);
            slotMethod.setAccessible(true);
            Vec3 fallback = (Vec3) slotMethod.invoke(null, fixture.formation, 1, 1.0D);
            return BlockPos.containing(fallback.x, fallback.y, fallback.z);
        }
        Method waypoint = projection.getClass().getDeclaredMethod("dismountWaypoint");
        waypoint.setAccessible(true);
        Vec3 release = (Vec3) waypoint.invoke(projection);
        return BlockPos.containing(release.x, release.y, release.z);
    }

    private static void clearFallColumns(ServerLevel level, BlockPos releaseCell) {
        for (int z = releaseCell.getZ() - 2; z <= releaseCell.getZ() + 2; z++) {
            for (int fall = 1; fall <= 32; fall++) {
                level.setBlock(new BlockPos(releaseCell.getX(), releaseCell.getY() - fall, z),
                        Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void installCanonicalCrosserRequest(Fixture fixture, Mob crosser) throws Exception {
        LivingWallCoordinator.SitePlan plan = getField(fixture.formation, "plan");
        LivingWallCoordinator.IntakeWindow intake = plan.intakeWindows().get(0);
        BlockPos pivot = getField(fixture.formation, "pivot");
        BlockPos anchor = getField(fixture.formation, "anchor");
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                fixture.level.dimension().location(), LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                pivot, intake.feederApproach(), anchor, anchor, anchor, anchor,
                List.of(), List.of(), List.of(1L));

        Class<?> routeType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$RouteEvidence");
        Constructor<?> routeCtor = routeType.getDeclaredConstructor(java.util.UUID.class,
                LivingWallCoordinator.IntakeWindow.class, BlockPos.class, List.class,
                boolean.class, long.class, String.class, String.class);
        routeCtor.setAccessible(true);
        Object routeEvidence = routeCtor.newInstance(crosser.getUUID(), intake, anchor,
                List.of(intake.feederApproach(), anchor), true, 0L, "regression-test", corridor.corridorId());

        Object oldRequest = getField(fixture.formation, "request");
        Class<?> requestType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestType.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class,
                BlockPos.class, BlockPos.class,
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$TargetScanSnapshot"),
                Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence"),
                routeType, CanonicalFeetCorridor.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        Object request = requestCtor.newInstance(getField(oldRequest, "sourceCenter"), getField(oldRequest, "sourceBucket"),
                getField(oldRequest, "threatDestination"), anchor, getField(oldRequest, "threatBucket"),
                null, null, routeEvidence, corridor, 200L, 200L, 40L);
        setFieldValue(fixture.formation, "request", request);
    }

    private static Object newCrosserTransit(java.util.UUID mobId, LivingWallCoordinator.IntakeWindow intakeWindow) throws Exception {
        Class<?> type = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$CrosserTransit");
        Constructor<?> ctor = type.getDeclaredConstructor(java.util.UUID.class, Class.forName(
                "com.example.soundattract.ai.LivingWallCoordinator$IntakeWindow"));
        ctor.setAccessible(true);
        return ctor.newInstance(mobId, intakeWindow);
    }

    @SuppressWarnings("unchecked")
    private static void setEnum(Object instance, String fieldName, String constantName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<? extends Enum> enumType = (Class<? extends Enum>) field.getType();
        field.set(instance, Enum.valueOf(enumType, constantName));
    }

    private record Fixture(ServerLevel level, Object formation, List<Mob> members) {
    }
}
