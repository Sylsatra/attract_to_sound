package com.example.soundattract.gametest;

import com.example.soundattract.ai.LivingWallCoordinator;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LivingWallAdmissionGameTests {
    public LivingWallAdmissionGameTests() {
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void livingBridgeUpperLayerArticulationPreservesFoundationSlot(GameTestHelper helper) {
        Vec3 foundation = new Vec3(5.5D, -60.0D, -52.5D);
        Vec3 upperLayerBend = new Vec3(4.5D, -56.0D, -52.5D);
        Map<Integer, Vec3> slots = new HashMap<>();
        slots.put(0, foundation);
        try {
            var apply = LivingWallCoordinator.class.getDeclaredMethod("applyArticulationFoundationSlot",
                    Map.class, int.class, int.class, Vec3.class);
            apply.setAccessible(true);
            apply.invoke(null, slots, 3, 1, upperLayerBend);
            Vec3 preserved = slots.get(0);
            if (preserved == null || preserved.distanceToSqr(foundation) > 1.0E-9D || slots.containsKey(3)) {
                helper.fail("Upper-layer route bend replaced the supported foundation slot: " + slots);
                return;
            }
            apply.invoke(null, slots, 0, 1, upperLayerBend);
            Vec3 articulatedFoundation = slots.get(0);
            if (articulatedFoundation == null || articulatedFoundation.distanceToSqr(upperLayerBend) > 1.0E-9D) {
                helper.fail("Foundation-layer articulation did not update its own slot: " + slots);
                return;
            }
            var interpolate = LivingWallCoordinator.class.getDeclaredMethod("interpolateFormationTiltPosition",
                    Vec3.class, Vec3.class, double.class);
            interpolate.setAccessible(true);
            Vec3 verticalPillarPosition = new Vec3(8.5D, -52.0D, -50.5D);
            Vec3 bentWallLanding = new Vec3(6.5D, -56.0D, -51.5D);
            Vec3 beforeTilt = (Vec3) interpolate.invoke(null, verticalPillarPosition, bentWallLanding, 0.0D);
            Vec3 midwayTilt = (Vec3) interpolate.invoke(null, verticalPillarPosition, bentWallLanding, 0.5D);
            Vec3 completedTilt = (Vec3) interpolate.invoke(null, verticalPillarPosition, bentWallLanding, 1.0D);
            if (beforeTilt.distanceToSqr(verticalPillarPosition) > 1.0E-9D
                    || midwayTilt.distanceToSqr(verticalPillarPosition) < 1.0E-6D
                    || midwayTilt.distanceToSqr(bentWallLanding) < 1.0E-6D
                    || completedTilt.distanceToSqr(bentWallLanding) > 1.0E-9D) {
                helper.fail("Articulated wall-ledge tilt did not progress from the vertical pillar to its bent landing: "
                        + beforeTilt + " -> " + midwayTilt + " -> " + completedTilt);
                return;
            }
            var interpolateAnchored = LivingWallCoordinator.class.getDeclaredMethod(
                    "interpolateAnchoredTiltLayerPosition", Vec3.class, Vec3.class,
                    Vec3.class, Vec3.class, double.class);
            interpolateAnchored.setAccessible(true);
            Vec3 parentFinal = new Vec3(10.5D, -60.0D, -50.5D);
            Vec3 movingSource = new Vec3(5.5D, -58.0D, -50.5D);
            Vec3 parentSource = new Vec3(5.5D, -60.0D, -50.5D);
            Vec3 movingDestination = new Vec3(8.5D, -56.0D, -52.5D);
            Vec3 anchoredStart = parentFinal.add(movingSource.subtract(parentSource));
            Vec3 anchoredAtStart = (Vec3) interpolateAnchored.invoke(null, parentFinal, movingSource,
                    parentSource, movingDestination, 0.0D);
            Vec3 anchoredAtHalf = (Vec3) interpolateAnchored.invoke(null, parentFinal, movingSource,
                    parentSource, movingDestination, 0.5D);
            Vec3 anchoredAtEnd = (Vec3) interpolateAnchored.invoke(null, parentFinal, movingSource,
                    parentSource, movingDestination, 1.0D);
            if (anchoredAtStart.distanceToSqr(anchoredStart) > 1.0E-9D
                    || anchoredAtHalf.distanceToSqr(anchoredStart) < 1.0E-6D
                    || anchoredAtHalf.distanceToSqr(movingDestination) < 1.0E-6D
                    || anchoredAtEnd.distanceToSqr(movingDestination) > 1.0E-9D) {
                helper.fail("Next tilt layer did not move from the already anchored parent: "
                        + anchoredAtStart + " -> " + anchoredAtHalf + " -> " + anchoredAtEnd);
                return;
            }

            var alongRoute = LivingWallCoordinator.class.getDeclaredMethod("interpolateArticulatedRoute",
                    List.class, Vec3.class, Vec3.class, double.class, double.class);
            alongRoute.setAccessible(true);
            var alongLayerRoute = LivingWallCoordinator.class.getDeclaredMethod("interpolateArticulatedRouteSegment",
                    List.class, Vec3.class, Vec3.class, double.class, double.class, double.class);
            alongLayerRoute.setAccessible(true);
            List<Vec3> wrapAroundWall = List.of(
                    new Vec3(0.5D, 0.0D, 0.5D),
                    new Vec3(0.5D, 0.0D, 1.5D),
                    new Vec3(2.5D, 0.0D, 1.5D));
            AABB wall = new AABB(1.0D, 0.0D, 0.0D, 2.0D, 1.0D, 1.0D);
            boolean directPathHitsWall = false;
            boolean routedPathHitsWall = false;
            boolean articulatedLayerPathHitsWall = false;
            for (int sample = 0; sample <= 60; sample++) {
                double progress = sample / 60.0D;
                Vec3 direct = wrapAroundWall.get(0).lerp(wrapAroundWall.get(2), progress);
                if (new AABB(direct.x - 0.3D, direct.y, direct.z - 0.3D,
                        direct.x + 0.3D, direct.y + 1.8D, direct.z + 0.3D).intersects(wall)) {
                    directPathHitsWall = true;
                }
                Vec3 routed = (Vec3) alongRoute.invoke(null, wrapAroundWall, wrapAroundWall.get(0),
                        wrapAroundWall.get(2), 1.0D, progress);
                if (new AABB(routed.x - 0.3D, routed.y, routed.z - 0.3D,
                        routed.x + 0.3D, routed.y + 1.8D, routed.z + 0.3D).intersects(wall)) {
                    routedPathHitsWall = true;
                }
                Vec3 layerRouted = (Vec3) alongLayerRoute.invoke(null, wrapAroundWall, wrapAroundWall.get(0),
                        wrapAroundWall.get(2), 0.0D, 1.0D, progress);
                if (new AABB(layerRouted.x - 0.3D, layerRouted.y, layerRouted.z - 0.3D,
                        layerRouted.x + 0.3D, layerRouted.y + 1.8D, layerRouted.z + 0.3D).intersects(wall)) {
                    articulatedLayerPathHitsWall = true;
                }
            }
            if (!directPathHitsWall || routedPathHitsWall || articulatedLayerPathHitsWall) {
                helper.fail("Articulated tilt must follow the bend around the wall; direct collision="
                        + directPathHitsWall + ", routed collision=" + routedPathHitsWall
                        + ", layer route collision=" + articulatedLayerPathHitsWall);
                return;
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not verify Living Bridge articulation slot ownership: " + e);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void livingBridgeDoesNotExemptUnrelatedBlockBesideLanding(GameTestHelper helper) {
        BlockPos pivot = new BlockPos(-4, -60, -49);
        BlockPos spanEnd = new BlockPos(1, -56, -49);
        BlockPos besideLanding = new BlockPos(1, -56, -48);
        BlockPos observedWallFace = new BlockPos(0, -56, -49);
        BlockPos landingSupport = spanEnd.below();
        try {
            var predicate = LivingWallCoordinator.class.getDeclaredMethod("isPlannedTiltContactCell",
                    BlockPos.class, BlockPos.class, BlockPos.class,
                    net.minecraft.world.level.block.state.BlockState.class);
            predicate.setAccessible(true);
            boolean unrelatedCobblestoneAllowed = (boolean) predicate.invoke(null, pivot, spanEnd,
                    besideLanding, Blocks.COBBLESTONE.defaultBlockState());
            boolean observedWallFaceAllowed = (boolean) predicate.invoke(null, pivot, spanEnd,
                    observedWallFace, Blocks.COBBLESTONE.defaultBlockState());
            boolean endpointSupportExemption = (boolean) predicate.invoke(null, pivot, spanEnd,
                    landingSupport, Blocks.COBBLESTONE.defaultBlockState());
            if (unrelatedCobblestoneAllowed || observedWallFaceAllowed || endpointSupportExemption) {
                helper.fail("Living bridge endpoint contact policy was wrong: unrelated cobblestone allowed="
                        + unrelatedCobblestoneAllowed + ", observed wall face allowed=" + observedWallFaceAllowed
                        + ", endpoint support exemption=" + endpointSupportExemption);
                return;
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not verify Living Bridge endpoint contact policy: " + e);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void livingBridgeSupportContactReturnsClearExecutedFeet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlock(support, Blocks.COBBLESTONE.defaultBlockState(), 2);
        AABB embeddedPose = new AABB(support.getX() + 0.2D, support.getY() + 0.4D, support.getZ() + 0.2D,
                support.getX() + 0.8D, support.getY() + 2.35D, support.getZ() + 0.8D);
        try {
            var supportLift = LivingWallCoordinator.class.getDeclaredMethod("findSupportLift",
                    ServerLevel.class, AABB.class, double.class);
            supportLift.setAccessible(true);
            double hypotheticalLift = (double) supportLift.invoke(null, level, embeddedPose, 2.03D);
            if (hypotheticalLift < 0.59D || hypotheticalLift > 0.61D) {
                helper.fail("Regression fixture did not produce the support-lift false-clear: " + hypotheticalLift);
                return;
            }

            var exactPoseCheck = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals("findFormationVolumeBlockage")
                            && method.getParameterCount() == 4)
                    .findFirst().orElseThrow();
            exactPoseCheck.setAccessible(true);
            Class<?> sitePlanType = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("SitePlan"))
                    .findFirst().orElseThrow();
            Class<?> formationType = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("Formation"))
                    .findFirst().orElseThrow();
            Class<?> geometryType = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("BodyGeometry"))
                    .findFirst().orElseThrow();
            Class<?> requestType = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("ConstructionRequest"))
                    .findFirst().orElseThrow();
            Object scenario = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("ConstructionScenario"))
                    .findFirst().orElseThrow().getEnumConstants()[2]; // WALL_LEDGE
            BlockPos pivot = helper.absolutePos(new BlockPos(0, 2, 0));
            BlockPos anchor = helper.absolutePos(new BlockPos(1, 4, 0));
            BlockPos spanEnd = helper.absolutePos(new BlockPos(2, 5, 0));
            var sitePlanConstructor = java.util.Arrays.stream(sitePlanType.getDeclaredConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 10)
                    .findFirst().orElseThrow();
            sitePlanConstructor.setAccessible(true);
            Object sitePlan = sitePlanConstructor.newInstance(scenario, pivot, anchor, spanEnd,
                    net.minecraft.world.phys.Vec3.ZERO, java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.Set.of(), java.util.List.of());
            var geometryConstructor = geometryType.getDeclaredConstructor(double.class, double.class);
            geometryConstructor.setAccessible(true);
            Object geometry = geometryConstructor.newInstance(0.6D, 1.95D);
            Zombie leader = helper.spawn(GameTestEntityTypes.ZOMBIE, 1, 2, 1);
            var formationConstructor = java.util.Arrays.stream(formationType.getDeclaredConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 9
                            && constructor.getParameterTypes()[3] == requestType)
                    .findFirst().orElseThrow();
            formationConstructor.setAccessible(true);
            Object formation = formationConstructor.newInstance(leader, spanEnd, sitePlan, null,
                    4, 3, 8, geometry, level.getGameTime());
            Object plannedPoseBlockage = exactPoseCheck.invoke(null, level, formation, embeddedPose, 0.0D);
            Object liftedPoseBlockage = exactPoseCheck.invoke(null, level, formation,
                    embeddedPose.move(0.0D, hypotheticalLift, 0.0D), 0.0D);
            var resolve = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals("resolveFormationVolumeClearance"))
                    .findFirst().orElseThrow();
            resolve.setAccessible(true);
            Object resolution = resolve.invoke(null, level, formation, embeddedPose, 0.0D);
            var position = resolution.getClass().getDeclaredMethod("position");
            position.setAccessible(true);
            Vec3 feet = (Vec3) position.invoke(resolution);
            if (plannedPoseBlockage != null || liftedPoseBlockage != null || feet == null
                    || Math.abs(feet.y - (support.getY() + 1.0D)) > 1.0E-7D
                    || Math.abs(feet.x - (support.getX() + 0.5D)) > 1.0E-7D
                    || Math.abs(feet.z - (support.getZ() + 0.5D)) > 1.0E-7D) {
                helper.fail("Bridge support contact must return the actual clear feet position, not the box center or embedded source: "
                        + "plannedBlockage=" + plannedPoseBlockage + ", liftedBlockage=" + liftedPoseBlockage + ", feet=" + feet);
                return;
            }
            level.setBlock(support.above(2), Blocks.COBBLESTONE.defaultBlockState(), 2);
            Object ceilingBlockage = exactPoseCheck.invoke(null, level, formation, embeddedPose, 0.0D);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(support, Blocks.LAVA.defaultBlockState(), 2);
            Object lavaBlockage = exactPoseCheck.invoke(null, level, formation, embeddedPose, 0.0D);
            if (ceilingBlockage == null || lavaBlockage == null) {
                helper.fail("Support resolution bypassed a ceiling or lava: ceiling=" + ceilingBlockage + ", lava=" + lavaBlockage);
                return;
            }
        } catch (ReflectiveOperationException | java.util.NoSuchElementException e) {
            helper.fail("Could not verify exact-pose Living Bridge collision preflight: " + e);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void livingBridgeAllowsOnlyShallowOverlapAtObservedWallFace(GameTestHelper helper) {
        BlockPos wallFace = new BlockPos(1, -56, -48);
        AABB wallShape = new AABB(1.0D, -56.0D, -48.0D, 2.0D, -55.0D, -47.0D);
        // Captured from the latest instance rejection: the member's body overlaps the wall face
        // by 0.05 blocks along Z while its other body dimensions remain clear of the edge.
        AABB shallowBody = new AABB(0.61666665D, -56.26371809D, -47.05D,
                1.21666668D, -54.31371804D, -46.44999998D);
        AABB deepBody = new AABB(0.61666665D, -56.26371809D, -47.25D,
                1.21666668D, -54.31371804D, -46.64999998D);
        try {
            var predicate = LivingWallCoordinator.class.getDeclaredMethod("isShallowPlannedWallFaceContact",
                    BlockPos.class, BlockPos.class, AABB.class, AABB.class);
            predicate.setAccessible(true);
            boolean shallowWallFaceAccepted = (boolean) predicate.invoke(null,
                    wallFace, wallFace, shallowBody, wallShape);
            boolean deepWallFaceAccepted = (boolean) predicate.invoke(null,
                    wallFace, wallFace, deepBody, wallShape);
            boolean unrelatedCellAccepted = (boolean) predicate.invoke(null,
                    wallFace, wallFace.above(), shallowBody, wallShape);
            if (!shallowWallFaceAccepted || deepWallFaceAccepted || unrelatedCellAccepted) {
                helper.fail("Wall-face contact bound was wrong: shallow=" + shallowWallFaceAccepted
                        + ", deep=" + deepWallFaceAccepted + ", unrelated=" + unrelatedCellAccepted);
                return;
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not verify shallow planned wall-face contact: " + e);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void livingBridgeFoundationLockAllowsItsPlannerAndCohortOccupants(GameTestHelper helper) {
        UUID planner = UUID.randomUUID();
        UUID cohortMob = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        try {
            var policy = LivingWallCoordinator.class.getDeclaredMethod("isAssemblyOccupantEligible",
                    UUID.class, UUID.class, Collection.class, Collection.class, Collection.class, Collection.class,
                    boolean.class, boolean.class);
            policy.setAccessible(true);
            boolean plannerBlocksFoundation = (boolean) policy.invoke(null, planner, planner,
                    Set.of(), Set.of(), Set.of(), Set.of(), true, true);
            boolean cohortBlocksFoundation = (boolean) policy.invoke(null, cohortMob, planner,
                    Set.of(), Set.of(), Set.of(), Set.of(cohortMob), true, true);
            boolean outsiderBlocksFoundation = (boolean) policy.invoke(null, outsider, planner,
                    Set.of(), Set.of(), Set.of(), Set.of(), true, true);
            boolean cohortBlocksRoute = (boolean) policy.invoke(null, cohortMob, planner,
                    Set.of(), Set.of(), Set.of(), Set.of(cohortMob), false, false);
            if (plannerBlocksFoundation || cohortBlocksFoundation || !outsiderBlocksFoundation
                    || !cohortBlocksRoute) {
                helper.fail("Living Bridge assembly occupancy policy was wrong: planner="
                        + plannerBlocksFoundation + ", cohort=" + cohortBlocksFoundation
                        + ", outsider=" + outsiderBlocksFoundation + ", route cohort=" + cohortBlocksRoute);
                return;
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not verify Living Bridge assembly occupancy policy: " + e);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void livingBridgeContentionDetectsOtherRosterInFoundationSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slot = helper.absolutePos(new BlockPos(4, 2, 4));
        Zombie blocker = helper.spawn(GameTestEntityTypes.ZOMBIE, 4, 2, 4);
        try {
            var predicate = LivingWallCoordinator.class.getDeclaredMethod("hasCrossFormationAssemblyBlocker",
                    ServerLevel.class, java.util.Collection.class, java.util.Collection.class,
                    java.util.Collection.class, double.class, double.class);
            predicate.setAccessible(true);
            boolean otherRosterBlocks = (boolean) predicate.invoke(null, level, List.of(slot), Set.of(),
                    Set.of(blocker.getUUID()), 0.6D, 1.95D);
            boolean ownRosterBlocks = (boolean) predicate.invoke(null, level, List.of(slot),
                    Set.of(blocker.getUUID()), Set.of(blocker.getUUID()), 0.6D, 1.95D);
            boolean distantSlotBlocks = (boolean) predicate.invoke(null, level,
                    List.of(helper.absolutePos(new BlockPos(10, 2, 10))), Set.of(),
                    Set.of(blocker.getUUID()), 0.6D, 1.95D);
            if (!otherRosterBlocks || ownRosterBlocks || distantSlotBlocks) {
                helper.fail("Cross-formation assembly occupancy detection was wrong: other="
                        + otherRosterBlocks + ", own=" + ownRosterBlocks + ", distant=" + distantSlotBlocks);
                return;
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not verify cross-formation assembly occupancy: " + e);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void livingBridgeFoundationDoesNotDeadlockOnMonsterCrowd(GameTestHelper helper) {
        Zombie monster = helper.spawn(GameTestEntityTypes.ZOMBIE, 3, 2, 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            var policy = LivingWallCoordinator.class.getDeclaredMethod(
                    "isBridgeAssemblyMonsterTransit", boolean.class, net.minecraft.world.entity.Entity.class);
            policy.setAccessible(true);
            boolean bridgeLetsMonsterThrough = (boolean) policy.invoke(null, true, monster);
            boolean ordinaryFormationBlocksMonster = (boolean) policy.invoke(null, false, monster);
            boolean bridgeBlocksPlayer = (boolean) policy.invoke(null, true, player);
            if (!bridgeLetsMonsterThrough || ordinaryFormationBlocksMonster || bridgeBlocksPlayer) {
                helper.fail("Living Bridge assembly monster transit policy was wrong: bridgeMonster="
                        + bridgeLetsMonsterThrough + ", ordinaryMonster=" + ordinaryFormationBlocksMonster
                        + ", bridgePlayer=" + bridgeBlocksPlayer);
                return;
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not verify Living Bridge assembly monster transit: " + e);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 180)
    public void livingWallAutomaticallyAdmitsClusterAcrossSimpleLavaTrench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().clockManager().setTotalTicks(
                level.dimensionType().defaultClock().orElseThrow(), 13000L);

        int baseY = 80;
        for (int x = 0; x <= 22; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean trench = x >= 6 && x <= 10;
                for (int y = baseY - 4; y < baseY; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                            (trench && y == baseY - 1 ? Blocks.LAVA : Blocks.STONE).defaultBlockState(), 2);
                }
                level.setBlock(helper.absolutePos(new BlockPos(x, baseY, z)),
                        (trench ? Blocks.AIR : Blocks.STONE).defaultBlockState(), 2);
            }
        }
        for (int y = baseY; y <= baseY + 3; y++) {
            for (int z = 0; z <= 8; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(13, y, z)), Blocks.STONE.defaultBlockState(), 2);
            }
        }

        var threat = helper.spawn(GameTestEntityTypes.CREEPER, 15, baseY + 1, 4);
        threat.setNoAi(true);
        ServerPlayer observer = helper.makeMockServerPlayerInLevel();
        // Keep the mock player near the cluster for threat/visibility checks without
        // placing its bounding box over the assembly cells selected for the bridge.
        BlockPos observerPos = helper.absolutePos(new BlockPos(3, baseY + 1, -4));
        observer.setPos(observerPos.getX() + 0.5, observerPos.getY(), observerPos.getZ() + 0.5);

        List<Zombie> workers = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            Zombie worker = helper.spawn(GameTestEntityTypes.ZOMBIE, index % 6, baseY + 1, index / 6);
            worker.setTarget(threat);
            workers.add(worker);
        }
        if (!clearBasinState(level)) {
            helper.fail("Could not reset basin cooldown state before the automatic-admission fixture");
            return;
        }

        helper.runAfterDelay(2, () -> {
            List<BlockPos> targets = findWallTargets(level, workers, threat.blockPosition());
            if (targets.isEmpty()) {
                helper.fail("Isolated fixture did not certify a Living Wall target: workers="
                        + workers.stream().map(Zombie::blockPosition).toList()
                        + ", threat=" + threat.blockPosition()
                        + ", targets=" + targets);
                return;
            }
            // Keep entity AI enabled: BridgeWorkGoal runs through the mob goal selector,
            // so setNoAi(true) would let the coordinator claim workers but prevent the
            // bridge recruitment and assembly behavior this fixture is meant to verify.
            helper.runAfterDelay(1, () -> awaitAdmission(helper, level, workers, 150));
        });
    }

    private static void awaitAdmission(GameTestHelper helper, ServerLevel level, List<Zombie> workers,
                                       int ticksLeft) {
        LivingWallCoordinator.tick(level);
        long controlled = workers.stream().filter(LivingWallCoordinator::isControlled).count();
        boolean foundationCommitted = LivingWallCoordinator.hasCommittedFoundationForTest(level,
                workers.stream().map(Zombie::getUUID).toList());
        if (controlled >= 3 && foundationCommitted) {
            helper.succeed();
            return;
        }
        if (ticksLeft <= 0) {
            helper.fail("Automatic Living Wall bridge did not commit its foundation: controlled="
                    + controlled + "/" + workers.size() + ", foundationCommitted=" + foundationCommitted
                    + ", positions="
                    + workers.stream().map(Zombie::blockPosition).toList());
            return;
        }
        helper.runAfterDelay(1, () -> awaitAdmission(helper, level, workers, ticksLeft - 1));
    }

    private static boolean clearBasinState(ServerLevel level) {
        try {
            var clear = LivingWallCoordinator.class.getDeclaredMethod("clearBasinStateForTest", ServerLevel.class);
            clear.setAccessible(true);
            clear.invoke(null, level);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPos> findWallTargets(ServerLevel level, List<Zombie> workers, BlockPos threat) {
        try {
            var finder = LivingWallCoordinator.class.getDeclaredMethod(
                    "findClusterTargetsForTest", ServerLevel.class, List.class, BlockPos.class);
            finder.setAccessible(true);
            return (List<BlockPos>) finder.invoke(null, level, workers, threat);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not invoke Living Wall target scan", e);
        }
    }
}
