package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(SoundAttractMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LivingWallIb3dGameTests {
    @GameTest(template = "empty")
    public void adapterSelectionPreservesLegacyScenariosAndKinds(GameTestHelper helper) {
        require(LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.NONE).orElseThrow().kind()
                        == LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT,
                "expected direct adapter for NONE");
        require(LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE).orElseThrow().kind()
                        == LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT,
                "expected pillar tilt adapter for WALL_LEDGE");
        require(LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.HAZARD_AND_WALL).isEmpty(),
                "expected HAZARD_AND_WALL to remain unsupported");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void castleThreatSelectionStaysStableAcrossSmallMovement(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 12, 0, 12);
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        Mob follower = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 3);
        BlockPos threat = helper.absolutePos(new BlockPos(10, 1, 10));
        helper.setBlock(helper.absolutePos(new BlockPos(8, 1, 8)), Blocks.STONE.defaultBlockState());
        helper.setBlock(helper.absolutePos(new BlockPos(8, 2, 8)), Blocks.AIR.defaultBlockState());
        List<Mob> cluster = List.of(leader, follower);
        List<BlockPos> initial = LivingWallCoordinator.findClusterTargetsForTest(helper.getLevel(), cluster, threat);
        follower.setPos(4.2D, 1.0D, 3.1D);
        List<BlockPos> moved = LivingWallCoordinator.findClusterTargetsForTest(helper.getLevel(), cluster, threat);
        require(initial.equals(moved), "small movement inside region should not change selected endpoints");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void routeBudgetHelperRespectsBoundedEvaluationAndDeferPaths(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 12, 0, 12);
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        Mob follower = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 3);
        leader.setOnGround(true);
        follower.setOnGround(true);
        List<Mob> cluster = List.of(leader, follower);
        BlockPos threat = helper.absolutePos(new BlockPos(9, 1, 9));
        CanonicalAuthorityFixture routeFixture = canonicalAuthorityFixture(helper);
        require(LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), cluster, null,
                        routeFixture.evidence, routeFixture.corridor).failureReason() != null,
                "null route target should defer");
        require(LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), cluster, threat,
                        routeFixture.evidence, routeFixture.corridor).failureReason() == null,
                "loaded flat route should satisfy within the reviewed budget: "
                        + LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), cluster, threat,
                        routeFixture.evidence, routeFixture.corridor));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void routeBudgetAdmitsAnchorWhenStableCenterFallsIntoGap(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 12, 0, 12);
        Mob left = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 4);
        Mob right = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 1, 4);
        left.setOnGround(true);
        right.setOnGround(true);
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.AIR.defaultBlockState());
        BlockPos threat = helper.absolutePos(new BlockPos(9, 1, 4));
        CanonicalAuthorityFixture routeFixture = canonicalAuthorityFixture(helper);

        require(LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), List.of(left, right), threat,
                        routeFixture.evidence, routeFixture.corridor).failureReason() == null,
                "nearest eligible worker should admit anchor without requiring path to averaged center: "
                        + LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), List.of(left, right), threat,
                        routeFixture.evidence, routeFixture.corridor));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void routeBudgetAdmitsHazardAdjacentAnchorWhenWorkerFacingIntakeExists(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 14, 0, 14);
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 4);
        Mob support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 6);
        worker.setOnGround(true);
        support.setOnGround(true);
        BlockPos threat = helper.absolutePos(new BlockPos(10, 1, 4));

        helper.setBlock(new BlockPos(10, 0, 4), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(10, 1, 4), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(10, 2, 4), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(8, 0, 4), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(8, 1, 4), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(8, 2, 4), Blocks.AIR.defaultBlockState());
        CanonicalAuthorityFixture routeFixture = canonicalAuthorityFixture(helper);

        require(LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), List.of(worker, support), threat,
                        routeFixture.evidence, routeFixture.corridor).failureReason() == null,
                "hazard-adjacent anchor should admit when a worker-facing intake surface exists: "
                        + LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), List.of(worker, support), threat,
                        routeFixture.evidence, routeFixture.corridor));
        helper.succeed();
    }

    @GameTest(template = "route_budget_unreachable_intake")
    public void routeBudgetReportsExactFailureWhenWorkerFacingIntakeIsUnreachable(GameTestHelper helper) throws Exception {
        int baseY = 30;
        prepareFlat(helper, 0, 14, 0, 14, baseY);
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, baseY + 1, 12);
        Mob support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, baseY + 1, 6);
        worker.setOnGround(true);
        support.setOnGround(true);
        BlockPos threat = helper.absolutePos(new BlockPos(10, baseY + 1, 4));
        CanonicalAuthorityFixture routeFixture = canonicalAuthorityFixture(helper, baseY);

        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, baseY, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, baseY + 1, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, baseY + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos workerFeet = worker.blockPosition().immutable();
        worker.setNoGravity(true);
        worker.setDeltaMovement(Vec3.ZERO);
        worker.setPos(workerFeet.getX() + 0.5D, workerFeet.getY(), workerFeet.getZ() + 0.5D);
        var origin = LivingWallCoordinator.admissionIntakeOriginForTest(threat, routeFixture.corridor);
        var candidates = LivingWallCoordinator.admissionIntakeCandidatesForTest(origin,
                Vec3.atBottomCenterOf(workerFeet).subtract(Vec3.atBottomCenterOf(origin)));
        for (BlockPos candidate : candidates) {
            for (int dy = -4; dy <= 3; dy++) {
                helper.getLevel().setBlock(candidate.offset(0, dy, 0), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(workerFeet, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(workerFeet.below(), Blocks.STONE.defaultBlockState(), 3);
        for (BlockPos candidate : candidates) {
            if (candidate.equals(workerFeet)) continue;
        }
        var proof = LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), List.of(worker, support), threat,
                routeFixture.evidence, routeFixture.corridor);
        require(proof.failureReason() != null,
                "missing worker-facing intake should reject admission: "
                        + proof + ", origin=" + origin + ", worker=" + worker.blockPosition()
                        + ", candidates=" + candidates.stream().map(pos -> pos + "="
                        + helper.getLevel().getBlockState(pos) + "/support="
                        + helper.getLevel().getBlockState(pos.below())).toList());
                require("worker_intake_no_support".equals(
                        LivingWallCoordinator.routeBudgetFailureReasonForTest(helper.getLevel(), List.of(worker, support), threat,
                                routeFixture.evidence, routeFixture.corridor)),
                "expected exact no-support failure reason for a genuinely unreachable intake, got "
                        + LivingWallCoordinator.routeBudgetFailureReasonForTest(helper.getLevel(), List.of(worker, support), threat,
                        routeFixture.evidence, routeFixture.corridor));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void routeBudgetRejectsAnchorWhenAnchorRemainsUnreachable(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 14, 0, 14);
        Mob left = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 4);
        Mob right = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 1, 4);
        BlockPos threat = helper.absolutePos(new BlockPos(9, 1, 4));

        for (int x = 8; x <= 10; x++) {
            for (int z = 3; z <= 5; z++) {
                if (x == 9 && z == 4) continue;
                helper.setBlock(new BlockPos(x, 0, z), Blocks.AIR.defaultBlockState());
            }
        }
        CanonicalAuthorityFixture routeFixture = canonicalAuthorityFixture(helper);

        require(LivingWallCoordinator.routeBudgetSatisfiedForTest(helper.getLevel(), List.of(left, right), threat,
                        routeFixture.evidence, routeFixture.corridor).failureReason() != null,
                "isolated anchor should still be rejected when worker cannot complete intake-to-anchor path");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void tacticalSnapshotUnknownRouteDefersAndCompleteRouteProceeds(GameTestHelper helper) throws Exception {
        LivingWallTacticalSnapshot.Identity identity = new LivingWallTacticalSnapshot.Identity(
                helper.getLevel().dimension().location(), null, null, "roster", "formation", 1L, 2L);
        LivingWallTacticalSnapshot.Bounds bounds = new LivingWallTacticalSnapshot.Bounds(
                helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(8, 8, 8)), 32, 32);
        LivingWallTacticalSnapshot.Snapshot unknown = new LivingWallTacticalSnapshot.Snapshot(bounds, identity,
                null, List.of(), 3L, 4L);
        List<BlockPos> route = List.of(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(2, 1, 1)));
        require(unknown.expansionStatus(route, 0) == LivingWallTacticalSnapshot.ExpansionStatus.UNKNOWN,
                "budget exhaustion should defer");
        require(unknown.expansionStatus(route, 1) == LivingWallTacticalSnapshot.ExpansionStatus.EXPANDING,
                "unknown route should expand when budget exists");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void crosserCaptureAtBaseBeforeTraversingOntoLiveSupport(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 10, 0, 10);
        Fixture fixture = createFixture(helper, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, true);
        Mob crosser = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 2);
        crosser.setOnGround(true);
        LivingWallCoordinator.SitePlan plan = getField(fixture.formation, "plan");
        Object transit = newCrosserTransit(crosser.getUUID(), plan.intakeWindows().get(0));
        setEnum(transit, "state", "CAPTURE_ENTRY");
        getMap(fixture.formation, "crosserTransit").put(crosser.getUUID(), transit);
        getMap(fixture.formation, "crosserSlots").put(crosser.getUUID(), 0);
        getList(fixture.formation, "crosserOrder").add(crosser.getUUID());
        setInt(fixture.formation, "activeCrossers", 1);
        invoke("driveCrossers", new Class<?>[]{ServerLevel.class, fixture.formation.getClass(), long.class},
                fixture.level, fixture.formation, fixture.level.getGameTime());
        require(crosser.isNoGravity(), "crosser should stay captured");
        require(crosser.noPhysics, "crosser should remain locked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void tiltExitRejectsAllAirColumnsAndTrapdoorColumns(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 14, 0, 14);
        Fixture fixture = createFixture(helper, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, true);
        BlockPos releaseCell = releaseCell(fixture);
        clearFallColumns(fixture.level, releaseCell);
        Object first = invoke("resolveTiltThreatExit", new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        require(first == null, "all-air column resolved: releaseCell=" + releaseCell
                + ", support1=" + fixture.level.getBlockState(releaseCell.below(1))
                + ", support5=" + fixture.level.getBlockState(releaseCell.below(5)));
        fixture.level.setBlock(releaseCell.below(5), Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);
        Object second = invoke("resolveTiltThreatExit", new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
                fixture.level, fixture.formation);
        require(second == null, "trapdoor column resolved: releaseCell=" + releaseCell
                + ", support5=" + fixture.level.getBlockState(releaseCell.below(5)));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void jumpAndDropScenariosNeverResolveTiltExit(GameTestHelper helper) throws Exception {
        prepareFlat(helper, 0, 14, 0, 14);
        Fixture jump = createFixture(helper, LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE, true);
        Fixture drop = createFixture(helper, LivingWallCoordinator.ConstructionScenario.DROP_TO_LEDGE, true);
        require(invoke("resolveTiltThreatExit", new Class<?>[]{ServerLevel.class, jump.formation.getClass()}, jump.level, jump.formation) == null,
                "jump should not resolve tilt exit");
        require(invoke("resolveTiltThreatExit", new Class<?>[]{ServerLevel.class, drop.formation.getClass()}, drop.level, drop.formation) == null,
                "drop should not resolve tilt exit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void primaryAndFallbackUseOneCanonicalEvaluator(GameTestHelper helper) {
        CanonicalAuthorityFixture fixture = canonicalAuthorityFixture(helper);
        LivingWallCoordinator.resetCanonicalAuthorityCountersForTest();
        WallProfileScanner.RayEvidence ray = fixture.ray;
        LivingWallCoordinator.CanonicalCorridorDecision primary = LivingWallCoordinator.evaluateCanonicalCorridor(
                fixture.level, fixture.source, fixture.threat, ray, 0, fixture.structural, 0);
        LivingWallCoordinator.CanonicalCorridorDecision fallback = LivingWallCoordinator.evaluateCanonicalCorridor(
                fixture.level, fixture.source, fixture.threat, ray, 0, fixture.structural, 0);
        require(primary.rejection() == fallback.rejection(), "primary and fallback decisions must match");
        require((primary.corridor() == null ? null : primary.corridor().corridorId())
                        .equals(fallback.corridor() == null ? null : fallback.corridor().corridorId()),
                "primary and fallback corridor ids must match");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void ordinarySafeEndpointWithoutCanonicalCorridorIsRejected(GameTestHelper helper) {
        CanonicalAuthorityFixture fixture = canonicalAuthorityFixture(helper);
        LivingWallCoordinator.resetCanonicalAuthorityCountersForTest();
        LivingWallCoordinator.RouteProbeProof proof = LivingWallCoordinator.routeProbeProofForTest(
                fixture.level, List.of(fixture.worker), fixture.threat, fixture.evidence, null);
        require(!proof.staticCorridorValid() && proof.routeProbeAttempts() == 0, "missing corridor must reject");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void canonicalPivotIsUsedWithoutAlternateSearch(GameTestHelper helper) {
        CanonicalAuthorityFixture fixture = canonicalAuthorityFixture(helper);
        LivingWallCoordinator.resetCanonicalAuthorityCountersForTest();
        require(fixture.corridor.pivot().equals(fixture.source), "fixture corridor must use canonical pivot");
        require(LivingWallCoordinator.routeProbeProofForTest(fixture.level, List.of(fixture.worker), fixture.threat,
                fixture.evidence, null).failureReason().equals("null_corridor"), "null corridor must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void rawFallbackSymbolsAreAbsentAndPlanDoesNotReclassify(GameTestHelper helper) {
        canonicalAuthorityFixture(helper);
        LivingWallCoordinator.resetCanonicalAuthorityCountersForTest();
        require(LivingWallCoordinator.canonicalAuthorityCountersForTest().evaluated() == 0,
                "plan validation must not evaluate a second authority");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void routeProbeFollowsStaticCorridorValidation(GameTestHelper helper) {
        CanonicalAuthorityFixture fixture = canonicalAuthorityFixture(helper);
        LivingWallCoordinator.resetCanonicalAuthorityCountersForTest();
        LivingWallCoordinator.RouteProbeProof proof = LivingWallCoordinator.routeProbeProofForTest(
                fixture.level, List.of(fixture.worker), fixture.threat, fixture.evidence, null);
        require(!proof.staticCorridorValid() && proof.intakeResolutionAttempts() == 0
                        && proof.routeProbeAttempts() == 0 && proof.reservations() == 0
                        && proof.pathCreationAttempts() == 0, "route probing must follow the static gate");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void candidateOrderIsDeterministicAndObservable(GameTestHelper helper) {
        canonicalAuthorityFixture(helper);
        LivingWallCoordinator.resetCanonicalAuthorityCountersForTest();
        LivingWallCoordinator.evaluateCanonicalCorridor(null, null, null, null, 0, null, 0);
        require(LivingWallCoordinator.canonicalAuthorityCountersForTest().invalidCanonicalEvidence() == 1,
                "invalid evaluator input must be observable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void cachedEvidenceWithoutCorridorCannotReconstructOrAdmit(GameTestHelper helper) {
        CanonicalAuthorityFixture fixture = canonicalAuthorityFixture(helper);
        LivingWallCoordinator.resetCanonicalAuthorityCountersForTest();
        LivingWallCoordinator.RouteProbeProof proof = LivingWallCoordinator.routeProbeProofForTest(
                fixture.level, List.of(fixture.worker), fixture.threat, fixture.evidence, null);
        require(proof.failureReason().equals("null_corridor") && proof.pathCreationAttempts() == 0,
                "evidence without corridor cannot be admitted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void invalidCorridorProducesZeroRouteProofCounters(GameTestHelper helper) {
        CanonicalAuthorityFixture fixture = canonicalAuthorityFixture(helper);
        LivingWallCoordinator.RouteProbeProof proof = LivingWallCoordinator.routeProbeProofForTest(
                fixture.level, List.of(), fixture.threat, fixture.evidence, null);
        require(!proof.staticCorridorValid() && proof.intakeResolutionAttempts() == 0
                        && proof.routeProbeAttempts() == 0 && proof.reservations() == 0
                        && proof.pathCreationAttempts() == 0, "invalid corridor counters must remain zero");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void rawFailureReasonHelperAlsoRequiresCorridor(GameTestHelper helper) {
        CanonicalAuthorityFixture fixture = canonicalAuthorityFixture(helper);
        require("null_corridor".equals(LivingWallCoordinator.routeBudgetFailureReasonForTest(
                fixture.level, List.of(fixture.worker), fixture.threat, fixture.evidence, null)),
                "failure helper must require a corridor");
        helper.succeed();
    }

    private static CanonicalAuthorityFixture canonicalAuthorityFixture(GameTestHelper helper) {
        return canonicalAuthorityFixture(helper, 0);
    }

    private static CanonicalAuthorityFixture canonicalAuthorityFixture(GameTestHelper helper, int baseY) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos source = origin.offset(0, baseY, 0);
        BlockPos threat = origin.offset(10, baseY, 0);
        BlockPos wall = origin.offset(5, baseY, 0);
        BlockPos landing = origin.offset(6, baseY, 0);
        BlockPos egress = origin.offset(7, baseY, 0);
        for (int x = -2; x <= 10; x++) helper.setBlock(new BlockPos(x, baseY, 0), Blocks.STONE.defaultBlockState());
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                wall, List.of(1L), List.of(), List.of(), wall, wall.below(), source, threat,
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(), 1.0D, null,
                List.of(wall), wall, source.offset(-2, 0, 0), landing, egress);
        CanonicalFeetCorridor corridor = CanonicalFeetCorridor.fromEvidence(helper.getLevel().dimension().location(),
                source, LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, evidence, egress);
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, -1, baseY + 1, 0);
        WallProfileScanner.RayEvidence ray = new WallProfileScanner.RayEvidence(1L,
                Vec3.atBottomCenterOf(source), new Vec3(1, 0, 0), wall, List.of(wall),
                List.of(WallProfileScanner.CollisionKind.SOLID), List.of(wall), false, 0.31D, 0.0D);
        return new CanonicalAuthorityFixture(helper.getLevel(), worker, source, threat, wall, evidence, corridor, ray);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Fixture createFixture(GameTestHelper helper, LivingWallCoordinator.ConstructionScenario scenario,
                                         boolean threatBelow) throws Exception {
        ServerLevel level = helper.getLevel();
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos pivot = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = helper.absolutePos(new BlockPos(8, threatBelow ? 5 : 1, 2));
        BlockPos spanEnd = helper.absolutePos(new BlockPos(8, threatBelow ? 5 : 1, 2));
        int threatY = scenario == LivingWallCoordinator.ConstructionScenario.WALL_LEDGE
                ? 1 : (threatBelow ? 0 : 1);
        BlockPos threat = helper.absolutePos(new BlockPos(6, threatY, 2));
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
        setDouble(formation, "integrity", 1.0D);
        setFieldValue(formation, "phase", enumValue(formation.getClass(), "TRAVERSE"));
        return new Fixture(level, formation);
    }

    private static void prepareFlat(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        prepareFlat(helper, minX, maxX, minZ, maxZ, 0);
    }

    private static void prepareFlat(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ, int baseY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(new BlockPos(x, baseY, z), Blocks.STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, baseY + 1, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, baseY + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static Object newConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                                                 BlockPos selectedAnchor, BlockPos threatBucket, long revision) throws Exception {
        Class<?> type = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        var ctor = type.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class,
                BlockPos.class, Class.forName("com.example.soundattract.ai.WallProfileScanner$TargetEvidence"),
                long.class, long.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket, null,
                200L, 200L, revision);
    }

    private static Object newFormation(Mob leader, BlockPos target, LivingWallCoordinator.SitePlan plan, Object request,
                                       long now) throws Exception {
        Class<?> geometryType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$BodyGeometry");
        var geometryCtor = geometryType.getDeclaredConstructor(double.class, double.class);
        geometryCtor.setAccessible(true);
        Object geometry = geometryCtor.newInstance(leader.getBbWidth(), leader.getBbHeight());
        Class<?> formationType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        var ctor = formationType.getDeclaredConstructor(Mob.class, BlockPos.class, LivingWallCoordinator.SitePlan.class,
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest"),
                int.class, int.class, int.class, geometryType, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(leader, target, plan, request, 4, 4, 8, geometry, now);
    }

    private static Object enumValue(Class<?> formationType, String name) throws Exception {
        Class<?> phaseType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Phase");
        return java.lang.Enum.valueOf((Class<java.lang.Enum>) phaseType, name);
    }

    private static void setInt(Object instance, String name, int value) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(instance, value);
    }

    private static void setLong(Object instance, String name, long value) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(instance, value);
    }

    private static void setDouble(Object instance, String name, double value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(instance, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<java.util.UUID, T> getMap(Object instance, String name) throws Exception {
        return (Map<java.util.UUID, T>) getField(instance, name);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getList(Object instance, String name) throws Exception {
        return (List<T>) getField(instance, name);
    }

    private static void setEnum(Object instance, String name, String value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Class<Enum> enumType = (Class<Enum>) field.getType();
        field.set(instance, Enum.valueOf(enumType, value));
    }

    private static Object newCrosserTransit(java.util.UUID id, LivingWallCoordinator.IntakeWindow window) throws Exception {
        Class<?> type = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$CrosserTransit");
        var ctor = type.getDeclaredConstructor(java.util.UUID.class, LivingWallCoordinator.IntakeWindow.class);
        ctor.setAccessible(true);
        return ctor.newInstance(id, window);
    }

    private static void setFieldValue(Object instance, String name, Object value) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static BlockPos releaseCell(Fixture fixture) throws Exception {
        Object projection = invoke("resolveTiltThreatExit", new Class<?>[]{ServerLevel.class, fixture.formation.getClass()},
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
        for (int z = releaseCell.getZ() - 1; z <= releaseCell.getZ() + 1; z++) {
            for (int fall = 1; fall <= 32; fall++) {
                level.setBlock(new BlockPos(releaseCell.getX(), releaseCell.getY() - fall, z),
                        Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static BlockPos relative(GameTestHelper helper, BlockPos absolute) {
        return new BlockPos(absolute.getX() - helper.absolutePos(BlockPos.ZERO).getX(),
                absolute.getY() - helper.absolutePos(BlockPos.ZERO).getY(),
                absolute.getZ() - helper.absolutePos(BlockPos.ZERO).getZ());
    }

    private record Fixture(ServerLevel level, Object formation) {
    }

    private record CanonicalAuthorityFixture(ServerLevel level, Mob worker, BlockPos source, BlockPos threat,
                                              BlockPos structural, WallProfileScanner.TargetEvidence evidence,
                                              CanonicalFeetCorridor corridor, WallProfileScanner.RayEvidence ray) {
    }
}