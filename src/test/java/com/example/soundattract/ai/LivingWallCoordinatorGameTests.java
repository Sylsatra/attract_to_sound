package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.mixin.MobAccessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(SoundAttractMod.MOD_ID)
public class LivingWallCoordinatorGameTests {
    @GameTest(template = "empty")
    public void sourceOriginSelectionIsForwardMostDeterministicAndFailClosed(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 12, 0, 5);
        ServerLevel level = helper.getLevel();
        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Mob forward = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 1, 2);
        BlockPos threat = helper.absolutePos(new BlockPos(11, 1, 2));
        Vec3 center = new Vec3((near.getX() + forward.getX()) * 0.5D,
                (near.getY() + forward.getY()) * 0.5D, (near.getZ() + forward.getZ()) * 0.5D);
        Method selector = LivingWallCoordinator.class.getDeclaredMethod("resolveStableSourceOrigin",
                ServerLevel.class, List.class, Vec3.class, BlockPos.class);
        selector.setAccessible(true);
        BlockPos selected = (BlockPos) selector.invoke(null, level, List.of(near, forward), center, threat);
        BlockPos reversed = (BlockPos) selector.invoke(null, level, List.of(forward, near), center, threat);
        require(selected != null && selected.equals(forward.blockPosition()) && selected.equals(reversed),
                "single source origin must be the deterministic forward-most validated surface");

        LivingWallCoordinator.SitePlan incident = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(-15, -60, 1), new BlockPos(-19, -60, 6), new BlockPos(-19, -60, 6),
                new Vec3(-4.0D / Math.sqrt(41.0D), 0.0D, 5.0D / Math.sqrt(41.0D)),
                List.of(), List.of(), List.of(), Set.of(), List.of());
        require(LivingWallCoordinator.finalizedSourceAdmissionReasonForTest(
                        incident, new Vec3(-15.0D, -60.0D, 2.0D), new BlockPos(-15, -60, 2)) == null,
                "group centroid and bucket must not stand in for worker reachability");
        require(LivingWallCoordinator.finalizedSourceAdmissionReasonForTest(incident,
                        new Vec3(-11.0D, -60.0D, -3.0D), new BlockPos(-21, -60, -5)) == null,
                "strictly behind aggregate control must remain admissible");
        require(LivingWallCoordinator.finalizedSourceAdmissionReasonForTest(incident,
                        new Vec3(-11.0D, -60.0D, -3.0D), new BlockPos(-15, -60, 2)) == null,
                "aggregate source bucket must not reject an individually routable worker");
        Mob unsupported = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 9, 1, 4);
        helper.setBlock(new BlockPos(9, 0, 4), Blocks.LAVA.defaultBlockState());
        Vec3 unsupportedCenter = unsupported.position();
        require(selector.invoke(null, level, List.of(unsupported), unsupportedCenter, threat) == null,
                "no validated source surface must fail closed without centroid fallback or alternate retry");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void admissionCountsCombatBlockedMobWithoutPathfindingButRuntimeStillYields(GameTestHelper helper) throws Exception {
        StructuralFixture fixture = null;
        CountingZombie candidate = null;
        Mob target = null;
        try {
            fixture = newStructuralFixture(helper, "admissionCountsCombatBlockedMobWithoutPathfinding");
            ServerLevel level = helper.getLevel();
            BlockPos pivot = (BlockPos) getFieldValue(fixture.formation, "pivot");
            candidate = new CountingZombie(level);
            candidate.setPos(pivot.getX() + 2.5D, pivot.getY(), pivot.getZ() + 0.5D);
            level.addFreshEntity(candidate);
            target = helper.spawnWithNoFreeWill(EntityType.SKELETON,
                    pivot.getX() + 3, pivot.getY(), pivot.getZ());
            target.setPos(candidate.position().add(1.0D, 0.0D, 0.0D));
            candidate.setTarget(target);
            candidate.getSensing().tick();
            require(candidate.getSensing().hasLineOfSight(target),
                    "combat fixture must establish visible target before checking runtime filtering");
            int beforeAdmission = candidate.pathCreations;

            Object snapshot = invokeCoordinator("potentialSupplySnapshot",
                    new Class<?>[]{ServerLevel.class, Mob.class, classFor("Formation"), List.class, long.class},
                    level, fixture.leader, fixture.formation, List.of(candidate), level.getGameTime());
            @SuppressWarnings("unchecked")
            List<Mob> potential = (List<Mob>) invokeRecordComponent(snapshot, "candidates");

            require(candidate.pathCreations == beforeAdmission,
                    "potential-supply admission must not create navigation paths");
            require(potential.contains(candidate),
                    "candidate blocked only by path-dependent combat policy remains potential supply");
            require(LivingWallCoordinator.shouldYieldBridgeCombat(candidate),
                    "runtime recruitment combat filter must remain authoritative");
            require(candidate.pathCreations > beforeAdmission,
                    "runtime combat filter must exercise its navigation path branch");
            helper.succeed();
        } finally {
            if (candidate != null && candidate.isAlive()) candidate.discard();
            if (target != null && target.isAlive()) target.discard();
            cleanupStructuralFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void movementTransitionWaitsForFullAssembledContactChain(GameTestHelper helper) throws Exception {
        StructuralFixture fixture = null;
        try {
            fixture = newStructuralFixture(helper, "movementTransitionWaitsForFullAssembledContactChain");
            ServerLevel level = helper.getLevel();
            setFieldValue(fixture.formation, "phase", phase("SETTLE"));
            setIntField(fixture.formation, "assembledSlots", 1);
            for (String movementPhase : List.of("ROTATE", "ANCHOR", "TRAVERSE")) {
                boolean incomplete = (Boolean) invokeCoordinator("transitionToMovement",
                        new Class<?>[]{ServerLevel.class, classFor("Formation"), classFor("Phase"), long.class, String.class},
                        level, fixture.formation, phase(movementPhase), level.getGameTime(), "gametest_incomplete");
                require(!incomplete, movementPhase + " must not start before all required slots are assembled");
                invokeCoordinator("transition",
                        new Class<?>[]{classFor("Formation"), classFor("Phase"), long.class, String.class},
                        fixture.formation, phase(movementPhase), level.getGameTime(), "gametest_unchecked_transition");
                require("SETTLE".equals(getFieldValue(fixture.formation, "phase").toString()),
                        "unchecked or blocked movement transition must preserve the non-movement phase");
            }

            fixture.worker.setPos(fixture.slotPosition);
            setIntField(fixture.formation, "assembledSlots", 2);
            getUuidSetField(fixture.formation, "structuralBuilderIds").add(fixture.foundation.getUUID());
            getUuidSetField(fixture.formation, "structuralBuilderIds").add(fixture.worker.getUUID());
            boolean validChain = (Boolean) invokeCoordinator("transitionToMovement",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), classFor("Phase"), long.class, String.class},
                    level, fixture.formation, phase("ROTATE"), level.getGameTime(), "gametest_full_chain");
            require(validChain, "complete contacted member/slot chain should permit rotation");

            setFieldValue(fixture.formation, "phase", phase("SETTLE"));
            fixture.worker.setPos(fixture.foundation.position().add(8.0D, 0.0D, 0.0D));
            boolean brokenChain = (Boolean) invokeCoordinator("transitionToMovement",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), classFor("Phase"), long.class, String.class},
                    level, fixture.formation, phase("ROTATE"), level.getGameTime(), "gametest_broken_chain");
            require(!brokenChain, "full member/slot counts must not bypass invalid structural contact");
            helper.succeed();
        } finally {
            cleanupStructuralFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void foundationFirstAllowsUpperRecruitmentAfterBaseContact(GameTestHelper helper) {
        require(LivingWallCoordinator.nextLayerLimitForProgress(true, 0, 1, 4, 3) == 1,
                "SCAN must remain bounded to foundation builders");
        require(LivingWallCoordinator.nextLayerLimitForProgress(false, 1, 1, 4, 3) == 4,
                "ASSEMBLE must admit upper-layer recruitment before the full cohort is gathered");
        require(LivingWallCoordinator.foundationCommittedForLayer(1, 1, 1, true, 1),
                "valid foundation contact should permit upper-layer intake");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void terrainMutationInvalidatesBridgeWindow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos destination = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos corridor = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 2));
        long before = LivingWallCoordinator.terrainEpochForTest(level);
        LivingWallCoordinator.markTerrainMutationForTest(level, destination, corridor, support);
        helper.assertTrue(LivingWallCoordinator.terrainEpochForTest(level) > before,
                "destination/corridor/support mutation must advance terrain epoch");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void lateralRayHazardWitnessDoesNotRejectOrderedCorridor(GameTestHelper helper) {
        CanonicalFeetCorridor corridor = new CanonicalFeetCorridor(
                new ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                new BlockPos(0, 1, 0),
                new BlockPos(0, 1, 0),
                new BlockPos(8, 4, 0),
                new BlockPos(6, 4, 0),
                new BlockPos(8, 4, 0),
                new BlockPos(9, 4, 0),
                List.of(new BlockPos(2, 1, 4)),
                List.of(WallProfileScanner.CollisionKind.LAVA),
                List.of(1L));
        if (!LivingWallCoordinator.orderedCorridorTransitionValidForTest(
                corridor, null, new BlockPos(12, 4, 0),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE)) {
            throw new AssertionError("lateral ray hazard witness should not invalidate ordered corridor admission");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void navigableAssemblySurfaceCanBeLessStrictThanStructuralSupport(GameTestHelper helper) throws Exception {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        helper.setBlock(pos.above(), Blocks.AIR.defaultBlockState());
        helper.setBlock(pos.below(), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));

        Method navigable = LivingWallCoordinator.class.getDeclaredMethod("isNavigableAssemblySurface",
                BlockState.class, BlockState.class, BlockState.class);
        Method safeSite = LivingWallCoordinator.class.getDeclaredMethod("isSafeSite",
                net.minecraft.server.level.ServerLevel.class, BlockPos.class);
        navigable.setAccessible(true);
        safeSite.setAccessible(true);

        if (!Boolean.TRUE.equals(navigable.invoke(null,
                Blocks.AIR.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)))) {
            throw new AssertionError("expected navigable assembly support");
        }
        if (Boolean.TRUE.equals(safeSite.invoke(null, helper.getLevel(), helper.absolutePos(pos)))) {
            throw new AssertionError("structural support should remain strict");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void captureReadyPoseKeepsOwnSlotButRejectsOtherAssemblySlots(GameTestHelper helper) throws Exception {
        BlockPos feeder = new BlockPos(1, 1, 1);
        BlockPos ownSlot = new BlockPos(4, 1, 1);
        BlockPos otherSlot = new BlockPos(7, 1, 1);
        Method captureReady = LivingWallCoordinator.class.getDeclaredMethod("isCaptureReadySuctionPose",
                Vec3.class, LivingWallCoordinator.IntakeWindow.class, List.class, BlockPos.class);
        captureReady.setAccessible(true);
        LivingWallCoordinator.IntakeWindow window = new LivingWallCoordinator.IntakeWindow(feeder, new BlockPos(0, 1, 1), false);
        boolean accepted = Boolean.TRUE.equals(captureReady.invoke(null,
                Vec3.atBottomCenterOf(feeder),
                window, List.of(ownSlot, otherSlot), ownSlot));
        boolean rejected = Boolean.TRUE.equals(captureReady.invoke(null,
                Vec3.atBottomCenterOf(otherSlot),
                window, List.of(ownSlot, otherSlot), ownSlot));
        if (!accepted || rejected) {
            throw new AssertionError("capture-ready suction pose should respect the assigned slot exemption only");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void slotPositionUsesFeetCellAlignment(GameTestHelper helper) throws Exception {
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(2, 1, 1),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(1, 1, 1)),
                List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(0, 1, 1))),
                List.of(new BlockPos(2, 1, 1)), Set.of(new BlockPos(2, 1, 1)),
                List.of(new BlockPos(3, 1, 1)));
        Object request = newConstructionRequest(
                Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)), new BlockPos(1, 1, 1),
                new BlockPos(2, 1, 1), new BlockPos(1, 1, 1), new BlockPos(2, 1, 1),
                null, 0L, 0L, 0L);
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        Object formation = newFormation(leader, new BlockPos(5, 1, 1), plan, request, 2, 2, 4,
                newBodyGeometry(1.0D, 2.0D), helper.getLevel().getGameTime());
        Method slotPosition = LivingWallCoordinator.class.getDeclaredMethod("formationSlotPosition",
                formation.getClass(), int.class, double.class);
        slotPosition.setAccessible(true);
        Vec3 position = (Vec3) slotPosition.invoke(null, formation, 0, 0.0D);
        if (Math.abs(position.y - 1.0D) > 1.0E-6D) {
            throw new AssertionError("expected feet-cell y alignment, got " + position);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void supportCanBeRevokedAfterPreflight(GameTestHelper helper) throws Exception {
        BlockPos feet = new BlockPos(2, 1, 2);
        helper.setBlock(feet, Blocks.AIR.defaultBlockState());
        helper.setBlock(feet.above(), Blocks.AIR.defaultBlockState());
        helper.setBlock(feet.below(), Blocks.STONE.defaultBlockState());
        Object formation = newFormation(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1),
                new BlockPos(5, 1, 1),
                new LivingWallCoordinator.SitePlan(
                        LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                        new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(2, 1, 1),
                        new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(1, 1, 1)),
                        List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(0, 1, 1))),
                        List.of(new BlockPos(2, 1, 1)), Set.of(new BlockPos(2, 1, 1)),
                        List.of(new BlockPos(3, 1, 1))),
                newConstructionRequest(Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)),
                        new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(1, 1, 1),
                        new BlockPos(2, 1, 1), null, 0L, 0L, 0L),
                2, 2, 4, newBodyGeometry(1.0D, 2.0D), helper.getLevel().getGameTime());
        Field columnCount = formation.getClass().getDeclaredField("columnCount");
        columnCount.setAccessible(true);
        columnCount.setInt(formation, 1);
        BlockPos absoluteFeet = helper.absolutePos(feet);
        boolean before = !helper.getLevel().getBlockState(absoluteFeet.below()).getCollisionShape(helper.getLevel(), absoluteFeet.below()).isEmpty();
        helper.setBlock(feet.below(), Blocks.AIR.defaultBlockState());
        boolean after = !helper.getLevel().getBlockState(absoluteFeet.below()).getCollisionShape(helper.getLevel(), absoluteFeet.below()).isEmpty();
        if (!before || after) {
            throw new AssertionError("support preflight should fail after the support block is removed: before=" + before
                    + ", after=" + after + ", support=" + helper.getLevel().getBlockState(absoluteFeet.below()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void unchangedRouteEvidenceSnapshotRemainsAdmissible(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 6, 0, 6);
        ServerLevel level = helper.getLevel();
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos sourceBucket = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 1, 1));
        BlockPos wall = helper.absolutePos(new BlockPos(4, 2, 1));
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 1));
        BlockPos threat = helper.absolutePos(new BlockPos(6, 1, 1));
        LivingWallCoordinator.IntakeWindow intake = new LivingWallCoordinator.IntakeWindow(
                helper.absolutePos(new BlockPos(2, 1, 1)),
                helper.absolutePos(new BlockPos(3, 1, 1)),
                false);
        Object evidence = newTargetEvidence(anchor, List.of(11L), List.of(), List.of(), wall, support, sourceBucket, threat,
                "WALL_LEDGE", 1.0D, null);
        Object routeEvidence = newRouteEvidence(level, worker, intake, anchor,
                List.of(intake.feederApproach(), anchor), true, 7L);
        Object snapshot = newTargetSnapshot(level, Vec3.atBottomCenterOf(sourceBucket), Vec3.atBottomCenterOf(sourceBucket), threat, evidence,
                10L, 7L, level.getGameTime() + 200L, routeEvidence);
        Object request = newConstructionRequestWithSnapshot(Vec3.atBottomCenterOf(sourceBucket), sourceBucket, threat, anchor,
                new BlockPos(Math.floorDiv(threat.getX(), 4), Math.floorDiv(threat.getY(), 4), Math.floorDiv(threat.getZ(), 4)),
                snapshot, evidence, routeEvidence, level.getGameTime() + 200L, level.getGameTime() + 200L, 7L);
        String reason = (String) invokeCoordinator("snapshotAdmissionReason",
                new Class<?>[]{ServerLevel.class, classFor("ConstructionRequest"), long.class},
                level, request, level.getGameTime());
        require(reason == null, "expected unchanged route evidence snapshot to remain admissible, got " + reason);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void supportMutationInvalidatesCarriedRouteEvidenceSnapshot(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 6, 0, 6);
        ServerLevel level = helper.getLevel();
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos sourceBucket = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 1, 1));
        BlockPos wall = helper.absolutePos(new BlockPos(4, 2, 1));
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 1));
        BlockPos threat = helper.absolutePos(new BlockPos(6, 1, 1));
        LivingWallCoordinator.IntakeWindow intake = new LivingWallCoordinator.IntakeWindow(
                helper.absolutePos(new BlockPos(2, 1, 1)),
                helper.absolutePos(new BlockPos(3, 1, 1)),
                false);
        Object evidence = newTargetEvidence(anchor, List.of(11L), List.of(), List.of(), wall, support, sourceBucket, threat,
                "WALL_LEDGE", 1.0D, null);
        Object routeEvidence = newRouteEvidence(level, worker, intake, anchor,
                List.of(intake.feederApproach(), anchor), true, 7L);
        Object snapshot = newTargetSnapshot(level, Vec3.atBottomCenterOf(sourceBucket), Vec3.atBottomCenterOf(sourceBucket), threat, evidence,
                10L, 7L, level.getGameTime() + 200L, routeEvidence);
        Object request = newConstructionRequestWithSnapshot(Vec3.atBottomCenterOf(sourceBucket), sourceBucket, threat, anchor,
                new BlockPos(Math.floorDiv(threat.getX(), 4), Math.floorDiv(threat.getY(), 4), Math.floorDiv(threat.getZ(), 4)),
                snapshot, evidence, routeEvidence, level.getGameTime() + 200L, level.getGameTime() + 200L, 7L);
        helper.setBlock(new BlockPos(4, 0, 1), Blocks.AIR.defaultBlockState());
        String reason = (String) invokeCoordinator("snapshotAdmissionReason",
                new Class<?>[]{ServerLevel.class, classFor("ConstructionRequest"), long.class},
                level, request, level.getGameTime());
        require("support_changed".equals(reason), "expected support_changed after support mutation, got " + reason);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void routeEvidenceAcceptsCertifiedProgressAndRejectsOffRouteDisplacement(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 6, 0, 6);
        ServerLevel level = helper.getLevel();
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos sourceBucket = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 1, 1));
        BlockPos wall = helper.absolutePos(new BlockPos(4, 2, 1));
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 1));
        BlockPos threat = helper.absolutePos(new BlockPos(6, 1, 1));
        LivingWallCoordinator.IntakeWindow intake = new LivingWallCoordinator.IntakeWindow(
                helper.absolutePos(new BlockPos(2, 1, 1)), helper.absolutePos(new BlockPos(3, 1, 1)), false);
        Object evidence = newTargetEvidence(anchor, List.of(13L), List.of(), List.of(), wall, support,
                sourceBucket, threat, "WALL_LEDGE", 1.0D, null);
        Object routeEvidence = newRouteEvidence(level, worker, intake, anchor,
                List.of(intake.feederApproach(), anchor), true, 9L);
        Object snapshot = newTargetSnapshot(level, Vec3.atBottomCenterOf(sourceBucket), worker.position(), threat,
                evidence, 10L, 9L, level.getGameTime() + 200L, routeEvidence);
        Object request = newConstructionRequestWithSnapshot(worker.position(), sourceBucket, threat, anchor,
                new BlockPos(Math.floorDiv(threat.getX(), 4), Math.floorDiv(threat.getY(), 4), Math.floorDiv(threat.getZ(), 4)),
                snapshot, evidence, routeEvidence, level.getGameTime() + 200L, level.getGameTime() + 200L, 9L);
        Vec3 certifiedStart = worker.position();
        Vec3 endpoint = Vec3.atBottomCenterOf(intake.feederApproach());
        worker.setPos(certifiedStart.lerp(endpoint, 0.5D).x, certifiedStart.lerp(endpoint, 0.5D).y,
                certifiedStart.lerp(endpoint, 0.5D).z);
        String onRoute = (String) invokeCoordinator("snapshotAdmissionReason",
                new Class<?>[]{ServerLevel.class, classFor("ConstructionRequest"), long.class},
                level, request, level.getGameTime());
        require(onRoute == null, "monotone progress on the certified route should remain admissible, got " + onRoute);
        worker.setPos(certifiedStart.x, certifiedStart.y, certifiedStart.z + 5.0D);
        String offRoute = (String) invokeCoordinator("snapshotAdmissionReason",
                new Class<?>[]{ServerLevel.class, classFor("ConstructionRequest"), long.class},
                level, request, level.getGameTime());
        require("worker_off_certified_route".equals(offRoute), "off-route displacement must reject closed, got " + offRoute);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void safeBaseCertificateRejectionRemainsTerminalAcrossAssemblyTicks(GameTestHelper helper) throws Exception {
        StructuralFixture fixture = null;
        try {
            fixture = newStructuralFixture(helper, "safeBaseCertificateRejectionRemainsTerminalAcrossAssemblyTicks");
            require(!LivingWallCoordinator.guardSafeBaseNavigation(fixture.worker),
                    "missing worker route certificate must stop navigation");
            require(!LivingWallCoordinator.guardSafeBaseNavigation(fixture.worker),
                    "repeated guard calls must not create a replacement route");
            Set<UUID> terminal = getUuidSetField(fixture.formation, "terminalSafeBaseFailures");
            Set<UUID> pending = getUuidSetField(fixture.formation, "pendingSafeBaseReplans");
            require(terminal.contains(fixture.worker.getUUID()), "certificate rejection must be terminal");
            require(pending.isEmpty(), "certificate rejection must not queue an unkeyed replan");
            for (int tick = 0; tick < 3; tick++) {
                invokeCoordinator("assembleFormation",
                        new Class<?>[]{ServerLevel.class, classFor("Formation"), long.class},
                        fixture.level, fixture.formation, fixture.level.getGameTime() + tick);
            }
            require(pending.isEmpty(), "assembly ticks must not enqueue a same-request route retry");
            require(((Map<?, ?>) getFieldValue(fixture.formation, "safeBaseNavigation")).isEmpty(),
                    "terminal certificate rejection must not install a replacement reservation");
            helper.succeed();
        } finally {
            cleanupStructuralFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void safeBaseRouteRepairsOnlySupportedStaleGroundState(GameTestHelper helper) throws Exception {
        StructuralFixture fixture = null;
        try {
            fixture = newStructuralFixture(helper, "safeBaseRouteRepairsOnlySupportedStaleGroundState");
            prepareSurface(helper, -8, 16, -8, 16);
            ServerLevel level = fixture.level;
            Mob worker = fixture.worker;
            Object formation = fixture.formation;
            setFieldValue(formation, "phase", phase("ASSEMBLE"));
            setIntField(formation, "assembledSlots", 1);
            setIntField(formation, "activeStructuralSlot", 1);
            getUuidSetField(formation, "structuralBuilderIds").add(worker.getUUID());
            BlockPos assembly = (BlockPos) ((List<?>) getFieldValue(getFieldValue(formation, "plan"), "assemblySlots")).get(0);
            getBlockPosUuidMap(formation, "assemblySlotOwners").put(assembly, worker.getUUID());
            setFieldValue(formation, "plan", routeTestPlan(formation, List.of()));
            worker.setPos(fixture.foundation.position().add(3.0D, 0.0D, 0.0D));
            worker.setNoGravity(true);
            worker.noPhysics = true;
            worker.setDeltaMovement(Vec3.ZERO);
            worker.getNavigation().stop();
            worker.setOnGround(true);
            BlockPos support = worker.blockPosition().below();
            require(!level.getBlockState(support).getCollisionShape(level, support).isEmpty(),
                    "grounded baseline requires a solid support");
            LivingWallCoordinator.guardSafeBaseNavigation(worker);
            require(worker.getNavigation().isDone(), "held worker navigation must remain stopped");
            List<BlockPos> groundedCandidates = enumerateRouteCandidates(level, formation, worker);
            require(!groundedCandidates.isEmpty(), "grounded baseline requires resolved route candidates");
            require(worker.getNavigation().createPath(groundedCandidates.get(0), 0) != null,
                    "grounded baseline candidate must produce a direct path");
            require(findSafeBaseRoute(level, formation, worker, 1) != null,
                    "healthy grounded baseline must acquire a certified route");
            clearSafeBaseRouteState(level, formation, worker);
            setFieldValue(formation, "plan", routeTestPlan(formation, List.of()));

            worker.setOnGround(false);
            Path stalePath = worker.getNavigation().createPath(
                    enumerateRouteCandidates(level, formation, worker).get(0), 0);
            require(stalePath == null, "pinned stale onGround=false must reproduce the pre-fix null path");
            Vec3 heldPosition = worker.position();
            boolean heldNoGravity = worker.isNoGravity();
            boolean heldNoPhysics = worker.noPhysics;
            UUID formationId = (UUID) getFieldValue(formation, "id");
            require(findSafeBaseRoute(level, formation, worker, 1) != null,
                    "support-verified onGround repair must permit certified route acquisition");
            require(worker.onGround(), "supported worker should remain onGround after the proof");
            require(worker.position().equals(heldPosition), "route acquisition must preserve worker position");
            require(worker.getDeltaMovement().equals(Vec3.ZERO), "route acquisition must preserve zero motion");
            require(worker.isNoGravity() == heldNoGravity && worker.noPhysics == heldNoPhysics,
                    "route acquisition must preserve held physics flags");
            require(worker.getNavigation().isDone(), "route acquisition must leave navigation stopped");
            require(worker.isSupportedBy(support), "repaired worker must still be physically supported");
            require(formationId.equals(getMobOwners(level).get(worker.getUUID())),
                    "route acquisition must preserve formation ownership");

            for (boolean obstructed : List.of(false, true)) {
                clearSafeBaseRouteState(level, formation, worker);
                setFieldValue(formation, "plan", routeTestPlan(formation, List.of()));
                worker.setOnGround(false);
                worker.setPos(heldPosition);
                BlockPos invalidSupport = worker.blockPosition().below();
                if (obstructed) {
                    helper.setBlock(relativePos(helper, invalidSupport), Blocks.STONE.defaultBlockState());
                    helper.setBlock(relativePos(helper, worker.blockPosition()), Blocks.STONE.defaultBlockState());
                } else {
                    helper.setBlock(relativePos(helper, invalidSupport), Blocks.AIR.defaultBlockState());
                }
                require(findSafeBaseRoute(level, formation, worker, 1) == null,
                        "unsupported or obstructed worker must fail closed");
                require(!worker.onGround(), "invalid support proof must not ground the worker");
                require(((Map<?, ?>) getFieldValue(formation, "safeBaseNavigation")).isEmpty(),
                        "invalid support proof must not reserve a safe-base route");
                require(((Map<?, ?>) getFieldValue(formation, "structuralApproachReservations")).isEmpty(),
                        "invalid support proof must not reserve an approach lane");
                require(LivingBridgeRouteData.get(level).snapshot(routeReservationId(formationId, worker.getUUID())) == null,
                        "invalid support proof must not persist a route snapshot");
                require(worker.position().equals(heldPosition) && worker.getDeltaMovement().equals(Vec3.ZERO),
                        "invalid support proof must preserve held pose and motion");
                require(worker.isNoGravity() == heldNoGravity && worker.noPhysics == heldNoPhysics
                                && worker.getNavigation().isDone(),
                        "invalid support proof must preserve held physics and stopped navigation");
                require(formationId.equals(getMobOwners(level).get(worker.getUUID())),
                        "invalid support proof must preserve formation ownership");
                require(worker.getUUID().equals(getBlockPosUuidMap(formation, "assemblySlotOwners").get(assembly)),
                        "invalid support proof must preserve assembly-slot ownership");
                helper.setBlock(relativePos(helper, invalidSupport), Blocks.STONE.defaultBlockState());
                helper.setBlock(relativePos(helper, worker.blockPosition()), Blocks.AIR.defaultBlockState());
            }

            clearSafeBaseRouteState(level, formation, worker);
            List<BlockPos> resolved = enumerateRouteCandidates(level, formation, worker);
            long safeGround = resolved.stream().filter(candidate ->
                    com.example.soundattract.pathfinding.HazardClassifier.isSafeGround(level, candidate)).count();
            require(!resolved.isEmpty() && safeGround > 0,
                    "hazard-only no-route fixture must resolve candidates including safe ground");
            setFieldValue(formation, "plan", routeTestPlan(formation, resolved));
            List<BlockPos> plannedHazards = (List<BlockPos>) getFieldValue(
                    getFieldValue(formation, "plan"), "hazardCells");
            long plannedHazardRejected = resolved.stream().filter(plannedHazards::contains).count();
            require(plannedHazardRejected == resolved.size(),
                    "planned hazards must reject every resolved candidate");
            long survivingCandidates = resolved.stream().filter(candidate -> !plannedHazards.contains(candidate)).count();
            require(survivingCandidates == 0, "no candidate may survive the planned-hazard filter");
            worker.setOnGround(true);
            require(findSafeBaseRoute(level, formation, worker, 1) == null,
                    "hazard-covered candidates must not acquire a route");
            require(((Map<?, ?>) getFieldValue(formation, "safeBaseNavigation")).isEmpty(),
                    "hazard-only rejection must not reserve a safe-base route");
            require(((Map<?, ?>) getFieldValue(formation, "structuralApproachReservations")).isEmpty(),
                    "hazard-only rejection must not reserve an approach lane");
            require(LivingBridgeRouteData.get(level).snapshot(routeReservationId(formationId, worker.getUUID())) == null,
                    "hazard-only rejection must not persist a route snapshot");
            require(formationId.equals(getMobOwners(level).get(worker.getUUID()))
                            && worker.getUUID().equals(getBlockPosUuidMap(formation, "assemblySlotOwners").get(assembly)),
                    "hazard-only rejection must preserve formation and assembly ownership");
            require(worker.onGround() && worker.position().equals(heldPosition)
                            && worker.isNoGravity() == heldNoGravity && worker.noPhysics == heldNoPhysics
                            && worker.getNavigation().isDone() && worker.getDeltaMovement().equals(Vec3.ZERO),
                    "hazard-only rejection must preserve exact held worker state");
            helper.succeed();
        } finally {
            cleanupStructuralFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void failedControlledSuctionRetainsExclusiveMovementControl(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob originalTarget = null;
        try {
            fixture = newFixture(helper, "failedControlledSuctionRetainsExclusiveMovementControl");
            originalTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 10, 1, 4);
            fixture.worker.setTarget(originalTarget);
            fixture.worker.setAggressive(true);
            fixture.worker.setDeltaMovement(new Vec3(0.35D, 0.0D, 0.0D));
            fixture.worker.getNavigation().moveTo(11.5D, 1.0D, 4.5D, 1.0D);
            fixture.worker.setPos(1.5D, 1.0D, 4.5D);

            LivingWallCoordinator.tickBridgeWork(fixture.worker, Vec3.atBottomCenterOf(fixture.feeder));

            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.SUCTIONING,
                    "ticket should remain SUCTIONING");
            require(fixture.worker.getNavigation().isDone(), "navigation should be stopped");
            require(fixture.worker.getDeltaMovement().lengthSqr() <= 1.0E-9D, "delta should be zero");
            require(fixture.worker.isNoGravity(), "controlled gravity hold should remain");
            require(fixture.worker.noPhysics, "controlled collision hold should remain");
            require(((MobAccessor) fixture.worker).isPersistenceRequiredFlag(), "persistence hold should remain");
            require(fixture.worker.getTarget() == null, "combat target should stay cleared");
            require(!fixture.worker.isAggressive(), "aggression should stay cleared");
            require(fixture.assignment.navigationSubmissionTick == Long.MIN_VALUE,
                    "ordinary navigation must not be submitted");
            require(getIntField(fixture.formation, "navigationSubmittedCount") == 0,
                    "navigation submission counter must not advance");
            require(fixture.assignment.pathFailures == 0, "path failures must not advance");
            require(!fixture.assignment.captureCompleted, "captureCompleted must remain false");
            require(!getProvenanceMap(fixture.formation).containsKey(fixture.worker.getUUID()),
                    "provenance must remain absent");
            require(fixture.assignment.state != LivingWallCoordinator.MobTicketState.BUILDING,
                    "failed suction must not enter BUILDING");
            require(!getUuidListField(fixture.formation, "pendingBuilderIds").contains(fixture.worker.getUUID()),
                    "worker must not become pending");
            require(!getUuidListField(fixture.formation, "members").contains(fixture.worker.getUUID()),
                    "worker must not commit");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, originalTarget);
        }
    }

    @GameTest(template = "empty")
    public void controlledSuctionRetryPreservesCaptureProvenanceAndCommitsBuilder(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob originalTarget = null;
        try {
            fixture = newFixture(helper, "controlledSuctionRetryPreservesCaptureProvenanceAndCommitsBuilder");
            originalTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 10, 1, 4);
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER;
            fixture.worker.setTarget(originalTarget);
            fixture.worker.setAggressive(true);
            getUuidSetField(fixture.formation, "recruitmentArrivalReports").add(fixture.worker.getUUID());
            invokeCoordinator("processRecruitmentReports",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), long.class},
                    fixture.level, fixture.formation, fixture.level.getGameTime());
            fixture.assignment.intakeWindowFailures = 1;
            getUuidBooleanMap(fixture.formation, "ticketGoals").put(fixture.worker.getUUID(), Boolean.TRUE);
            getUuidSetField(fixture.formation, "recruitmentArrivalReports").add(fixture.worker.getUUID());
            invokeCoordinator("processRecruitmentReports",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), long.class},
                    fixture.level, fixture.formation, fixture.level.getGameTime());

            require(fixture.assignment.captureCompleted, "capture should complete on valid retry");
            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.BUILDING,
                    "ticket should enter BUILDING");
            require(getProvenanceMap(fixture.formation).get(fixture.worker.getUUID()).equals(fixture.window),
                    "assembly intake provenance should be copied");
            require(!getUuidBooleanMap(fixture.formation, "ticketGoals").containsKey(fixture.worker.getUUID()),
                    "capture completion should remove ticket goal");

            getUuidListField(fixture.formation, "members").add(fixture.leader.getUUID());
            getUuidSetField(fixture.formation, "structuralBuilderIds").add(fixture.leader.getUUID());
            getUuidSetField(fixture.formation, "builderReservations").add(fixture.leader.getUUID());
            setIntField(fixture.formation, "columnCount", 1);
            setIntField(fixture.formation, "requiredBodies", 2);
            setIntField(fixture.formation, "assembledSlots", 1);
            setFieldValue(fixture.formation, "phase", phase("ASSEMBLE"));

            boolean queued = (Boolean) invokeCoordinator("queuePendingBuilder",
                    new Class<?>[]{Mob.class, classFor("Formation"), int.class, long.class},
                    fixture.worker, fixture.formation, 2, fixture.level.getGameTime());
            require(queued, "worker should queue as pending builder");
            require(getUuidListField(fixture.formation, "pendingBuilderIds").contains(fixture.worker.getUUID()),
                    "worker should be pending after queue");

            invokeCoordinator("promotePendingBuilders",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), int.class},
                    fixture.level, fixture.formation, 1);

            require(getProvenanceMap(fixture.formation).get(fixture.worker.getUUID()).equals(fixture.window),
                    "provenance should survive builder promotion");
            require(getUuidListField(fixture.formation, "members").contains(fixture.worker.getUUID()),
                    "worker should commit as member");
            require(getUuidSetField(fixture.formation, "structuralBuilderIds").contains(fixture.worker.getUUID()),
                    "structural builder bookkeeping should include worker");
            require(getUuidSetField(fixture.formation, "builderReservations").contains(fixture.worker.getUUID()),
                    "builder reservation bookkeeping should include worker");
            require(!getUuidListField(fixture.formation, "pendingBuilderIds").contains(fixture.worker.getUUID()),
                    "worker should leave pending queue after commit");
            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.RELEASED,
                    "committed member ticket should release");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, originalTarget);
        }
    }

    @GameTest(template = "empty")
    public void controlledSuctionTimeoutAbortsBeforeNavigationFallback(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob originalTarget = null;
        try {
            fixture = newFixture(helper, "controlledSuctionTimeoutAbortsBeforeNavigationFallback");
            originalTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 10, 1, 4);
            boolean originalNoGravity = fixture.worker.isNoGravity();
            boolean originalNoPhysics = fixture.worker.noPhysics;
            boolean originalPersistence = ((MobAccessor) fixture.worker).isPersistenceRequiredFlag();
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER;
            fixture.worker.setTarget(originalTarget);
            fixture.worker.setAggressive(true);
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.SUCTIONING;
            fixture.worker.setPos(fixture.feeder.getX() + 0.20D, fixture.feeder.getY(), fixture.feeder.getZ() + 0.20D);
            fixture.assignment.intakeWindowFailures = 0;
            fixture.assignment.captureTicks = getStaticInt("CONTROLLED_INTAKE_MAX_TICKS");
            fixture.assignment.controlledPull = true;
            long now = fixture.level.getGameTime();

            boolean captured = LivingWallCoordinator.tryControlledRecruitmentPull(
                    fixture.worker, Vec3.atBottomCenterOf(fixture.feeder));

            require(!captured, "timeout branch should not report capture");
            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER,
                    "timeout should transition to MOVING_TO_FEEDER");
            require(fixture.assignment.captureTicks == 0, "captureTicks should reset");
            require(!fixture.assignment.controlledPull, "controlledPull should clear");
            require(fixture.assignment.captureRetryUntil == now + getStaticInt("CAPTURE_RETRY_COOLDOWN_TICKS"),
                    "retry cooldown should be exact");
            require(fixture.assignment.intakeWindowFailures == 1, "timeout should increment window failures once");
            require(fixture.worker.isNoGravity() == originalNoGravity, "gravity should restore");
            require(fixture.worker.noPhysics == originalNoPhysics, "noPhysics should restore");
            require(((MobAccessor) fixture.worker).isPersistenceRequiredFlag() == originalPersistence,
                    "persistence should restore");
            require(!fixture.assignment.captureCompleted, "timeout must not complete capture");
            require(!getProvenanceMap(fixture.formation).containsKey(fixture.worker.getUUID()),
                    "timeout must not write provenance");
            require(fixture.assignment.state != LivingWallCoordinator.MobTicketState.BUILDING,
                    "timeout must not enter BUILDING");
            require(!getUuidListField(fixture.formation, "pendingBuilderIds").contains(fixture.worker.getUUID()),
                    "timeout must not queue pending builder");
            require(!getUuidListField(fixture.formation, "members").contains(fixture.worker.getUUID()),
                    "timeout must not commit member");
            require(fixture.assignment.navigationSubmissionTick == Long.MIN_VALUE,
                    "timeout checkpoint must precede ordinary navigation");
            require(getIntField(fixture.formation, "navigationSubmittedCount") == 0,
                    "timeout checkpoint must not submit navigation");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, originalTarget);
        }
    }

    @GameTest(template = "empty")
    public void terminalControlledSuctionFailureReleasesWithoutFallback(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob originalTarget = null;
        try {
            fixture = newFixture(helper, "terminalControlledSuctionFailureReleasesWithoutFallback");
            originalTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 10, 1, 4);
            originalTarget.setPos(fixture.feeder.getX() + 1.5D, fixture.feeder.getY(), fixture.feeder.getZ() + 0.5D);
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER;
            double initialDx = originalTarget.getX() - fixture.worker.getX();
            double initialDz = originalTarget.getZ() - fixture.worker.getZ();
            float initialYRot = (float) (Math.toDegrees(Math.atan2(initialDz, initialDx)) - 90.0D);
            fixture.worker.setYRot(initialYRot);
            fixture.worker.setYHeadRot(initialYRot);
            fixture.worker.setYBodyRot(initialYRot);
            fixture.worker.setTarget(originalTarget);
            fixture.worker.setAggressive(true);
            getUuidSetField(fixture.formation, "recruitmentArrivalReports").add(fixture.worker.getUUID());
            invokeCoordinator("processRecruitmentReports",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), long.class},
                    fixture.level, fixture.formation, fixture.level.getGameTime());
            fixture.assignment.intakeWindowFailures = 2;
            require(originalTarget.getUUID().equals(getUuidUuidMap(fixture.formation, "originalTargets").get(fixture.worker.getUUID())),
                    "controlled hold should snapshot the original target before terminal release");
            require(fixture.level.getEntity(originalTarget.getUUID()) == originalTarget,
                    "original target should remain resolvable before terminal release");
            fixture.worker.setPos(fixture.feeder.getX() - 2.5D, fixture.feeder.getY(), fixture.feeder.getZ() - 2.5D);
            double dx = originalTarget.getX() - fixture.worker.getX();
            double dz = originalTarget.getZ() - fixture.worker.getZ();
            float yRot = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            fixture.worker.setYRot(yRot);
            fixture.worker.setYHeadRot(yRot);
            fixture.worker.setYBodyRot(yRot);

            boolean captured = LivingWallCoordinator.tryControlledRecruitmentPull(
                    fixture.worker, Vec3.atBottomCenterOf(fixture.feeder));

            require(!captured, "terminal release-producing pull should not report capture");
            require(fixture.assignment.intakeWindowFailures == 3, "terminal threshold should be reached exactly once");
            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.RELEASED,
                    "terminal failure should release ticket");
            require(!getUuidBooleanMap(fixture.formation, "ticketGoals").containsKey(fixture.worker.getUUID()),
                    "ticket goal should be removed on release");
            require(!getMobOwners(fixture.level).containsKey(fixture.worker.getUUID()),
                    "bridge ownership should be removed on release");
            require(fixture.worker.getTarget() == originalTarget,
                    "target object should restore on release");
            require(fixture.worker.getTarget() != null
                    && fixture.worker.getTarget().getUUID().equals(originalTarget.getUUID()),
                    "target UUID should restore on release");
            require(!fixture.worker.isNoGravity(), "held gravity should restore");
            require(!fixture.worker.noPhysics, "held noPhysics should restore");
            require(!fixture.assignment.captureCompleted, "terminal failure must not complete capture");
            require(!getProvenanceMap(fixture.formation).containsKey(fixture.worker.getUUID()),
                    "terminal failure must not write provenance");
            require(fixture.assignment.state != LivingWallCoordinator.MobTicketState.BUILDING,
                    "terminal failure must not enter BUILDING");
            require(!getUuidListField(fixture.formation, "pendingBuilderIds").contains(fixture.worker.getUUID()),
                    "terminal failure must not queue pending builder");
            require(!getUuidListField(fixture.formation, "members").contains(fixture.worker.getUUID()),
                    "terminal failure must not commit member");
            require(fixture.assignment.navigationSubmissionTick == Long.MIN_VALUE,
                    "terminal release must not submit fallback navigation");
            require(getIntField(fixture.formation, "navigationSubmittedCount") == 0,
                    "terminal release must not submit fallback navigation");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, originalTarget);
        }
    }

    @GameTest(template = "empty")
    public void cleanupPreservesBothMovementTicketStates(GameTestHelper helper) throws Exception {
        Fixture first = null;
        Fixture second = null;
        try {
            first = newFixture(helper, "cleanupPreservesBothMovementTicketStatesA");
            second = newFixture(helper, "cleanupPreservesBothMovementTicketStatesB");
            first.assignment.state = LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY;
            second.assignment.state = LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER;
            holdForCleanup(first);
            holdForCleanup(second);
            Object state = state(first.level);
            setLongField(state, "lastOrphanFreezeCleanupTick", first.level.getGameTime() - 40L);

            invokeCoordinator("cleanupOrphanedFreezes",
                    new Class<?>[]{classFor("PerLevelState"), ServerLevel.class},
                    state, first.level);

            require(first.worker.isNoGravity() && first.worker.noPhysics,
                    "MOVING_TO_ASSEMBLY worker should remain frozen");
            require(second.worker.isNoGravity() && second.worker.noPhysics,
                    "MOVING_TO_FEEDER worker should remain frozen");
            require(getMobOwners(first.level).containsKey(first.worker.getUUID()),
                    "MOVING_TO_ASSEMBLY owner should remain");
            require(getMobOwners(second.level).containsKey(second.worker.getUUID()),
                    "MOVING_TO_FEEDER owner should remain");
            helper.succeed();
        } finally {
            cleanupFixture(first, null);
            cleanupFixture(second, null);
        }
    }

    @GameTest(template = "empty")
    public void cleanupStillReleasesInactiveTicket(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        try {
            fixture = newFixture(helper, "cleanupStillReleasesInactiveTicket");
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.RELEASED;
            holdForCleanup(fixture);
            Object state = state(fixture.level);
            setLongField(state, "lastOrphanFreezeCleanupTick", fixture.level.getGameTime() - 40L);

            invokeCoordinator("cleanupOrphanedFreezes",
                    new Class<?>[]{classFor("PerLevelState"), ServerLevel.class},
                    state, fixture.level);

            require(!fixture.worker.isNoGravity(), "inactive worker gravity should restore");
            require(!fixture.worker.noPhysics, "inactive worker noPhysics should restore");
            require(!getMobOwners(fixture.level).containsKey(fixture.worker.getUUID()),
                    "inactive worker ownership should clear");
            require(!getBooleanMap(fixture.formation, "originalGravity").containsKey(fixture.worker.getUUID()),
                    "inactive worker gravity snapshot should clear");
            require(!getBooleanMap(fixture.formation, "originalNoPhysics").containsKey(fixture.worker.getUUID()),
                    "inactive worker noPhysics snapshot should clear");
            require(!getBooleanMap(fixture.formation, "originalPersistence").containsKey(fixture.worker.getUUID()),
                    "inactive worker persistence snapshot should clear");
            require(!getUuidUuidMap(fixture.formation, "originalTargets").containsKey(fixture.worker.getUUID()),
                    "inactive worker target snapshot should clear");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void controlledHoldReleaseRetiresSnapshotAndRestoresPhysics(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob originalTarget = null;
        try {
            fixture = newFixture(helper, "controlledHoldReleaseRetiresSnapshotAndRestoresPhysics");
            fixture.worker.setOnGround(true);
            originalTarget = fixture.leader;
            fixture.worker.setTarget(originalTarget);
            fixture.worker.setAggressive(true);
            require(fixture.worker.getTarget() == originalTarget,
                    "release diagnostic: original target was rejected before hold");
            require(fixture.level.getEntity(originalTarget.getUUID()) == originalTarget,
                    "release diagnostic: original target was not registered in the level before hold");
            invokeCoordinator("holdRalliedWorker", new Class<?>[]{Mob.class, classFor("Formation")},
                    fixture.worker, fixture.formation);
            require(originalTarget.getUUID().equals(
                            getUuidUuidMap(fixture.formation, "originalTargets").get(fixture.worker.getUUID())),
                    "release diagnostic: controlled hold did not snapshot the original target UUID");
            require(fixture.level.getEntity(originalTarget.getUUID()) == originalTarget
                            && originalTarget.isAlive() && fixture.worker.canAttack(originalTarget),
                    "release diagnostic: original target became absent or non-attackable during hold");
            require(fixture.worker.isNoGravity(), "controlled hold should freeze gravity");
            require(fixture.worker.noPhysics, "controlled hold should freeze collisions");
            invokeCoordinator("releaseRalliedHold",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), UUID.class, Mob.class},
                    fixture.level, fixture.formation, fixture.worker.getUUID(), fixture.worker);
            require(!fixture.worker.isNoGravity(), "release should restore gravity");
            require(!fixture.worker.noPhysics, "release should restore collisions");
            require(fixture.worker.getTarget() == originalTarget,
                    "release should restore original target object; restored=" + fixture.worker.getTarget()
                            + " targetAlive=" + originalTarget.isAlive()
                            + " targetRegistered=" + (fixture.level.getEntity(originalTarget.getUUID()) == originalTarget)
                            + " canAttack=" + fixture.worker.canAttack(originalTarget));
            require(!fixture.worker.isAggressive(), "release should clear aggression");
            require(!getBooleanMap(fixture.formation, "originalGravity").containsKey(fixture.worker.getUUID()),
                    "gravity snapshot should retire");
            require(!getBooleanMap(fixture.formation, "originalPersistence").containsKey(fixture.worker.getUUID()),
                    "persistence snapshot should retire");
            require(!getBooleanMap(fixture.formation, "originalNoPhysics").containsKey(fixture.worker.getUUID()),
                    "collision snapshot should retire");
            require(!getUuidUuidMap(fixture.formation, "originalTargets").containsKey(fixture.worker.getUUID()),
                    "target snapshot should retire");
            require(!fixture.worker.getPersistentData().contains("soundattract_living_bridge"),
                    "controlled marker should retire");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, originalTarget);
        }
    }

    @GameTest(template = "empty")
    public void suctionAtTargetCompletesBeforeTimeout(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob originalTarget = null;
        try {
            fixture = newFixture(helper, "suctionAtTargetCompletesBeforeTimeout");
            originalTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 10, 1, 4);
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.SUCTIONING;
            fixture.assignment.captureTicks = getStaticInt("CONTROLLED_INTAKE_MAX_TICKS");
            fixture.worker.setPos(Vec3.atBottomCenterOf(fixture.feeder));
            fixture.worker.setTarget(originalTarget);
            fixture.worker.setAggressive(true);
            boolean captured = LivingWallCoordinator.tryControlledRecruitmentPull(
                    fixture.worker, Vec3.atBottomCenterOf(fixture.feeder));
            require(captured, "exact-target suction should succeed before timeout");
            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.SUCTIONING,
                    "successful suction should stay in SUCTIONING until downstream capture");
            require(fixture.assignment.captureTicks == getStaticInt("CONTROLLED_INTAKE_MAX_TICKS"),
                    "exact-target success should not trip timeout increment");
            require(!fixture.assignment.captureCompleted, "success alone should not mark capture complete");
            require(!getProvenanceMap(fixture.formation).containsKey(fixture.worker.getUUID()),
                    "success alone should not write provenance");
            require(!fixture.assignment.state.equals(LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER),
                    "success should not fall back to MOVING_TO_FEEDER");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, originalTarget);
        }
    }

    @GameTest(template = "empty")
    public void transientAnchorAtOverlappingPlannedSlotReportsArrivalAndBuilds(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        try {
            fixture = newFixture(helper, "transientAnchorAtOverlappingPlannedSlotReportsArrivalAndBuilds", true);
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.SUCTIONING;
            fixture.worker.setPos(Vec3.atBottomCenterOf(fixture.feeder));

            require(LivingWallCoordinator.isRecruitmentCaptureReady(fixture.worker),
                    "transient exact-feeder worker should be capture-ready even near another planned slot");
            LivingWallCoordinator.tickBridgeWork(fixture.worker, Vec3.atBottomCenterOf(fixture.feeder));
            require(getUuidSetField(fixture.formation, "recruitmentArrivalReports").contains(fixture.worker.getUUID()),
                    "exact-feeder transient worker should report arrival");

            invokeCoordinator("processRecruitmentReports",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), long.class},
                    fixture.level, fixture.formation, fixture.level.getGameTime());

            require(fixture.assignment.captureCompleted, "arrival processing should complete capture");
            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.BUILDING,
                    "arrival processing should advance to BUILDING");
            require(getProvenanceMap(fixture.formation).get(fixture.worker.getUUID()).equals(fixture.window),
                    "capture should preserve transient intake provenance");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void transientAnchorRejectsExactFeederOwnedByAnotherWorker(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        try {
            fixture = newFixture(helper, "transientAnchorRejectsExactFeederOwnedByAnotherWorker", true);
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.SUCTIONING;
            fixture.worker.setPos(Vec3.atBottomCenterOf(fixture.feeder));
            getBlockPosUuidMap(fixture.formation, "assemblySlotOwners").put(fixture.feeder, UUID.randomUUID());

            require(!LivingWallCoordinator.isRecruitmentCaptureReady(fixture.worker),
                    "transient feeder owned by another worker must not become capture-ready");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void coarseAssemblyArrivalStagesBeforeExactLock(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        try {
            fixture = newFixture(helper, "coarseAssemblyArrivalStagesBeforeExactLock");
            BlockPos assembly = fixture.assignment.assemblySlot;
            Vec3 coarseOnly = Vec3.atBottomCenterOf(assembly).add(1.95D, 0.0D, 0.0D);
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY;
            fixture.worker.setPos(coarseOnly);
            getBlockPosUuidMap(fixture.formation, "assemblySlotOwners").put(assembly, fixture.worker.getUUID());
            getMapField(fixture.formation, "assemblySlotAssignments").put(fixture.worker.getUUID(), assembly);
            Vec3 before = fixture.worker.position();

            boolean locked = LivingWallCoordinator.tryControlledAssemblyStaging(
                    fixture.worker, Vec3.atBottomCenterOf(assembly));

            require(!locked, "coarse-only worker must not exact-lock in one tick");
            require(fixture.assignment.state != LivingWallCoordinator.MobTicketState.ASSEMBLED,
                    "coarse-only worker must not become assembled");
            require(!getUuidListField(fixture.formation, "members").contains(fixture.worker.getUUID()),
                    "coarse-only worker must not commit early");
            require(fixture.worker.position().distanceTo(before) <= 0.080001D,
                    "assembly staging movement must stay bounded to 0.08 blocks per tick");
            require(fixture.worker.getDeltaMovement().lengthSqr() == 0.0D,
                    "direct staging setPos must clear velocity before the entity tick");
            require(LivingWallCoordinator.exactAssemblyDistanceSqr(fixture.worker.position(), assembly) > 0.35D * 0.35D,
                    "worker should still be outside exact lock tolerance");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void crowdBlockedAssemblyStageRetriesWithoutFalseAssembly(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob blocker = null;
        try {
            fixture = newFixture(helper, "crowdBlockedAssemblyStageRetriesWithoutFalseAssembly");
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY;
            fixture.worker.setPos(Vec3.atBottomCenterOf(fixture.assignment.assemblySlot).add(0.7D, 0.0D, 0.0D));
            getBlockPosUuidMap(fixture.formation, "assemblySlotOwners").put(fixture.assignment.assemblySlot,
                    fixture.worker.getUUID());
            getMapField(fixture.formation, "assemblySlotAssignments").put(fixture.worker.getUUID(),
                    fixture.assignment.assemblySlot);
            blocker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                    fixture.assignment.assemblySlot.getX(), fixture.assignment.assemblySlot.getY(),
                    fixture.assignment.assemblySlot.getZ());
            blocker.setPos(Vec3.atBottomCenterOf(fixture.assignment.assemblySlot));

            boolean locked = LivingWallCoordinator.tryControlledAssemblyStaging(
                    fixture.worker, Vec3.atBottomCenterOf(fixture.assignment.assemblySlot));

            require(!locked, "blocked stage must not lock");
            require(fixture.assignment.state != LivingWallCoordinator.MobTicketState.ASSEMBLED,
                    "blocked stage must not become assembled");
            require(!getUuidListField(fixture.formation, "members").contains(fixture.worker.getUUID()),
                    "blocked stage must not commit");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, blocker);
        }
    }

    @GameTest(template = "empty")
    public void recruitmentLaneDiagnosticsSeparateAssignedBuildersAndIgnoreBoxCorners(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        Mob assigned = null;
        Mob unassigned = null;
        Mob boxCorner = null;
        try {
            fixture = newFixture(helper, "recruitmentLaneDiagnosticsSeparateAssignedBuildersAndIgnoreBoxCorners");
            BlockPos slot = fixture.assignment.assemblySlot;
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY;
            fixture.worker.setPos(Vec3.atBottomCenterOf(slot).add(-1.0D, 0.0D, -1.0D));
            assigned = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                    slot.getX(), slot.getY(), slot.getZ());
            assigned.setPos(Vec3.atBottomCenterOf(slot).add(-0.25D, 0.0D, -0.25D));
            unassigned = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                    slot.getX(), slot.getY(), slot.getZ() + 1);
            unassigned.setPos(Vec3.atBottomCenterOf(slot).add(-0.25D, 0.0D, -0.25D));
            boxCorner = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                    slot.getX(), slot.getY(), slot.getZ() + 1);
            boxCorner.setPos(Vec3.atBottomCenterOf(slot).add(-0.9D, 0.0D, 0.65D));
            getMapField(fixture.formation, "mobTickets").put(assigned.getUUID(), fixture.assignment);
            Vec3 assignedVelocity = assigned.getDeltaMovement();
            Vec3 routeEnd = Vec3.atBottomCenterOf(slot);

            Object blockers = invokeCoordinator("findCrowdBlockers",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), Mob.class, Vec3.class},
                    fixture.level, fixture.formation, fixture.worker, routeEnd);
            @SuppressWarnings("unchecked")
            List<Mob> assignedBlockers = (List<Mob>) invokeRecordComponent(blockers, "assigned");
            @SuppressWarnings("unchecked")
            List<Mob> unassignedBlockers = (List<Mob>) invokeRecordComponent(blockers, "unassigned");
            require(assignedBlockers.contains(assigned), "ticketed builder on the lane must be reported as assigned");
            require(!unassignedBlockers.contains(assigned), "ticketed builder must not be classified as unassigned");
            require(unassignedBlockers.contains(unassigned), "unassigned mob on the lane must remain nudge-eligible");
            require(!assignedBlockers.contains(boxCorner) && !unassignedBlockers.contains(boxCorner),
                    "entity inside the expanded box but outside the lane segment must not count as a blocker");

            invokeCoordinator("guardRecruitmentLane",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), long.class},
                    fixture.level, fixture.formation, fixture.level.getGameTime());
            require(assigned.getDeltaMovement().equals(assignedVelocity),
                    "lane guard must never displace an assigned builder");
            helper.succeed();
        } finally {
            if (assigned != null && assigned.isAlive()) assigned.discard();
            if (unassigned != null && unassigned.isAlive()) unassigned.discard();
            if (boxCorner != null && boxCorner.isAlive()) boxCorner.discard();
            cleanupFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void invalidAssembledWorkerDemotesThenReleasesAfterBudget(GameTestHelper helper) throws Exception {
        Fixture fixture = null;
        try {
            fixture = newFixture(helper, "invalidAssembledWorkerDemotesThenReleasesAfterBudget");
            fixture.assignment.state = LivingWallCoordinator.MobTicketState.ASSEMBLED;
            fixture.assignment.assemblyRecoveryAttempts = 2;
            fixture.worker.setPos(Vec3.atBottomCenterOf(fixture.assignment.assemblySlot).add(1.0D, 0.0D, 0.0D));
            getBlockPosUuidMap(fixture.formation, "assemblySlotOwners").put(fixture.assignment.assemblySlot,
                    fixture.worker.getUUID());
            getMapField(fixture.formation, "assemblySlotAssignments").put(fixture.worker.getUUID(),
                    fixture.assignment.assemblySlot);
            getUuidSetField(fixture.formation, "recruitmentArrivalReports").add(fixture.worker.getUUID());

            invokeCoordinator("processRecruitmentReports",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), long.class},
                    fixture.level, fixture.formation, fixture.level.getGameTime());

            require(fixture.assignment.state == LivingWallCoordinator.MobTicketState.RELEASED,
                    "exhausted invalid assembled worker should release");
            require(!getMobOwners(fixture.level).containsKey(fixture.worker.getUUID()),
                    "release should restore ownership");
            require(!fixture.worker.isNoGravity(), "release should restore gravity");
            require(!fixture.worker.noPhysics, "release should restore noPhysics");
            helper.succeed();
        } finally {
            cleanupFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void structuralRouteUsesAlternateFreeStagingPose(GameTestHelper helper) throws Exception {
        StructuralFixture fixture = null;
        try {
            fixture = newStructuralFixture(helper, "structuralRouteUsesAlternateFreeStagingPose");
            @SuppressWarnings("unchecked")
            List<Vec3> candidates = (List<Vec3>) invokeCoordinator("structuralStagingCandidates",
                    new Class<?>[]{classFor("Formation"), int.class, Mob.class, Vec3.class},
                    fixture.formation, 1, fixture.worker, fixture.slotPosition);
            require(!candidates.isEmpty(), "expected deterministic staging candidates");
            require(candidates.size() > 1, "expected alternate staging candidates");
            require(candidates.get(0).distanceToSqr(candidates.get(1)) > 1.0E-6D,
                    "alternate staging candidates must be distinct");
            helper.succeed();
        } finally {
            cleanupStructuralFixture(fixture, null);
        }
    }

    @GameTest(template = "empty")
    public void structuralReleaseClearsExhaustedJitteringMember(GameTestHelper helper) throws Exception {
        StructuralFixture fixture = null;
        try {
            fixture = newStructuralFixture(helper, "structuralReleaseClearsExhaustedJitteringMember");
            setIntField(fixture.formation, "activeStructuralRouteRetries", getStaticInt("RECRUITER_RETRY_WINDOWS"));

            boolean reroute = (Boolean) invokeCoordinator("rerouteOrReleaseStructuralMember",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), Mob.class, int.class, String.class},
                    fixture.level, fixture.formation, fixture.worker, 1, "test_exhausted");

            require(!reroute, "exhausted structural retries should release instead of rerouting");
            require(!getUuidListField(fixture.formation, "members").contains(fixture.worker.getUUID()),
                    "released jittering member must leave the structural roster");
            require(!getMobOwners(fixture.level).containsKey(fixture.worker.getUUID()),
                    "released jittering member must clear ownership");
            require(!fixture.worker.isNoGravity(), "release should restore gravity");
            require(!fixture.worker.noPhysics, "release should restore noPhysics");
            helper.succeed();
        } finally {
            cleanupStructuralFixture(fixture, null);
        }
    }

    private record Fixture(ServerLevel level, Mob leader, Mob worker, Object formation,
                           LivingWallCoordinator.MobTicket assignment,
                           LivingWallCoordinator.IntakeWindow window, BlockPos feeder) {
    }

    private record StructuralFixture(ServerLevel level, Mob leader, Mob foundation, Mob worker,
                                     Object formation, Vec3 slotPosition) {
    }

    private static Fixture newFixture(GameTestHelper helper, String name) throws Exception {
        return newFixture(helper, name, false);
    }

    private static Fixture newFixture(GameTestHelper helper, String name, boolean includeFeederAsPlannedSlot) throws Exception {
        int lane = name.endsWith("B") ? 1 : 0;
        int baseX = 1;
        int baseZ = 1 + lane * 3;
        long revision = Math.floorMod(name.hashCode(), 100000) + 1L;
        prepareSurface(helper, 0, 6, 0, 6);
        ServerLevel level = helper.getLevel();
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, baseX + 3, 1, baseZ);
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, baseX, 1, baseZ + 1);
        BlockPos localFeeder = new BlockPos(baseX, 1, baseZ);
        BlockPos localAssembly = new BlockPos(baseX + 1, 1, baseZ);
        helper.setBlock(localFeeder.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(localFeeder, Blocks.AIR.defaultBlockState());
        helper.setBlock(localFeeder.above(), Blocks.AIR.defaultBlockState());
        helper.setBlock(localAssembly.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(localAssembly, Blocks.AIR.defaultBlockState());
        helper.setBlock(localAssembly.above(), Blocks.AIR.defaultBlockState());
        BlockPos feeder = helper.absolutePos(localFeeder);
        BlockPos feederApproach = feeder;
        BlockPos assembly = helper.absolutePos(localAssembly);
        BlockPos anchor = helper.absolutePos(new BlockPos(baseX + 2, 1, baseZ));
        BlockPos spanEnd = helper.absolutePos(new BlockPos(baseX + 4, 1, baseZ));
        BlockPos leaderTarget = spanEnd;
        LivingWallCoordinator.IntakeWindow window = new LivingWallCoordinator.IntakeWindow(feeder, feederApproach, true);
        List<BlockPos> assemblySlots = includeFeederAsPlannedSlot ? List.of(assembly, feeder) : List.of(assembly);
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                anchor, spanEnd, spanEnd,
                new Vec3(1.0D, 0.0D, 0.0D), List.of(anchor, helper.absolutePos(new BlockPos(baseX + 3, 1, baseZ))),
                List.of(window), assemblySlots, Set.of(assembly, feeder),
                List.of());
        Object request = newConstructionRequest(
                Vec3.atBottomCenterOf(anchor),
                anchor, spanEnd, spanEnd, spanEnd, null, 0L, 0L, revision);
        Object formation = newFormation(leader, leaderTarget, plan, request, 2, 2, 4,
                newBodyGeometry(0.6D, 1.95D), level.getGameTime());
        setFieldValue(formation, "phase", phase("ASSEMBLE"));
        setIntField(formation, "columnCount", 1);
        setIntField(formation, "requiredBodies", 2);
        setIntField(formation, "assembledSlots", 1);
        setLongField(formation, "lastRecruitmentProgressTick", level.getGameTime());
        setLongField(formation, "lastAssemblyProgressTick", level.getGameTime());
        LivingWallCoordinator.MobTicket assignment = new LivingWallCoordinator.MobTicket(worker.getUUID(), assembly, 1L);
        assignment.state = LivingWallCoordinator.MobTicketState.SUCTIONING;
        assignment.intakeWindow = window;
        assignment.goalStarted = true;
        assignment.grantTick = level.getGameTime();
        assignment.controllerStartTick = level.getGameTime();
        assignment.navigationSubmissionTick = Long.MIN_VALUE;
        getMapField(formation, "mobTickets").put(worker.getUUID(), assignment);
        getUuidBooleanMap(formation, "ticketGoals").put(worker.getUUID(), Boolean.TRUE);
        registerFormation(level, formation, worker);
        worker.setPos(Vec3.atBottomCenterOf(feeder));
        return new Fixture(level, leader, worker, formation, assignment, window, feeder);
    }

    private static StructuralFixture newStructuralFixture(GameTestHelper helper, String name) throws Exception {
        int lane = name.endsWith("B") ? 1 : 0;
        int baseX = 1;
        int baseZ = 1 + lane * 4;
        long revision = Math.floorMod(name.hashCode(), 100000) + 1L;
        prepareSurface(helper, 0, 6, 0, 8);
        ServerLevel level = helper.getLevel();
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, baseX + 4, 1, baseZ);
        Mob foundation = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, baseX + 1, 1, baseZ + 1);
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, baseX + 2, 1, baseZ + 1);
        BlockPos pivot = helper.absolutePos(new BlockPos(baseX + 1, 1, baseZ + 1));
        BlockPos anchor = helper.absolutePos(new BlockPos(baseX + 1, 2, baseZ + 1));
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                pivot, anchor, anchor, new Vec3(0.0D, 0.0D, 1.0D),
                List.of(pivot), List.of(new LivingWallCoordinator.IntakeWindow(pivot.west())),
                List.of(pivot), Set.of(pivot), List.of());
        Object request = newConstructionRequest(Vec3.atBottomCenterOf(pivot), pivot, anchor, anchor, anchor,
                null, 0L, 0L, revision);
        Object formation = newFormation(leader, anchor, plan, request, 2, 1, 2,
                newBodyGeometry(0.6D, 1.95D), level.getGameTime());
        setFieldValue(formation, "phase", phase("ASSEMBLE"));
        setIntField(formation, "columnCount", 1);
        setIntField(formation, "layerCount", 2);
        setIntField(formation, "requiredBodies", 2);
        setIntField(formation, "assembledSlots", 1);
        setLongField(formation, "lastRecruitmentProgressTick", level.getGameTime());
        setLongField(formation, "lastAssemblyProgressTick", level.getGameTime());
        @SuppressWarnings("unchecked")
        List<UUID> members = (List<UUID>) getFieldValue(formation, "members");
        members.add(foundation.getUUID());
        members.add(worker.getUUID());
        getBooleanMap(formation, "originalGravity").put(foundation.getUUID(), Boolean.TRUE);
        getBooleanMap(formation, "originalGravity").put(worker.getUUID(), Boolean.TRUE);
        getBooleanMap(formation, "originalPersistence").put(foundation.getUUID(), Boolean.FALSE);
        getBooleanMap(formation, "originalPersistence").put(worker.getUUID(), Boolean.FALSE);
        getBooleanMap(formation, "originalNoPhysics").put(foundation.getUUID(), Boolean.FALSE);
        getBooleanMap(formation, "originalNoPhysics").put(worker.getUUID(), Boolean.FALSE);
        getUuidUuidMap(formation, "originalTargets").put(foundation.getUUID(), null);
        getUuidUuidMap(formation, "originalTargets").put(worker.getUUID(), null);
        registerFormation(level, formation, worker);
        Vec3 foundationPosition = Vec3.atBottomCenterOf(pivot);
        foundation.setPos(foundationPosition);
        foundation.setNoGravity(true);
        foundation.noPhysics = true;
        ((Map<UUID, Vec3>) getFieldValue(formation, "assemblyHoldPositions")).put(foundation.getUUID(), foundationPosition);
        Vec3 slotPosition = (Vec3) invokeCoordinator("formationSlotPosition",
                new Class<?>[]{classFor("Formation"), int.class, double.class},
                formation, 1, 0.0D);
        worker.setPos(foundationPosition.add(3.0D, 0.0D, 0.0D));
        return new StructuralFixture(level, leader, foundation, worker, formation, slotPosition);
    }

    @GameTest(template = "empty")
    public void unsafeDockRetainsLaneAndVerifiedSafeDockCommitsTerminalReceipt(GameTestHelper helper) throws Exception {
        StructuralFixture fixture = null;
        try {
            fixture = newStructuralFixture(helper, "unsafeDockRetainsLaneAndVerifiedSafeDockCommitsTerminalReceipt");
            ServerLevel level = fixture.level;
            Mob worker = fixture.worker;
            BlockPos dock = (BlockPos) ((List<?>) getFieldValue(
                    getFieldValue(fixture.formation, "plan"), "assemblySlots")).get(0);
            UUID formationId = (UUID) getFieldValue(fixture.formation, "id");
            UUID reservationId = UUID.randomUUID();
            LivingBridgeRouteSnapshot active = new LivingBridgeRouteSnapshot(reservationId, worker.getUUID(),
                    formationId, level.dimension().location().toString(), 4L, UUID.randomUUID(), 1L,
                    "CONTROLLED_BASE_APPROACH", "CONTROLLED_APPROACH", false, false, false,
                    List.of(LivingBridgeRouteSnapshot.Point.from(dock.getX(), dock.getY(), dock.getZ()),
                            LivingBridgeRouteSnapshot.Point.from(dock.getX() + 1.0D, dock.getY(), dock.getZ())),
                    3072L, List.of(), 0, null, worker.chunkPosition().x, worker.chunkPosition().z);
            LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
            data.putSnapshot(active);
            net.minecraft.nbt.CompoundTag capsule = new net.minecraft.nbt.CompoundTag();
            capsule.putInt("schema", LivingBridgeRouteData.CURRENT_SCHEMA);
            capsule.put("snapshot", active.toTag());
            capsule.putString("snapshotDigest", active.digest());
            worker.getPersistentData().put("livingBridgeControl", capsule.copy());

            worker.setPos(Vec3.atBottomCenterOf(dock));
            BlockPos unsafe = dock.above();
            boolean rejected = (Boolean) invokeCoordinator("persistSafeBaseDocked",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), Mob.class, BlockPos.class},
                    level, fixture.formation, worker, unsafe);
            require(!rejected, "unsupported endpoint must not retire the active lane");
            require(data.snapshot(reservationId).exactlyEquals(active)
                            && worker.getPersistentData().getCompound("livingBridgeControl").equals(capsule),
                    "unsafe dock rejection must preserve exact SavedData and entity capsule copies");
            boolean committed = (Boolean) invokeCoordinator("persistSafeBaseDocked",
                    new Class<?>[]{ServerLevel.class, classFor("Formation"), Mob.class, BlockPos.class},
                    level, fixture.formation, worker, dock);
            LivingBridgeRouteSnapshot terminal = data.snapshot(reservationId);
            require(committed && terminal != null && "DOCKED".equals(terminal.lifecycleState())
                            && terminal.safeEndpoint() != null && !terminal.recoveryRequired(),
                    "validated supported endpoint must commit a non-recovering terminal dock receipt");
            net.minecraft.nbt.CompoundTag terminalCapsule = worker.getPersistentData()
                    .getCompound("livingBridgeControl");
            LivingBridgeRouteSnapshot entityCopy = LivingBridgeRouteSnapshot.fromTag(
                    terminalCapsule.getCompound("snapshot"));
            require(terminal.exactlyEquals(entityCopy)
                            && terminal.digest().equals(terminalCapsule.getString("snapshotDigest")),
                    "terminal receipt must atomically converge the entity capsule and SavedData copies");
            require(LivingBridgeRouteData.isTerminalLaneState(terminal.lifecycleState()),
                    "verified dock receipt must be classified as terminal and stop occupying a lane");
            helper.succeed();
        } finally {
            cleanupStructuralFixture(fixture, null);
        }
    }

    private static void holdForCleanup(Fixture fixture) throws Exception {
        fixture.worker.setNoGravity(true);
        fixture.worker.noPhysics = true;
        fixture.worker.setTarget(null);
        fixture.worker.setAggressive(false);
        getBooleanMap(fixture.formation, "originalGravity").put(fixture.worker.getUUID(), Boolean.TRUE);
        getBooleanMap(fixture.formation, "originalPersistence").put(fixture.worker.getUUID(),
                ((MobAccessor) fixture.worker).isPersistenceRequiredFlag());
        getBooleanMap(fixture.formation, "originalNoPhysics").put(fixture.worker.getUUID(), Boolean.FALSE);
    }

    private static void cleanupFixture(Fixture fixture, Entity extra) {
        if (extra != null && extra.isAlive()) extra.discard();
        if (fixture == null) return;
        if (fixture.worker.isAlive()) fixture.worker.discard();
        if (fixture.leader.isAlive()) fixture.leader.discard();
        clearLevelState(fixture.level);
    }

    private static void cleanupStructuralFixture(StructuralFixture fixture, Entity extra) {
        if (extra != null && extra.isAlive()) extra.discard();
        if (fixture == null) return;
        if (fixture.worker.isAlive()) fixture.worker.discard();
        if (fixture.foundation.isAlive()) fixture.foundation.discard();
        if (fixture.leader.isAlive()) fixture.leader.discard();
        clearLevelState(fixture.level);
    }

    private static final class CountingZombie extends Zombie {
        private int pathCreations;

        private CountingZombie(Level level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public boolean isWithinMeleeAttackRange(LivingEntity target) {
            return false;
        }

        @Override
        protected PathNavigation createNavigation(Level level) {
            return new GroundPathNavigation(this, level) {
                @Override
                public Path createPath(Entity target, int accuracy) {
                    pathCreations++;
                    if (target == null) return null;
                    BlockPos targetPos = target.blockPosition();
                    return new Path(List.of(new Node(targetPos.getX(), targetPos.getY(), targetPos.getZ())),
                            targetPos, true);
                }
            };
        }
    }

    private static void prepareSurface(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void registerFormation(ServerLevel level, Object formation, Mob worker) throws Exception {
        Object state = state(level);
        UUID formationId = (UUID) getFieldValue(formation, "id");
        @SuppressWarnings("unchecked")
        Map<UUID, Object> formations = (Map<UUID, Object>) getFieldValue(state, "formations");
        formations.put(formationId, formation);
        getMobOwners(level).put(worker.getUUID(), formationId);
    }

    private static Object findSafeBaseRoute(ServerLevel level, Object formation, Mob worker, int slot) throws Exception {
        return invokeCoordinator("findSafeBaseNavigationRoute",
                new Class<?>[]{ServerLevel.class, classFor("Formation"), Mob.class, int.class},
                level, formation, worker, slot);
    }

    private static void clearSafeBaseRouteState(ServerLevel level, Object formation, Mob worker) throws Exception {
        ((Map<?, ?>) getFieldValue(formation, "safeBaseNavigation")).clear();
        ((Map<?, ?>) getFieldValue(formation, "structuralApproachReservations")).clear();
        ((Map<?, ?>) getFieldValue(formation, "rejectedSafeBaseStaging")).remove(worker.getUUID());
        ((Map<?, ?>) getFieldValue(formation, "lastReportedSafeBaseStagingDiagnostics")).remove(worker.getUUID());
        ((Map<?, ?>) getFieldValue(LivingBridgeRouteData.get(level), "reservations"))
                .remove(routeReservationId((UUID) getFieldValue(formation, "id"), worker.getUUID()));
    }

    private static LivingWallCoordinator.SitePlan routeTestPlan(Object formation, List<BlockPos> hazards) throws Exception {
        LivingWallCoordinator.SitePlan plan = (LivingWallCoordinator.SitePlan) getFieldValue(formation, "plan");
        return new LivingWallCoordinator.SitePlan(plan.scenario(), plan.pivot(), plan.anchor(),
                plan.pivot().offset(0, 0, 4),
                plan.forward(), plan.baseCells(), plan.intakeWindows(), plan.assemblySlots(),
                plan.validationCells(), hazards, plan.corridor());
    }

    private static List<BlockPos> enumerateRouteCandidates(ServerLevel level, Object formation, Mob mob) throws Exception {
        LivingWallCoordinator.SitePlan plan = (LivingWallCoordinator.SitePlan) getFieldValue(formation, "plan");
        Vec3 forward = LivingBridgeSafeBaseRoute.verifiedForward(plan.pivot(), plan.spanEnd(), plan.forward());
        require(forward != null, "route fixture requires a verified forward direction");
        Vec3 pivot = Vec3.atBottomCenterOf(plan.pivot());
        double margin = mob.getBbWidth() * 0.5D * (Math.abs(forward.x) + Math.abs(forward.z)) + 1.0D;
        Vec3 targetCenter = pivot.add(new Vec3(-forward.x, 0.0D, -forward.z).scale(margin + 1.0D));
        List<BlockPos> candidates = new ArrayList<>();
        for (int yOffset = -2; yOffset <= 2; yOffset++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidate = (BlockPos) invokeCoordinator("resolveLocalSafeSurface",
                            new Class<?>[]{ServerLevel.class, int.class, int.class, int.class}, level,
                            Mth.floor(targetCenter.x) + dx, Mth.floor(targetCenter.z) + dz,
                            Mth.floor(targetCenter.y) + yOffset);
                    if (candidate != null) candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private static UUID routeReservationId(UUID formationId, UUID workerId) {
        return UUID.nameUUIDFromBytes((formationId + ":" + workerId).getBytes(StandardCharsets.UTF_8));
    }

    private static BlockPos relativePos(GameTestHelper helper, BlockPos absolute) {
        return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> getMobOwners(ServerLevel level) throws Exception {
        return (Map<UUID, UUID>) getFieldValue(state(level), "mobOwners");
    }

    private static Object state(ServerLevel level) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod("state", ServerLevel.class);
        method.setAccessible(true);
        return method.invoke(null, level);
    }

    private static Object invokeCoordinator(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Class<?> classFor(String simpleName) throws Exception {
        return Class.forName("com.example.soundattract.ai.LivingWallCoordinator$" + simpleName);
    }

    private static Object phase(String name) throws Exception {
        Class<?> phaseClass = classFor("Phase");
        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumClass = (Class<? extends Enum>) phaseClass.asSubclass(Enum.class);
        return Enum.valueOf(enumClass, name);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, LivingWallCoordinator.IntakeWindow> getProvenanceMap(Object formation) throws Exception {
        return (Map<UUID, LivingWallCoordinator.IntakeWindow>) getFieldValue(formation, "assemblyIntakeProvenance");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Boolean> getUuidBooleanMap(Object target, String fieldName) throws Exception {
        return (Map<UUID, Boolean>) getFieldValue(target, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> getUuidListField(Object target, String fieldName) throws Exception {
        return (List<UUID>) getFieldValue(target, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> getUuidSetField(Object target, String fieldName) throws Exception {
        return (Set<UUID>) getFieldValue(target, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Boolean> getBooleanMap(Object target, String fieldName) throws Exception {
        return (Map<UUID, Boolean>) getFieldValue(target, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> getUuidUuidMap(Object target, String fieldName) throws Exception {
        return (Map<UUID, UUID>) getFieldValue(target, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> getMapField(Object target, String fieldName) throws Exception {
        return (Map<UUID, Object>) getFieldValue(target, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, UUID> getBlockPosUuidMap(Object target, String fieldName) throws Exception {
        return (Map<BlockPos, UUID>) getFieldValue(target, fieldName);
    }

    private static Object getFieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setLongField(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static int getStaticInt(String fieldName) throws Exception {
        Field field = LivingWallCoordinator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    @SuppressWarnings("unchecked")
    private static void clearLevelState(ServerLevel level) {
        try {
            Field levels = LivingWallCoordinator.class.getDeclaredField("LEVELS");
            levels.setAccessible(true);
            ((Map<ResourceLocation, Object>) levels.get(null)).remove(level.dimension().location());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Object newFormation(Mob leader, BlockPos target, LivingWallCoordinator.SitePlan plan,
                                       Object request, int wallHeight, int gapWidth,
                                       int availableBodies, Object geometry, long now) throws Exception {
        Class<?> type = classFor("Formation");
        Class<?> requestType = classFor("ConstructionRequest");
        Class<?> geometryType = classFor("BodyGeometry");
        Constructor<?> ctor = type.getDeclaredConstructor(Mob.class, BlockPos.class,
                LivingWallCoordinator.SitePlan.class, requestType, int.class, int.class, int.class, geometryType, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(leader, target, plan, request, wallHeight, gapWidth, availableBodies, geometry, now);
    }

    private static Object newConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                                                 BlockPos selectedAnchor, BlockPos threatBucket, Object evidence,
                                                 long expiresAt, long scanExpiresAt, long revision) throws Exception {
        Class<?> type = classFor("ConstructionRequest");
        Constructor<?> ctor = type.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class,
                BlockPos.class, Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence"),
                long.class, long.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket,
                evidence, expiresAt, scanExpiresAt, revision);
    }

    private static Object newConstructionRequestWithSnapshot(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                                                             BlockPos selectedAnchor, BlockPos threatBucket, Object snapshot,
                                                             Object evidence, Object routeEvidence,
                                                             long expiresAt, long scanExpiresAt, long revision) throws Exception {
        Class<?> type = classFor("ConstructionRequest");
        Constructor<?> ctor = type.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class,
                BlockPos.class, classFor("TargetScanSnapshot"),
                Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence"),
                classFor("RouteEvidence"), long.class, long.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket,
                snapshot, evidence, routeEvidence, expiresAt, scanExpiresAt, revision);
    }

    private static Object newTargetSnapshot(ServerLevel level, Vec3 sourceOrigin, Vec3 sourceCenter, BlockPos threatIdentity,
                                            Object evidence, long captureTick, long revision, long expiryTick,
                                            Object routeEvidence) throws Exception {
        Class<?> scenarioClass = classFor("ConstructionScenario");
        Class<?> snapshotClass = classFor("TargetScanSnapshot");
        Constructor<?> ctor = snapshotClass.getDeclaredConstructor(Vec3.class, Vec3.class, BlockPos.class,
                Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence"),
                long.class, long.class, long.class, double.class, double.class, double.class,
                scenarioClass, List.class, Class.forName("com.example.soundattract.ai.LivingWallTacticalSnapshot$Identity"),
                classFor("RouteEvidence"));
        ctor.setAccessible(true);
        List<Object> fingerprint = new ArrayList<>();
        fingerprint.add(newCellFingerprint(anchorFromEvidence(evidence), "anchor", liveFingerprint(level, anchorFromEvidence(evidence))));
        fingerprint.add(newCellFingerprint(wallFromEvidence(evidence), "wall", liveFingerprint(level, wallFromEvidence(evidence))));
        fingerprint.add(newCellFingerprint(supportFromEvidence(evidence), "support", liveFingerprint(level, supportFromEvidence(evidence))));
        Object intakeWindow = invokeRecordComponent(routeEvidence, "intakeWindow");
        BlockPos feeder = (BlockPos) invokeRecordComponent(intakeWindow, "feederCell");
        BlockPos approach = (BlockPos) invokeRecordComponent(intakeWindow, "feederApproach");
        fingerprint.add(newCellFingerprint(feeder, "intake", liveFingerprint(level, feeder)));
        fingerprint.add(newCellFingerprint(approach, "intake_approach", liveFingerprint(level, approach)));
        Object identity = newTacticalIdentity(level, sourceOrigin, sourceCenter, threatIdentity, captureTick, revision);
        Object scenario = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        return ctor.newInstance(sourceOrigin, sourceCenter, threatIdentity, evidence, captureTick, revision, expiryTick,
                0.31D, 0.6D, 1.95D, scenario, fingerprint, identity, routeEvidence);
    }

    private static Object newRouteEvidence(ServerLevel level, Mob worker, LivingWallCoordinator.IntakeWindow intakeWindow,
                                           BlockPos anchor, List<BlockPos> routeNodes,
                                           boolean pathReachable, long revision) throws Exception {
        Class<?> type = classFor("RouteEvidence");
        Constructor<?> ctor = type.getDeclaredConstructor(UUID.class, LivingWallCoordinator.IntakeWindow.class, BlockPos.class,
                List.class, boolean.class, long.class, String.class, String.class, ResourceLocation.class, Vec3.class,
                net.minecraft.world.phys.AABB.class, net.minecraft.world.entity.Pose.class, BlockPos.class, String.class);
        ctor.setAccessible(true);
        List<BlockPos> certifiedNodes = List.of(worker.blockPosition(), intakeWindow.feederApproach());
        String worldFingerprint = (String) invokeCoordinator("routeWorldFingerprint",
                new Class<?>[]{ServerLevel.class, List.class}, level, certifiedNodes);
        String fingerprint = worker.getUUID() + "|" + intakeWindow + "|" + anchor + "|" + certifiedNodes + "|" + revision;
        return ctor.newInstance(worker.getUUID(), intakeWindow, anchor, certifiedNodes, pathReachable, revision,
                fingerprint, "", level.dimension().location(), worker.position(), worker.getBoundingBox(),
                worker.getPose(), intakeWindow.feederApproach(), worldFingerprint);
    }

    private static Object newTargetEvidence(BlockPos anchor, List<Long> rayIds, List<BlockPos> hazardCells, List<?> hazardKinds,
                                            BlockPos wallFace, BlockPos supportCell, BlockPos sourceBucket, BlockPos targetBucket,
                                            String scenario, double score, String rejectionCode) throws Exception {
        Constructor<?> ctor = Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence")
                .getDeclaredConstructor(BlockPos.class, List.class, List.class, List.class, BlockPos.class, BlockPos.class,
                        BlockPos.class, BlockPos.class, String.class, double.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(anchor, rayIds, hazardCells, hazardKinds, wallFace, supportCell, sourceBucket, targetBucket,
                scenario, score, rejectionCode);
    }

    private static Object newCellFingerprint(BlockPos cell, String kind, String liveStateFingerprint) throws Exception {
        Constructor<?> ctor = classFor("CellFingerprint").getDeclaredConstructor(BlockPos.class, String.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(cell, kind, liveStateFingerprint);
    }

    private static Object newTacticalIdentity(ServerLevel level, Vec3 sourceOrigin, Vec3 sourceCenter, BlockPos threatIdentity,
                                              long captureTick, long revision) throws Exception {
        Constructor<?> ctor = Class.forName("com.example.soundattract.ai.LivingWallTacticalSnapshot$Identity")
                .getDeclaredConstructor(ResourceLocation.class, UUID.class, UUID.class, String.class, String.class, long.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(level.dimension().location(), null, null, "",
                sourceOrigin + "|" + sourceCenter + "|" + threatIdentity + "|" + captureTick, level.getGameTime(), revision);
    }

    private static String liveFingerprint(ServerLevel level, BlockPos cell) throws Exception {
        return (String) invokeCoordinator("liveStateFingerprint", new Class<?>[]{ServerLevel.class, BlockPos.class}, level, cell);
    }

    private static BlockPos anchorFromEvidence(Object evidence) throws Exception {
        return (BlockPos) invokeRecordComponent(evidence, "anchor");
    }

    private static BlockPos wallFromEvidence(Object evidence) throws Exception {
        return (BlockPos) invokeRecordComponent(evidence, "wallFace");
    }

    private static BlockPos supportFromEvidence(Object evidence) throws Exception {
        return (BlockPos) invokeRecordComponent(evidence, "supportCell");
    }

    private static Object newBodyGeometry(double width, double height) throws Exception {
        Class<?> type = classFor("BodyGeometry");
        Constructor<?> ctor = type.getDeclaredConstructor(double.class, double.class);
        ctor.setAccessible(true);
        return ctor.newInstance(width, height);
    }

    @GameTest(template = "empty")
    public void clearanceResolverUsesFeetCoordinates(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 4, 0, 4);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Vec3 requested = new Vec3(mob.getX(), mob.getBoundingBox().minY, mob.getZ());
        Object resolution = invokeClearanceResolver(helper.getLevel(), mob, requested, 0.0D);
        Vec3 resolved = (Vec3) invokeRecordComponent(resolution, "position");
        AABB box = (AABB) invokeRecordComponent(resolution, "box");
        require((Boolean) invokeRecordComponent(resolution, "clear"), "expected clear feet-position resolution");
        require(resolved != null, "expected a resolved position");
        require(Math.abs(resolved.x - requested.x) <= 1.0E-6D, "resolved x should preserve the requested center");
        require(Math.abs(resolved.z - requested.z) <= 1.0E-6D, "resolved z should preserve the requested center");
        require(Math.abs(resolved.y - requested.y) <= 1.0E-6D, "resolved y should preserve requested feet height");
        require(Math.abs(resolved.y - box.minY) <= 1.0E-6D, "resolved y should match the resolved feet box minY");

        Object repeated = invokeClearanceResolver(helper.getLevel(), mob, resolved, 0.0D);
        Vec3 repeatedPosition = (Vec3) invokeRecordComponent(repeated, "position");
        require(repeatedPosition != null, "expected repeated resolution to stay valid");
        require(Math.abs(repeatedPosition.y - resolved.y) <= 1.0E-6D, "repeated resolution must be vertically idempotent");

        helper.setBlock(new BlockPos(2, 0, 2), Blocks.GRASS_BLOCK.defaultBlockState());
        Vec3 liftedRequest = new Vec3(2.5D, requested.y - 0.14D, 2.5D);
        Object lifted = invokeClearanceResolver(helper.getLevel(), mob, liftedRequest, 0.6D);
        Vec3 liftedPosition = (Vec3) invokeRecordComponent(lifted, "position");
        double supportLift = (Double) invokeRecordComponent(lifted, "supportLift");
        require((Boolean) invokeRecordComponent(lifted, "clear"), "expected lifted resolution to remain clear");
        require(liftedPosition != null, "expected lifted resolution to produce a position");
        require(Math.abs(liftedPosition.x - liftedRequest.x) <= 1.0E-6D, "support lift must preserve x");
        require(Math.abs(liftedPosition.z - liftedRequest.z) <= 1.0E-6D, "support lift must preserve z");
        require(Math.abs(liftedPosition.y - (liftedRequest.y + supportLift)) <= 1.0E-6D,
                "support lift must adjust only feet y by the measured lift");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void clearanceResolverLiftsFractionalFeetOntoGroundSupport(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 4, 0, 4);
        helper.setBlock(new BlockPos(2, -1, 2), Blocks.GRASS_BLOCK.defaultBlockState());
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.AIR.defaultBlockState());
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 0, 2);
        Vec3 buriedFeet = new Vec3(mob.getX(), mob.getBoundingBox().minY - 0.14D, mob.getZ());
        Object resolution = invokeClearanceResolver(helper.getLevel(), mob, buriedFeet, 0.6D);
        Vec3 resolved = (Vec3) invokeRecordComponent(resolution, "position");
        double supportLift = (Double) invokeRecordComponent(resolution, "supportLift");
        require(resolved != null, "expected a resolved position");
        require(supportLift > 0.0D, "expected support lift");
        require(supportLift <= mob.maxUpStep() + 1.0E-6D, "support lift must respect max-up-step");
        require((Boolean) invokeRecordComponent(resolution, "clear"), "expected the lifted feet position to resolve clear");
        require(Math.abs(resolved.y - (buriedFeet.y + supportLift)) <= 1.0E-6D,
                "support lift should raise the buried feet request by the measured amount");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void clearanceResolverRejectsOverheadCollisionAndAllowsBoundaryContact(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 4, 0, 4);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        double feetY = mob.getBoundingBox().minY;
        int bodyY = 1;
        helper.setBlock(new BlockPos(2, bodyY, 2), Blocks.STONE.defaultBlockState());
        Object blocked = invokeClearanceResolver(helper.getLevel(), mob, new Vec3(mob.getX(), feetY, mob.getZ()), 0.0D);
        require(!(Boolean) invokeRecordComponent(blocked, "clear"), "expected the slab overlap to block clearance");
        helper.setBlock(new BlockPos(2, bodyY, 2), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE.defaultBlockState());
        Object boundary = invokeClearanceResolver(helper.getLevel(), mob, new Vec3(mob.getX(), feetY, mob.getZ()), 0.0D);
        require((Boolean) invokeRecordComponent(boundary, "clear"), "boundary contact should remain allowed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void clearanceResolverLeavesSuspendedSpanValidOverAir(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 6, 0, 6);
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.AIR.defaultBlockState());
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        Object resolution = invokeClearanceResolver(helper.getLevel(), mob, new Vec3(mob.getX(), mob.getBoundingBox().minY, mob.getZ()), 0.0D);
        require((Boolean) invokeRecordComponent(resolution, "clear"), "suspended span over air should stay valid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void clearanceResolverRespectsTighterMaxUpStep(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 4, 0, 4);
        helper.setBlock(new BlockPos(2, -1, 2), Blocks.GRASS_BLOCK.defaultBlockState());
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.AIR.defaultBlockState());
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 0, 2);
        Vec3 buriedFeet = new Vec3(mob.getX(), mob.getBoundingBox().minY - 0.14D, mob.getZ());
        Object tight = invokeClearanceResolver(helper.getLevel(), mob, buriedFeet, 0.05D);
        Object loose = invokeClearanceResolver(helper.getLevel(), mob, buriedFeet, 0.6D);
        require(!(Boolean) invokeRecordComponent(tight, "clear"), "tight max-up-step should fail");
        require((Boolean) invokeRecordComponent(loose, "clear"), "looser max-up-step should pass");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void sourceStagingTrapdoorExceptionIsNarrowAndGenericClearanceRemainsStrict(GameTestHelper helper) throws Exception {
        prepareSurface(helper, 0, 5, 0, 5);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 1.30D));
        BlockPos sideTrapdoor = new BlockPos(2, 1, 1);
        BlockState openSide = Blocks.CRIMSON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.POWERED, false)
                .setValue(TrapDoorBlock.WATERLOGGED, false);
        helper.setBlock(sideTrapdoor, openSide);
        BlockPos absoluteSideTrapdoor = helper.absolutePos(sideTrapdoor);
        AABB trapdoorShape = openSide.getCollisionShape(helper.getLevel(), sideTrapdoor)
                .bounds().move(absoluteSideTrapdoor.getX(), absoluteSideTrapdoor.getY(), absoluteSideTrapdoor.getZ());
        boolean thinZ = trapdoorShape.getZsize() < trapdoorShape.getXsize();
        boolean lowSide = thinZ
                ? trapdoorShape.minZ < absoluteSideTrapdoor.getZ() + 0.5D
                : trapdoorShape.minX < absoluteSideTrapdoor.getX() + 0.5D;
        double halfWidth = mob.getBbWidth() * 0.5D;
        double contactOverlap = 0.01D;
        double acceptedX = thinZ ? absoluteSideTrapdoor.getX() + 0.5D
                : (lowSide ? trapdoorShape.minX + halfWidth - contactOverlap
                        : trapdoorShape.maxX - halfWidth + contactOverlap);
        double acceptedZ = thinZ ? (lowSide ? trapdoorShape.minZ + halfWidth - contactOverlap
                        : trapdoorShape.maxZ - halfWidth + contactOverlap)
                : absoluteSideTrapdoor.getZ() + 0.5D;
        mob.setPos(acceptedX, mob.getY(), acceptedZ);
        Vec3 request = new Vec3(mob.getX(), mob.getBoundingBox().minY, mob.getZ());
        Object staging = invokeSourceStagingClearance(helper.getLevel(), mob, request, 0.0D);
        Object generic = invokeClearanceResolver(helper.getLevel(), mob, request, 0.0D);
        require((Boolean) invokeRecordComponent(staging, "clear"),
                "open vertical side-boundary trapdoor should be allowed only for source staging");
        require(!(Boolean) invokeRecordComponent(generic, "clear"),
                "generic clearance must continue rejecting the open trapdoor shape=" + trapdoorShape
                        + " body=" + mob.getBoundingBox());

        helper.setBlock(sideTrapdoor, openSide.setValue(TrapDoorBlock.OPEN, false));
        BlockPos closedTopTrapdoor = new BlockPos(2, 2, 1);
        BlockState closedTop = openSide.setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.WEST);
        helper.setBlock(closedTopTrapdoor, closedTop);
        require(!(Boolean) invokeRecordComponent(invokeSourceStagingClearance(
                helper.getLevel(), mob, request, 0.0D), "clear"),
                "closed top-half body-core trapdoor must remain blocked for source staging");
        require(!(Boolean) invokeRecordComponent(invokeClearanceResolver(
                helper.getLevel(), mob, request, 0.0D), "clear"),
                "generic clearance must continue rejecting the closed top-half trapdoor shape");

        BlockState feetTrapdoor = closedTop.setValue(TrapDoorBlock.HALF, Half.BOTTOM);
        helper.setBlock(sideTrapdoor, feetTrapdoor);
        require(!(Boolean) invokeRecordComponent(invokeSourceStagingClearance(
                helper.getLevel(), mob, request, 0.0D), "clear"),
                "feet-cell trapdoor must be rejected before shell admission");

        helper.setBlock(closedTopTrapdoor, closedTop.setValue(TrapDoorBlock.WATERLOGGED, true));
        Object waterlogged = invokeSourceStagingClearance(helper.getLevel(), mob, request, 0.0D);
        require(!(Boolean) invokeRecordComponent(waterlogged, "clear"),
                "waterlogged trapdoor must remain a source-staging blocker");

        ServerLevel level = helper.getLevel();
        Vec3 unloadedPosition = findConfirmedUnloadedPosition(helper);
        Object unloaded = invokeSourceStagingClearance(level, mob, unloadedPosition, 0.0D);
        require(!(Boolean) invokeRecordComponent(unloaded, "clear"),
                "unloaded source-staging space must remain blocked");
        try {
            require((Boolean) invokeRecordComponent(unloaded, "unloaded"),
                    "source-staging clearance must identify the unloaded space");
        } catch (NoSuchMethodException ignored) {

        }

        helper.setBlock(sideTrapdoor, openSide);
        double intrusion = 0.12D;
        double intrudingX = thinZ ? acceptedX : acceptedX + (lowSide ? -intrusion : intrusion);
        double intrudingZ = thinZ ? acceptedZ + (lowSide ? -intrusion : intrusion) : acceptedZ;
        Mob intrudingMob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 1.5D));
        intrudingMob.setPos(intrudingX, mob.getY(), intrudingZ);
        Vec3 intrudingRequest = new Vec3(intrudingMob.getX(), intrudingMob.getBoundingBox().minY, intrudingMob.getZ());
        require(!(Boolean) invokeRecordComponent(invokeSourceStagingClearance(
                helper.getLevel(), intrudingMob, intrudingRequest, 0.0D), "clear"),
                "positive body-core trapdoor intrusion must remain blocked");

        BlockPos support = new BlockPos(4, 0, 2);
        helper.setBlock(support, openSide);
        Method supportMethod = LivingWallCoordinator.class.getDeclaredMethod(
                "isStagingSupportCell", ServerLevel.class, BlockPos.class);
        supportMethod.setAccessible(true);
        require(!(Boolean) supportMethod.invoke(null, helper.getLevel(),
                        helper.absolutePos(support.above())),
                "trapdoor support must remain invalid for source staging");

        helper.setBlock(closedTopTrapdoor, Blocks.LAVA.defaultBlockState());
        require(!(Boolean) invokeRecordComponent(invokeSourceStagingClearance(
                helper.getLevel(), mob, request, 0.0D), "clear"),
                "lava must remain a source-staging blocker");
        helper.succeed();
    }

    private static Object invokeClearanceResolver(ServerLevel level, Mob mob, Vec3 position, double maxUpStep) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod("resolveClearance",
                ServerLevel.class, Mob.class, Vec3.class, double.class);
        method.setAccessible(true);
        return method.invoke(null, level, mob, position, maxUpStep);
    }

    private static Object invokeSourceStagingClearance(ServerLevel level, Mob mob, Vec3 position,
                                                       double maxUpStep) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod("resolveClearance",
                ServerLevel.class, Mob.class, Vec3.class, double.class, boolean.class);
        method.setAccessible(true);
        return method.invoke(null, level, mob, position, maxUpStep, true);
    }

    private static Object invokeRecordComponent(Object record, String name) throws Exception {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static Vec3 findConfirmedUnloadedPosition(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        ServerLevel level = helper.getLevel();
        for (int radius = 1; radius <= 32; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int chunkX = originChunkX + dx;
                    int chunkZ = originChunkZ + dz;
                    BlockPos candidate = new BlockPos(chunkX * 16 + 1, 1, chunkZ * 16 + 1);
                    if (!level.getChunkSource().hasChunk(chunkX, chunkZ)
                            && !level.isLoaded(candidate)) {
                        return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                    }
                }
            }
        }
        throw new AssertionError("bounded search found no confirmed-unloaded chunk");
    }
}
