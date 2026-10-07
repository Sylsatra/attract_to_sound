package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("soundattract")
public class LivingBridgeSafeBaseRouteGameTests {
    private record ReentryEvidence(boolean leftLevel, boolean loadedFromDisk, boolean heldBeforeTick,
                                   boolean rawPreserved, boolean archived, boolean savedAgreement) {
    }

    private static final Map<UUID, ReentryEvidence> REENTRY_EVIDENCE = new ConcurrentHashMap<>();
    private static final Map<UUID, CompoundTag> EXPECTED_CAPSULES = new ConcurrentHashMap<>();

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityLeaveLevelEvent.class,
                LivingBridgeSafeBaseRouteGameTests::captureEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class,
                LivingBridgeSafeBaseRouteGameTests::captureDiskEntityJoin);
    }

    private static void captureEntityLeave(EntityLeaveLevelEvent event) {
        UUID id = event.getEntity().getUUID();
        REENTRY_EVIDENCE.computeIfPresent(id, (ignored, evidence) ->
                new ReentryEvidence(true, evidence.loadedFromDisk(), evidence.heldBeforeTick(),
                        evidence.rawPreserved(), evidence.archived(), evidence.savedAgreement()));
    }

    private static void captureDiskEntityJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        CompoundTag raw = mob.getPersistentData().getCompound("livingBridgeControl");
        CompoundTag expected = EXPECTED_CAPSULES.get(mob.getUUID());
        boolean preserved = expected != null && expected.equals(raw);
        boolean archived = hasOpaqueV1Archive(level, raw);
        boolean savedAgreement = raw.getInt("schema") == 1 ? archived : hasExactSavedRoute(level, raw);
        REENTRY_EVIDENCE.computeIfPresent(mob.getUUID(), (ignored, evidence) ->
                new ReentryEvidence(evidence.leftLevel(), true,
                        mob.isNoGravity() && mob.noPhysics && mob.getNavigation().isDone()
                                && mob.getDeltaMovement().x == 0.0D && mob.getDeltaMovement().z == 0.0D,
                        preserved, archived, savedAgreement));
    }

    private static boolean hasExactSavedRoute(net.minecraft.server.level.ServerLevel level, CompoundTag capsule) {
        try {
            LivingBridgeRouteSnapshot snapshot = LivingBridgeRouteSnapshot.fromTag(
                    capsule.getCompound("snapshot"));
            LivingBridgeRouteSnapshot saved = LivingBridgeRouteData.get(level).snapshot(snapshot.reservationId());
            return snapshot.digest().equals(capsule.getString("snapshotDigest"))
                    && saved != null && saved.exactlyEquals(snapshot);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static boolean hasOpaqueV1Archive(net.minecraft.server.level.ServerLevel level, CompoundTag raw) {
        net.minecraft.nbt.ListTag ledger = LivingBridgeRouteData.get(level).save(new CompoundTag())
                .getList("quarantineLedger", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < ledger.size(); index++) {
            CompoundTag entry = ledger.getCompound(index);
            if (entry.getString("classification").equals("OPAQUE_V1_ENTITY")
                    && entry.getCompound("firstRaw").equals(raw)) return true;
        }
        return false;
    }

    @GameTest(template = "empty")
    public void registeredNavigationGuardRunsExactlyOnce(GameTestHelper helper) {
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        worker.getPersistentData().putBoolean("livingBridgeSafeBaseRouteActive", true);
        LivingBridgeNavigationGuardProbe.reset();

        worker.getNavigation().tick();

        int invocations = LivingBridgeNavigationGuardProbe.invocationCount();
        worker.getPersistentData().remove("livingBridgeSafeBaseRouteActive");
        if (invocations != 1) {
            helper.fail("expected exactly one registered safe-base navigation guard, observed " + invocations);
            return;
        }
        SoundAttractMod.LOGGER.info("LIVING_BRIDGE_SAFE_BASE_ROUTE_R2_PASS");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void protectedNavigationWithoutFormationCannotFallThroughToVanilla(GameTestHelper helper) {
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        BlockPos next = helper.absolutePos(new BlockPos(8, 2, 2));
        Path path = new Path(List.of(new Node(next.getX(), next.getY(), next.getZ())), next, true);
        if (!worker.getNavigation().moveTo(path, 1.0D)) {
            helper.fail("could not install the guarded ordinary path");
            return;
        }
        worker.getPersistentData().putBoolean("livingBridgeSafeBaseRouteActive", true);
        worker.setDeltaMovement(0.1D, 0.0D, 0.0D);
        LivingBridgeNavigationGuardProbe.reset();
        Vec3 before = worker.position();

        worker.getNavigation().tick();

        worker.getPersistentData().remove("livingBridgeSafeBaseRouteActive");
        if (!worker.position().equals(before) || !worker.getNavigation().isDone()
                || worker.getDeltaMovement().x != 0.0D || worker.getDeltaMovement().z != 0.0D
                || LivingBridgeNavigationGuardProbe.invocationCount() != 1) {
            helper.fail("active route without trusted formation fell through: pos=" + worker.position()
                    + " before=" + before + " pathStopped=" + worker.getNavigation().isDone()
                    + " velocity=" + worker.getDeltaMovement()
                    + " guardCount=" + LivingBridgeNavigationGuardProbe.invocationCount());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void sweptBodyReportsCollisionLavaTrapdoorAndUnloadedCategories(GameTestHelper helper) throws Exception {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        BlockPos cell = helper.absolutePos(new BlockPos(2, 2, 2));
        Method sweep = LivingWallCoordinator.class.getDeclaredMethod("structuralSweepResult",
                net.minecraft.server.level.ServerLevel.class, Mob.class, AABB.class);
        sweep.setAccessible(true);

        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE.defaultBlockState());
        String collision = sweepCategory(sweep.invoke(null, level, worker, worker.getBoundingBox()));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.LAVA.defaultBlockState());
        String lava = sweepCategory(sweep.invoke(null, level, worker, worker.getBoundingBox()));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());

        BlockPos dropTrapdoorLocal = new BlockPos(2, 16, 2);
        BlockPos dropTrapdoor = helper.absolutePos(dropTrapdoorLocal);
        for (int y = 0; y < dropTrapdoorLocal.getY(); y++) {
            helper.setBlock(new BlockPos(2, y, 2), Blocks.AIR.defaultBlockState());
        }
        helper.setBlock(dropTrapdoorLocal, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, net.minecraft.core.Direction.EAST));
        AABB besideTrapdoorPanel = new AABB(dropTrapdoor.getX() + 0.4D, dropTrapdoor.getY() + 0.1D,
                dropTrapdoor.getZ() + 0.4D, dropTrapdoor.getX() + 0.6D, dropTrapdoor.getY() + 0.4D,
                dropTrapdoor.getZ() + 0.6D);
        AABB underTrapdoorPanel = new AABB(dropTrapdoor.getX() + 0.4D, dropTrapdoor.getY() - 0.5D,
                dropTrapdoor.getZ() + 0.4D, dropTrapdoor.getX() + 0.6D, dropTrapdoor.getY() + 0.05D,
                dropTrapdoor.getZ() + 0.6D);
        String trapdoorBeside = sweepCategory(sweep.invoke(null, level, worker, besideTrapdoorPanel));
        String trapdoorUnder = sweepCategory(sweep.invoke(null, level, worker, underTrapdoorPanel));
        helper.setBlock(dropTrapdoorLocal, Blocks.AIR.defaultBlockState());

        int unloadedX = cell.getX() + 4096;
        AABB unloadedBody = new AABB(unloadedX + 0.1D, 100.1D, cell.getZ() + 0.1D,
                unloadedX + 0.7D, 101.0D, cell.getZ() + 0.7D);
        String unloaded = sweepCategory(sweep.invoke(null, level, worker, unloadedBody));
        if (!"COLLISION".equals(collision) || !"LAVA".equals(lava)
                || !"TRAPDOOR_DROP".equals(trapdoorBeside) || !"TRAPDOOR_DROP".equals(trapdoorUnder)
                || !"UNLOADED_CELL".equals(unloaded)) {
            helper.fail("sweep rejection categories changed collision=" + collision + " lava=" + lava
                    + " trapdoorBeside=" + trapdoorBeside + " trapdoorUnder=" + trapdoorUnder
                    + " unloaded=" + unloaded);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void safeBaseFootingRequiresOnlyDirectSupportAndRejectsUnsafeCells(GameTestHelper helper) throws Exception {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2));
        Method footing = LivingWallCoordinator.class.getDeclaredMethod("safeBaseFootingResult",
                net.minecraft.server.level.ServerLevel.class, BlockPos.class);
        footing.setAccessible(true);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(2 + dx, 1, 2 + dz), Blocks.AIR.defaultBlockState());
            }
        }
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR.defaultBlockState());
        String supportedWithLateralAir = footingCategory(footing.invoke(null, level, feet));

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR.defaultBlockState());
        String missingSupport = footingCategory(footing.invoke(null, level, feet));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER.defaultBlockState());
        String fluidSupport = footingCategory(footing.invoke(null, level, feet));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_TRAPDOOR.defaultBlockState());
        String trapdoorSupport = footingCategory(footing.invoke(null, level, feet));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_TRAPDOOR.defaultBlockState());
        String trapdoorFeet = footingCategory(footing.invoke(null, level, feet));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER.defaultBlockState());
        String fluidFeet = footingCategory(footing.invoke(null, level, feet));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.WATER.defaultBlockState());
        String fluidHeadroom = footingCategory(footing.invoke(null, level, feet));

        if (!"NONE".equals(supportedWithLateralAir) || !"UNSUPPORTED".equals(missingSupport)
                || !"UNSUPPORTED".equals(fluidSupport) || !"UNSUPPORTED".equals(trapdoorSupport)
                || !"UNSUPPORTED".equals(trapdoorFeet) || !"UNSUPPORTED".equals(fluidFeet)
                || !"UNSUPPORTED".equals(fluidHeadroom)) {
            helper.fail("footing categories changed lateralAir=" + supportedWithLateralAir
                    + " missingSupport=" + missingSupport + " fluidSupport=" + fluidSupport
                    + " trapdoorSupport=" + trapdoorSupport + " trapdoorFeet=" + trapdoorFeet
                    + " fluidFeet=" + fluidFeet + " fluidHeadroom=" + fluidHeadroom);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void authorizedStructuralBuilderMayCrossPlannedEnvelopeButNotRealObstruction(GameTestHelper helper)
            throws Exception {
        Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 2);
        BlockPos pivot = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos spanEnd = helper.absolutePos(new BlockPos(8, 2, 2));
        BlockPos staging = helper.absolutePos(new BlockPos(10, 2, 6));
        BlockPos crossing = helper.absolutePos(new BlockPos(5, 2, 2));
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, pivot, spanEnd, spanEnd,
                new Vec3(1.0D, 0.0D, 0.0D), List.of(pivot), List.of(), List.of(pivot), Set.of(pivot), List.of());
        Class<?> requestType = Class.forName(
                "com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestConstructor = requestType.getDeclaredConstructor(Vec3.class, BlockPos.class,
                BlockPos.class, BlockPos.class, BlockPos.class, WallProfileScanner.TargetEvidence.class,
                long.class, long.class, long.class);
        requestConstructor.setAccessible(true);
        Object request = requestConstructor.newInstance(Vec3.atBottomCenterOf(pivot.west()), pivot.west(),
                spanEnd, spanEnd, spanEnd, null, 200L, 200L, 7L);
        Class<?> geometryType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$BodyGeometry");
        Constructor<?> geometryConstructor = geometryType.getDeclaredConstructor(double.class, double.class);
        geometryConstructor.setAccessible(true);
        Object geometry = geometryConstructor.newInstance(worker.getBbWidth(), worker.getBbHeight());
        Class<?> formationType = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        Constructor<?> formationConstructor = formationType.getDeclaredConstructor(Mob.class, BlockPos.class,
                LivingWallCoordinator.SitePlan.class, requestType, int.class, int.class, int.class,
                geometryType, long.class);
        formationConstructor.setAccessible(true);
        Object formation = formationConstructor.newInstance(worker, spanEnd, plan, request, 4, 4, 1,
                geometry, helper.getLevel().getGameTime());
        setField(formation, "phase", phase("ASSEMBLE"));
        setField(formation, "activeStructuralSlot", 0);
        @SuppressWarnings("unchecked")
        List<UUID> members = (List<UUID>) getField(formation, "members");
        members.add(worker.getUUID());
        @SuppressWarnings("unchecked")
        Set<UUID> builders = (Set<UUID>) getField(formation, "structuralBuilderIds");
        builders.add(worker.getUUID());
        Method stateMethod = LivingWallCoordinator.class.getDeclaredMethod("state",
                net.minecraft.server.level.ServerLevel.class);
        stateMethod.setAccessible(true);
        Object levelState = stateMethod.invoke(null, helper.getLevel());
        @SuppressWarnings("unchecked")
        Map<UUID, UUID> owners = (Map<UUID, UUID>) getField(levelState, "mobOwners");
        UUID formationId = (UUID) getField(formation, "id");
        owners.put(worker.getUUID(), formationId);
        worker.setPos(Vec3.atBottomCenterOf(crossing));
        for (int x = 1; x <= 12; x++) {
            for (int z = 0; z <= 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 1; x <= 12; x++) {
            for (int z = 0; z <= 8; z++) {
                for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos stagingNode = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos middleNode = helper.absolutePos(new BlockPos(3, 2, 2));
        Path path = new Path(new ArrayList<>(List.of(new Node(crossing.getX(), crossing.getY(), crossing.getZ()),
                new Node(middleNode.getX(), middleNode.getY(), middleNode.getZ()),
                new Node(stagingNode.getX(), stagingNode.getY(), stagingNode.getZ()))), stagingNode, true);
        Method safeValidation = LivingWallCoordinator.class.getDeclaredMethod("safeBasePathValidation",
                net.minecraft.server.level.ServerLevel.class, Mob.class, formationType,
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SafeBaseNavigationReservation"),
                Path.class);
        safeValidation.setAccessible(true);
        String allowed = validateBuilderCrossing(helper, worker, formation, request, formationId,
                crossing, stagingNode, path, safeValidation);
        helper.getLevel().setBlock(crossing, Blocks.STONE.defaultBlockState(), 3);
        if (!helper.getLevel().getBlockState(crossing).is(Blocks.STONE)) {
            helper.fail("solid obstruction was not placed at absolute crossing cell " + crossing);
            return;
        }
        String blocked = validateBuilderCrossing(helper, worker, formation, request, formationId,
                crossing, stagingNode, path, safeValidation);
        helper.getLevel().setBlock(crossing, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(crossing.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(crossing, Blocks.LAVA.defaultBlockState(), 3);
        if (!helper.getLevel().getBlockState(crossing).is(Blocks.LAVA)) {
            helper.fail("lava obstruction was not placed at absolute crossing cell " + crossing);
            return;
        }
        String hazardous = validateBuilderCrossing(helper, worker, formation, request, formationId,
                crossing, stagingNode, path, safeValidation);
        setField(formation, "phase", phase("SETTLE"));
        String stalePhase = validateBuilderCrossing(helper, worker, formation, request, formationId,
                crossing, stagingNode, path, safeValidation);

        if (!"NONE".equals(allowed) || "NONE".equals(blocked) || "NONE".equals(hazardous)
                || "NONE".equals(stalePhase)) {
            helper.fail("production route validation allowed=" + allowed + " solidBlock=" + blocked
                    + " lavaSupport=" + hazardous + " stalePhase=" + stalePhase);
            return;
        }
        helper.succeed();
    }

    private static String validateBuilderCrossing(GameTestHelper helper, Mob worker, Object formation,
                                                  Object request, UUID formationId, BlockPos crossing,
                                                  BlockPos staging, Path path, Method validation) throws Exception {
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        List<BlockPos> nodes = new ArrayList<>(path.getNodeCount());
        for (int index = 0; index < path.getNodeCount(); index++) {
            nodes.add(path.getNode(index).asBlockPos());
        }
        Constructor<?> constructor = Class.forName(
                        "com.example.soundattract.ai.LivingWallCoordinator$SafeBaseNavigationReservation")
                .getDeclaredConstructor(UUID.class, UUID.class, int.class, int.class,
                        phase("ASSEMBLE").getClass(), long.class, Vec3.class, Vec3.class,
                        Vec3.class, BlockPos.class, BlockPos.class, List.class, Path.class,
                        net.minecraft.resources.ResourceLocation.class, Vec3.class, AABB.class,
                        net.minecraft.world.entity.Pose.class, String.class);
        constructor.setAccessible(true);
        Object reservation = constructor.newInstance(formationId, worker.getUUID(), 0, 0,
                phase("ASSEMBLE"), 7L,
                Vec3.atBottomCenterOf((BlockPos) getField(formation, "pivot")), forward,
                (Vec3) getField(request, "sourceCenter"), (BlockPos) getField(request, "sourceBucket"),
                staging, nodes, path, helper.getLevel().dimension().location(), worker.position(),
                worker.getBoundingBox(), worker.getPose(), "test-certificate");
        return ((LivingBridgeSafeBaseRoute.Result) validation.invoke(null, helper.getLevel(), worker,
                formation, reservation, path)).rejection().name();
    }

    @SuppressWarnings("unchecked")
    private static Object phase(String name) {
        try {
            Class<? extends Enum> phaseType = (Class<? extends Enum>) Class.forName(
                    "com.example.soundattract.ai.LivingWallCoordinator$Phase");
            return Enum.valueOf(phaseType, name);
        } catch (ClassNotFoundException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @GameTest(template = "empty")
    public void finiteBridgeEnvelopeStillSupportsSafeGenericWrap(GameTestHelper helper) {
        BlockPos pivot = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos spanEnd = helper.absolutePos(new BlockPos(6, 2, 2));
        BlockPos staging = helper.absolutePos(new BlockPos(10, 2, 6));
        List<BlockPos> wrapped = List.of(helper.absolutePos(new BlockPos(9, 2, 6)), staging);
        AABB throughFootprint = new AABB(pivot.getX() + 2.0D, pivot.getY(), pivot.getZ() + 0.1D,
                pivot.getX() + 2.8D, pivot.getY() + 1.8D, pivot.getZ() + 0.9D);

        if (LivingBridgeSafeBaseRoute.bodyOutsideFiniteEnvelope(throughFootprint, pivot, spanEnd,
                0.8D, pivot.getY(), pivot.getY() + 2.0D)) {
            helper.fail("body intersecting the finite bridge prism was accepted");
            return;
        }
        LivingBridgeSafeBaseRoute.Result wrap = LivingBridgeSafeBaseRoute.validateFinitePathShape(
                wrapped, staging, true, staging, pivot, spanEnd, 0.8D, pivot.getY(), pivot.getY() + 2.0D);
        if (!wrap.safe()) {
            helper.fail("safe route around the finite bridge end was rejected: " + wrap);
            return;
        }
        helper.succeed();
    }

    private static String sweepCategory(Object result) throws Exception {
        return ((Enum<?>) result.getClass().getDeclaredMethod("rejection").invoke(result)).name();
    }

    private static String footingCategory(Object result) throws Exception {
        return ((Enum<?>) result.getClass().getDeclaredMethod("rejection").invoke(result)).name();
    }

    @GameTest(template = "empty")
    public void reassignmentAndFormationCleanupRetainDurableLaneOwner(GameTestHelper helper) throws Exception {
        Method factory = LivingWallCoordinatorGameTests.class.getDeclaredMethod(
                "newFixture", GameTestHelper.class, String.class, boolean.class);
        factory.setAccessible(true);
        Object fixture = factory.invoke(null, helper, "durable-lane-cleanup", false);
        ServerLevelView view = fixtureView(fixture);
        Mob worker = view.worker;
        Object formation = view.formation;
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        UUID formationId = (UUID) getField(formation, "id");
        UUID reservationId = UUID.randomUUID();
        LivingBridgeRouteSnapshot active = new LivingBridgeRouteSnapshot(reservationId, worker.getUUID(),
                formationId, level.dimension().location().toString(), 0L, UUID.randomUUID(), 1L,
                "CONTROLLED_BASE_APPROACH", "CONTROLLED_APPROACH", false, false, true,
                List.of(LivingBridgeRouteSnapshot.Point.from(worker.getX(), worker.getY(), worker.getZ())),
                3072L, List.of(), 0, null, worker.chunkPosition().x, worker.chunkPosition().z);
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        data.putSnapshot(active);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", LivingBridgeRouteData.CURRENT_SCHEMA);
        capsule.put("snapshot", active.toTag());
        capsule.putString("snapshotDigest", active.digest());
        worker.getPersistentData().put("livingBridgeControl", capsule.copy());
        worker.setNoGravity(false);
        worker.noPhysics = false;

        @SuppressWarnings("unchecked")
        Map<UUID, Object> structuralLanes = (Map<UUID, Object>) getField(formation,
                "structuralApproachReservations");
        Object lane = newStructuralLane(formationId, worker);
        structuralLanes.put(worker.getUUID(), lane);
        Method releaseMember = LivingWallCoordinator.class.getDeclaredMethod("releaseMember",
                net.minecraft.server.level.ServerLevel.class, formation.getClass(), UUID.class);
        releaseMember.setAccessible(true);
        releaseMember.invoke(null, level, formation, worker.getUUID());

        if (!worker.isNoGravity() || !worker.noPhysics || !capsule.equals(
                worker.getPersistentData().getCompound("livingBridgeControl"))
                || structuralLanes.containsKey(worker.getUUID())
                || data.snapshot(reservationId) == null || !data.snapshot(reservationId).exactlyEquals(active)) {
            helper.fail("member reassignment released an active durable route owner or local lane unexpectedly");
            return;
        }

        @SuppressWarnings("unchecked")
        List<UUID> members = (List<UUID>) getField(formation, "members");
        members.add(worker.getUUID());
        structuralLanes.put(worker.getUUID(), newStructuralLane(formationId, worker));
        Method releaseFormation = LivingWallCoordinator.class.getDeclaredMethod("releaseFormation",
                net.minecraft.server.level.ServerLevel.class, formation.getClass());
        releaseFormation.setAccessible(true);
        releaseFormation.invoke(null, level, formation);

        if (!worker.isNoGravity() || !worker.noPhysics || !structuralLanes.isEmpty()
                || !((Map<?, ?>) getField(formation, "safeBaseNavigation")).isEmpty()
                || !capsule.equals(worker.getPersistentData().getCompound("livingBridgeControl"))
                || !data.snapshot(reservationId).exactlyEquals(active)) {
            helper.fail("formation cleanup did not preserve durable control while retiring local lanes");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void timeoutAndCancellationKeepUnrecoveredRouteOwnersHeld(GameTestHelper helper) throws Exception {
        Method factory = LivingWallCoordinatorGameTests.class.getDeclaredMethod(
                "newFixture", GameTestHelper.class, String.class, boolean.class);
        factory.setAccessible(true);
        Object fixture = factory.invoke(null, helper, "durable-lane-timeout-cancel", false);
        ServerLevelView view = fixtureView(fixture);
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        UUID formationId = (UUID) getField(view.formation, "id");
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        @SuppressWarnings("unchecked")
        List<UUID> members = (List<UUID>) getField(view.formation, "members");
        @SuppressWarnings("unchecked")
        Map<UUID, Object> structuralLanes = (Map<UUID, Object>) getField(view.formation,
                "structuralApproachReservations");
        members.clear();
        members.add(view.worker.getUUID());

        UUID timeoutReservation = UUID.randomUUID();
        LivingBridgeRouteSnapshot timeoutOwner = activeRouteSnapshot(view.worker, level, formationId,
                timeoutReservation);
        installActiveRoute(data, view.worker, timeoutOwner);
        structuralLanes.put(view.worker.getUUID(), newStructuralLane(formationId, view.worker));
        Field retryLimit = LivingWallCoordinator.class.getDeclaredField("RECRUITER_RETRY_WINDOWS");
        retryLimit.setAccessible(true);
        setField(view.formation, "activeStructuralRouteRetries", retryLimit.getInt(null));
        Method reroute = LivingWallCoordinator.class.getDeclaredMethod("rerouteOrReleaseStructuralMember",
                net.minecraft.server.level.ServerLevel.class, view.formation.getClass(), Mob.class,
                int.class, String.class);
        reroute.setAccessible(true);
        reroute.invoke(null, level, view.formation, view.worker, 0, "timeout");
        if (!view.worker.isNoGravity() || !view.worker.noPhysics
                || !timeoutOwner.exactlyEquals(data.snapshot(timeoutReservation))) {
            helper.fail("retry-timeout released an unrecovered durable owner");
            return;
        }

        Mob cancelled = helper.spawn(EntityType.ZOMBIE, 5.5F, 2.0F, 2.5F);
        UUID cancelReservation = UUID.randomUUID();
        LivingBridgeRouteSnapshot cancelOwner = activeRouteSnapshot(cancelled, level, formationId,
                cancelReservation);
        installActiveRoute(data, cancelled, cancelOwner);
        members.add(cancelled.getUUID());
        structuralLanes.put(cancelled.getUUID(), newStructuralLane(formationId, cancelled));
        Method releaseFormation = LivingWallCoordinator.class.getDeclaredMethod("releaseFormation",
                net.minecraft.server.level.ServerLevel.class, view.formation.getClass());
        releaseFormation.setAccessible(true);
        releaseFormation.invoke(null, level, view.formation);
        if (!cancelled.isNoGravity() || !cancelled.noPhysics
                || !cancelOwner.exactlyEquals(data.snapshot(cancelReservation))
                || !cancelOwner.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(cancelled.getPersistentData()
                .getCompound("livingBridgeControl").getCompound("snapshot")))) {
            helper.fail("formation cancellation released an unrecovered durable owner");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void verifiedDeathRetiresExactV2RouteAndStructuralLane(GameTestHelper helper) throws Exception {
        Method factory = LivingWallCoordinatorGameTests.class.getDeclaredMethod(
                "newFixture", GameTestHelper.class, String.class, boolean.class);
        factory.setAccessible(true);
        Object fixture = factory.invoke(null, helper, "durable-lane-death", false);
        ServerLevelView view = fixtureView(fixture);
        Mob worker = view.worker;
        Object formation = view.formation;
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        UUID formationId = (UUID) getField(formation, "id");
        UUID reservationId = UUID.randomUUID();
        LivingBridgeRouteSnapshot active = new LivingBridgeRouteSnapshot(reservationId, worker.getUUID(),
                formationId, level.dimension().location().toString(), 0L, UUID.randomUUID(), 1L,
                "CONTROLLED_BASE_APPROACH", "CONTROLLED_APPROACH", false, false, true,
                List.of(LivingBridgeRouteSnapshot.Point.from(worker.getX(), worker.getY(), worker.getZ())),
                3072L, List.of(), 0, null, worker.chunkPosition().x, worker.chunkPosition().z);
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        data.putSnapshot(active);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", LivingBridgeRouteData.CURRENT_SCHEMA);
        capsule.put("snapshot", active.toTag());
        capsule.putString("snapshotDigest", active.digest());
        worker.getPersistentData().put("livingBridgeControl", capsule.copy());
        @SuppressWarnings("unchecked")
        Map<UUID, Object> structuralLanes = (Map<UUID, Object>) getField(formation,
                "structuralApproachReservations");
        structuralLanes.put(worker.getUUID(), newStructuralLane(formationId, worker));

        if (!worker.hurt(level.damageSources().generic(), Float.MAX_VALUE)) {
            helper.fail("lethal server-side damage did not trigger the death transition");
            return;
        }
        LivingBridgeRouteSnapshot dead = data.snapshot(reservationId);
        CompoundTag retiredCapsule = worker.getPersistentData().getCompound("livingBridgeControl");
        if (worker.isAlive() || dead == null || !"DEAD".equals(dead.lifecycleState())
                || !dead.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(retiredCapsule.getCompound("snapshot")))
                || !dead.digest().equals(retiredCapsule.getString("snapshotDigest"))
                || structuralLanes.containsKey(worker.getUUID())) {
            helper.fail("confirmed death failed to retire exact durable lane dead="
                    + (dead == null ? "missing" : dead.lifecycleState()) + " alive=" + worker.isAlive()
                    + " localLaneRetained=" + structuralLanes.containsKey(worker.getUUID()));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void discardRetiresExactRouteButChunkUnloadKeepsLane(GameTestHelper helper) throws Exception {
        Method factory = LivingWallCoordinatorGameTests.class.getDeclaredMethod(
                "newFixture", GameTestHelper.class, String.class, boolean.class);
        factory.setAccessible(true);
        Object fixture = factory.invoke(null, helper, "durable-lane-removal", false);
        ServerLevelView view = fixtureView(fixture);
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        UUID formationId = (UUID) getField(view.formation, "id");
        @SuppressWarnings("unchecked")
        Map<UUID, Object> structuralLanes = (Map<UUID, Object>) getField(view.formation,
                "structuralApproachReservations");
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);

        UUID unloadReservation = UUID.randomUUID();
        LivingBridgeRouteSnapshot unloadSnapshot = activeRouteSnapshot(view.worker, level, formationId,
                unloadReservation);
        installActiveRoute(data, view.worker, unloadSnapshot);
        structuralLanes.put(view.worker.getUUID(), newStructuralLane(formationId, view.worker));
        view.worker.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        if (!data.snapshot(unloadReservation).exactlyEquals(unloadSnapshot)
                || !structuralLanes.containsKey(view.worker.getUUID())) {
            helper.fail("chunk-unload removal reason incorrectly retired the durable lane");
            return;
        }

        Mob discarded = helper.spawn(EntityType.ZOMBIE, 5.5F, 2.0F, 2.5F);
        UUID discardReservation = UUID.randomUUID();
        LivingBridgeRouteSnapshot discardSnapshot = activeRouteSnapshot(discarded, level, formationId,
                discardReservation);
        installActiveRoute(data, discarded, discardSnapshot);
        structuralLanes.put(discarded.getUUID(), newStructuralLane(formationId, discarded));
        discarded.discard();
        LivingBridgeRouteSnapshot removed = data.snapshot(discardReservation);
        if (removed == null || !"REMOVED".equals(removed.lifecycleState())
                || !removed.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(discarded.getPersistentData()
                .getCompound("livingBridgeControl").getCompound("snapshot")))
                || structuralLanes.containsKey(discarded.getUUID())) {
            helper.fail("verified discard failed to commit matching REMOVED receipt and release local lane");
            return;
        }
        helper.succeed();
    }

    private static LivingBridgeRouteSnapshot activeRouteSnapshot(Mob mob,
            net.minecraft.server.level.ServerLevel level, UUID formationId, UUID reservationId) {
        return new LivingBridgeRouteSnapshot(reservationId, mob.getUUID(), formationId,
                level.dimension().location().toString(), 0L, UUID.randomUUID(), 1L,
                "CONTROLLED_BASE_APPROACH", "CONTROLLED_APPROACH", false, false, true,
                List.of(LivingBridgeRouteSnapshot.Point.from(mob.getX(), mob.getY(), mob.getZ())),
                3072L, List.of(), 0, null, mob.chunkPosition().x, mob.chunkPosition().z);
    }

    private static void installActiveRoute(LivingBridgeRouteData data, Mob mob,
                                           LivingBridgeRouteSnapshot snapshot) {
        data.putSnapshot(snapshot);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", LivingBridgeRouteData.CURRENT_SCHEMA);
        capsule.put("snapshot", snapshot.toTag());
        capsule.putString("snapshotDigest", snapshot.digest());
        mob.getPersistentData().put("livingBridgeControl", capsule);
    }

    @GameTest(template = "empty")
    public void boundedSafeRecoveryHoldsOnBlockedSegmentThenRetiresAtEndpoint(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos localStart = new BlockPos(2, 2, 2);
        BlockPos localEndpoint = new BlockPos(5, 2, 2);
        BlockPos start = helper.absolutePos(localStart);
        BlockPos endpoint = helper.absolutePos(localEndpoint);
        for (int x = 2; x <= 5; x++) {
            BlockPos cell = new BlockPos(x, 2, 2);
            helper.setBlock(cell.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(cell, Blocks.AIR.defaultBlockState());
            helper.setBlock(cell.above(), Blocks.AIR.defaultBlockState());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos support = localEndpoint.offset(dx, -1, dz);
                helper.setBlock(support, Blocks.STONE.defaultBlockState());
                helper.setBlock(support.above(), Blocks.AIR.defaultBlockState());
                helper.setBlock(support.above(2), Blocks.AIR.defaultBlockState());
            }
        }
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        worker.setPos(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        UUID reservationId = UUID.randomUUID();
        UUID formationId = UUID.randomUUID();
        LivingBridgeRouteSnapshot active = new LivingBridgeRouteSnapshot(reservationId, worker.getUUID(),
                formationId, level.dimension().location().toString(), 1L, UUID.randomUUID(), 1L,
                "CONTROLLED_BASE_APPROACH", "CONTROLLED_APPROACH", false, false, true,
                List.of(LivingBridgeRouteSnapshot.Point.from(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D),
                        LivingBridgeRouteSnapshot.Point.from(endpoint.getX() + 0.5D, endpoint.getY(), endpoint.getZ() + 0.5D)),
                3072L, List.of(), 0,
                LivingBridgeRouteSnapshot.Point.from(endpoint.getX() + 0.5D, endpoint.getY(), endpoint.getZ() + 0.5D),
                worker.chunkPosition().x, worker.chunkPosition().z);
        LivingBridgeRouteData data = LivingBridgeRouteData.load(new CompoundTag());
        data.putSnapshot(active);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", LivingBridgeRouteData.CURRENT_SCHEMA);
        capsule.put("snapshot", active.toTag());
        capsule.putString("snapshotDigest", active.digest());
        worker.getPersistentData().put("livingBridgeControl", capsule);
        worker.setNoGravity(true);
        worker.noPhysics = true;
        CompoundTag opaqueV1 = new CompoundTag();
        opaqueV1.putInt("schema", 1);
        opaqueV1.putString("opaqueEvidence", "retain-with-barrier");
        data.quarantine("OPAQUE_V1_ENTITY", opaqueV1, null, active.dimension());
        Mob newOwner = helper.spawn(EntityType.ZOMBIE, 7.5F, 2.0F, 2.5F);
        UUID newReservationId = UUID.randomUUID();
        LivingBridgeRouteSnapshot newClaim = new LivingBridgeRouteSnapshot(newReservationId, newOwner.getUUID(),
                UUID.randomUUID(), active.dimension(), 0L, UUID.randomUUID(), 1L,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(active.corridor().get(0)), 3072L, List.of(), 0, active.safeEndpoint(),
                newOwner.chunkPosition().x, newOwner.chunkPosition().z);
        if (!data.admissionBlocked(active.dimension()) || data.beginInitialClaim(newClaim, newOwner)
                || data.snapshot(newReservationId) != null || !data.canRecoverOwner(active)) {
            helper.fail("scoped quarantine barrier did not isolate exact-owner recovery from new admission");
            return;
        }

        BlockPos obstruction = new BlockPos(3, 2, 2);
        helper.setBlock(obstruction, Blocks.LAVA.defaultBlockState());
        boolean blockedAttemptObserved = false;
        boolean stationaryOnBlockedAttempt = false;
        for (int step = 0; step < 20; step++) {
            Vec3 beforeAttempt = worker.position();
            if (!invokeSafeRecovery(level, worker, data)) {
                blockedAttemptObserved = true;
                stationaryOnBlockedAttempt = worker.position().distanceToSqr(beforeAttempt) < 1.0E-8D;
                break;
            }
        }
        LivingBridgeRouteSnapshot blocked = data.snapshot(reservationId);
        boolean journaled = !data.save(new CompoundTag()).getList("transitionJournal", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty();
        boolean held = worker.isNoGravity() && worker.noPhysics;
        boolean shortOfEndpoint = worker.distanceToSqr(endpoint.getX() + 0.5D, endpoint.getY(), endpoint.getZ() + 0.5D)
                > 1.0E-8D;
        if (!blockedAttemptObserved || !stationaryOnBlockedAttempt || blocked == null
                || !"RECOVERING_TO_SAFE_ENDPOINT".equals(blocked.lifecycleState())
                || blocked.recoveryIndex() < 1 || !journaled || !held || !shortOfEndpoint) {
            helper.fail("blocked recovery evidence blockedAttempt=" + blockedAttemptObserved
                    + " stationaryOnBlockedAttempt=" + stationaryOnBlockedAttempt
                    + " laneState=" + (blocked == null ? "missing" : blocked.lifecycleState())
                    + " index=" + (blocked == null ? -1 : blocked.recoveryIndex())
                    + " journaled=" + journaled + " shortOfEndpoint=" + shortOfEndpoint
                    + " noGravity=" + worker.isNoGravity() + " noPhysics=" + worker.noPhysics
                    + " dimensionBarrier=" + data.admissionBlocked(active.dimension())
                    + " endpointLoaded=" + level.isLoaded(endpoint)
                    + " endpointSafeSite=" + invokeSafeSite(level, endpoint)
                    + " endpointSafeGround=" + com.example.soundattract.pathfinding.HazardClassifier
                    .isSafeGround(level, endpoint)
                    + " endpointStates=" + level.getBlockState(endpoint) + "/"
                    + level.getBlockState(endpoint.above()) + "/" + level.getBlockState(endpoint.below())
                    + " endpointCoordinates=" + endpoint + " localOrigin=" + helper.absolutePos(BlockPos.ZERO)
                    + " supportRing=" + java.util.stream.IntStream.rangeClosed(-1, 1).boxed()
                    .flatMap(dx -> java.util.stream.IntStream.rangeClosed(-1, 1)
                            .mapToObj(dz -> endpoint.offset(dx, -1, dz) + "="
                                    + level.getBlockState(endpoint.offset(dx, -1, dz))))
                    .toList()
                    + " endpointLoadedParts=" + level.isLoaded(endpoint.below()) + "/"
                    + level.isLoaded(endpoint.above())
                    + " endpointUnsafeColumn=" + invokeBoolean(LivingWallCoordinator.class,
                    "isUnsafeColumn", new Class<?>[]{net.minecraft.server.level.ServerLevel.class, BlockPos.class},
                    level, endpoint)
                    + " endpointStableSupport=" + invokeBoolean(LivingWallCoordinator.class,
                    "hasStableSupport", new Class<?>[]{net.minecraft.server.level.ServerLevel.class,
                    BlockPos.class, net.minecraft.world.level.block.state.BlockState.class},
                    level, endpoint.below(), level.getBlockState(endpoint.below()))
                    + " currentPosition=" + worker.position());
            return;
        }
        helper.setBlock(obstruction, Blocks.AIR.defaultBlockState());
        boolean recovered = false;
        for (int step = 0; step < 66 && !recovered; step++) {
            invokeSafeRecovery(level, worker, data);
            LivingBridgeRouteSnapshot current = data.snapshot(reservationId);
            recovered = current != null && LivingBridgeRouteData.isTerminalLaneState(current.lifecycleState())
                    && "SAFE_RECOVERY_COMPLETE".equals(current.lifecycleState());
        }
        LivingBridgeRouteSnapshot terminal = data.snapshot(reservationId);
        int recoveryIndex = terminal == null ? -1 : terminal.recoveryIndex();
        List<LivingBridgeRouteSnapshot.Point> recoveryRoute = terminal == null ? List.of() : terminal.recoveryRoute();
        Vec3 nextRecoveryPoint = recoveryIndex >= 0 && recoveryIndex < recoveryRoute.size()
                ? new Vec3(recoveryRoute.get(recoveryIndex).x() / 4096.0D,
                recoveryRoute.get(recoveryIndex).y() / 4096.0D,
                recoveryRoute.get(recoveryIndex).z() / 4096.0D) : worker.position();
        List<BlockPos> remainingLane = recoveryRoute.stream().skip(Math.max(0, recoveryIndex - 1L))
                .map(point -> BlockPos.containing(point.x() / 4096.0D,
                        point.y() / 4096.0D, point.z() / 4096.0D)).distinct().toList();
        boolean nextSegmentClear = invokeBoolean(LivingWallCoordinator.class, "isEntityClearAt",
                new Class<?>[]{net.minecraft.server.level.ServerLevel.class, Mob.class, Vec3.class, Vec3.class},
                level, worker, worker.position(), nextRecoveryPoint);
        boolean recoveryLaneConflict = terminal != null && data.hasConflictingRecoveryLane(terminal, remainingLane,
                terminal.halfWidth() * 2.0D / 4096.0D, 2);
        if (!recovered || terminal == null || !"SAFE_RECOVERY_COMPLETE".equals(terminal.lifecycleState())
                || !LivingBridgeRouteData.isTerminalLaneState(terminal.lifecycleState())
                || worker.isNoGravity() || worker.noPhysics
                || !data.admissionBlocked(active.dimension())
                || !terminal.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(worker.getPersistentData()
                .getCompound("livingBridgeControl").getCompound("snapshot")))) {
            helper.fail("safe endpoint recovery failed recovered=" + recovered
                    + " laneState=" + (terminal == null ? "missing" : terminal.lifecycleState())
                    + " recoveryIndex=" + (terminal == null ? -1 : terminal.recoveryIndex())
                    + " recoveryRouteSize=" + (terminal == null ? -1 : terminal.recoveryRoute().size())
                    + " nextRecoveryPoint=" + nextRecoveryPoint + " nextSegmentClear=" + nextSegmentClear
                    + " recoveryLaneConflict=" + recoveryLaneConflict
                    + " exactOwner=" + (terminal != null && data.canRecoverOwner(terminal))
                    + " position=" + worker.position() + " endpoint=" + endpoint
                    + " obstruction=" + level.getBlockState(helper.absolutePos(obstruction))
                    + " noGravity=" + worker.isNoGravity() + " noPhysics=" + worker.noPhysics);
            return;
        }
        helper.succeed();
    }

    private static boolean invokeSafeRecovery(net.minecraft.server.level.ServerLevel level, Mob worker,
                                              LivingBridgeRouteData data) {
        try {
            Method method = LivingWallCoordinator.class.getDeclaredMethod("advanceControlledSafeRecovery",
                    net.minecraft.server.level.ServerLevel.class, Mob.class, LivingBridgeRouteData.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, level, worker, data);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static boolean invokeSafeSite(net.minecraft.server.level.ServerLevel level, BlockPos position) {
        try {
            Method method = LivingWallCoordinator.class.getDeclaredMethod("isSafeSite",
                    net.minecraft.server.level.ServerLevel.class, BlockPos.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, level, position);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static boolean invokeBoolean(Class<?> owner, String name, Class<?>[] parameterTypes,
                                         Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Object newStructuralLane(UUID formationId, Mob mob) throws ReflectiveOperationException {
        Class<?> laneType = Class.forName(
                "com.example.soundattract.ai.LivingWallCoordinator$StructuralApproachReservation");
        Constructor<?> constructor = laneType.getDeclaredConstructor(UUID.class, UUID.class, long.class,
                List.class, double.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(formationId, mob.getUUID(), 1L,
                List.of(mob.blockPosition()), mob.getBbWidth(), mob.level().getGameTime());
    }

    @GameTest(template = "empty")
    public void staleRecoveryKeepsDurableRouteOwnerHeld(GameTestHelper helper) throws Exception {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        UUID reservationId = UUID.randomUUID();
        UUID formationId = UUID.randomUUID();
        LivingBridgeRouteSnapshot snapshot = new LivingBridgeRouteSnapshot(reservationId, worker.getUUID(),
                formationId, level.dimension().location().toString(), 0L, UUID.randomUUID(), 1L,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(LivingBridgeRouteSnapshot.Point.from(worker.getX(), worker.getY(), worker.getZ())),
                3072L, List.of(), 0, null, worker.chunkPosition().x, worker.chunkPosition().z);
        LivingBridgeRouteData.get(level).putSnapshot(snapshot);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", LivingBridgeRouteData.CURRENT_SCHEMA);
        capsule.put("snapshot", snapshot.toTag());
        capsule.putString("snapshotDigest", snapshot.digest());
        worker.getPersistentData().put("livingBridgeControl", capsule);
        CompoundTag ownership = new CompoundTag();
        ownership.putUUID("session", formationId);
        ownership.putBoolean("originalGravity", true);
        ownership.putBoolean("originalNoPhysics", false);
        worker.getPersistentData().put("soundattract_living_bridge", ownership);
        worker.setNoGravity(false);
        worker.noPhysics = false;
        worker.setDeltaMovement(0.4D, 0.2D, -0.3D);

        Method recover = LivingWallCoordinator.class.getDeclaredMethod("recoverStaleControlledMob",
                net.minecraft.server.level.ServerLevel.class, Mob.class, String.class);
        recover.setAccessible(true);
        recover.invoke(null, level, worker, "route_lifecycle_game_test");

        if (!worker.isNoGravity() || !worker.noPhysics || !worker.getNavigation().isDone()
                || !worker.getDeltaMovement().equals(Vec3.ZERO) || !worker.isPersistenceRequired()
                || !worker.getPersistentData().contains("soundattract_living_bridge")
                || !worker.getPersistentData().getBoolean("livingBridgeRecoveryRequiredLogged")
                || !worker.getPersistentData().getCompound("livingBridgeControl").equals(capsule)) {
            helper.fail("formation cleanup recovery released an exact active durable route owner");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void staleRecoveryKeepsOpaqueLegacyOwnerHeld(GameTestHelper helper) throws Exception {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", 1);
        capsule.putString("opaqueEvidence", "retain-exactly");
        worker.getPersistentData().put("livingBridgeControl", capsule.copy());
        LivingBridgeRouteData.quarantineEntityCapsule(LivingBridgeRouteData.get(level), capsule,
                level.dimension().location().toString(), true);
        CompoundTag ownership = new CompoundTag();
        ownership.putUUID("session", UUID.randomUUID());
        ownership.putBoolean("originalGravity", true);
        ownership.putBoolean("originalNoPhysics", false);
        worker.getPersistentData().put("soundattract_living_bridge", ownership);
        worker.setNoGravity(false);
        worker.noPhysics = false;
        worker.setDeltaMovement(0.3D, 0.1D, -0.2D);

        Method recover = LivingWallCoordinator.class.getDeclaredMethod("recoverStaleControlledMob",
                net.minecraft.server.level.ServerLevel.class, Mob.class, String.class);
        recover.setAccessible(true);
        recover.invoke(null, level, worker, "opaque_route_lifecycle_game_test");

        if (!worker.isNoGravity() || !worker.noPhysics || !worker.getNavigation().isDone()
                || !worker.getDeltaMovement().equals(Vec3.ZERO) || !worker.isPersistenceRequired()
                || !worker.getPersistentData().contains("soundattract_living_bridge")
                || !worker.getPersistentData().getBoolean("livingBridgeRecoveryRequiredLogged")
                || !worker.getPersistentData().getCompound("livingBridgeControl").equals(capsule)) {
            helper.fail("stale formation recovery released an opaque legacy owner without safe recovery");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void mutatedNextNavigationEdgeIsCancelledBeforeVanillaPathAdvance(GameTestHelper helper) throws Exception {
        Method factory = LivingWallCoordinatorGameTests.class.getDeclaredMethod(
                "newFixture", GameTestHelper.class, String.class, boolean.class);
        factory.setAccessible(true);
        Object fixture = factory.invoke(null, helper, "mutated-edge", false);
        ServerLevelView view = fixtureView(fixture);
        Mob worker = view.worker;
        Object formation = view.formation;
        setField(formation, "activeMemberSuctionEngaged", false);
        @SuppressWarnings("unchecked")
        List<UUID> members = (List<UUID>) getField(formation, "members");
        members.clear();
        members.add(worker.getUUID());
        @SuppressWarnings("unchecked")
        Set<UUID> structuralBuilders = (Set<UUID>) getField(formation, "structuralBuilderIds");
        structuralBuilders.clear();
        structuralBuilders.add(worker.getUUID());
        setField(formation, "phase", phase("ASSEMBLE"));
        setField(formation, "assembledSlots", 0);
        setField(formation, "activeStructuralSlot", 0);
        BlockPos pivot = helper.absolutePos(new BlockPos(6, 1, 2));
        Vec3 pivotPosition = Vec3.atBottomCenterOf(pivot);
        Vec3 sourceCenter = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 2)));
        BlockPos sourceBucket = helper.absolutePos(new BlockPos(0, 1, 2));
        BlockPos staging = helper.absolutePos(new BlockPos(4, 1, 2));
        worker.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 2))));
        BlockPos mutated = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos middle = helper.absolutePos(new BlockPos(3, 1, 2));
        Path path = new Path(List.of(new Node(mutated.getX(), mutated.getY(), mutated.getZ()),
                new Node(middle.getX(), middle.getY(), middle.getZ()),
                new Node(staging.getX(), staging.getY(), staging.getZ())), staging, true);
        if (!worker.getNavigation().moveTo(path, 1.0D) || path.isDone()) {
            helper.fail("could not install the complete pre-mutation ordinary path");
            return;
        }
        Constructor<?> routeConstructor = Class.forName(
                        "com.example.soundattract.ai.LivingWallCoordinator$SafeBaseNavigationReservation")
                .getDeclaredConstructor(UUID.class, UUID.class, int.class, int.class,
                        phase("ASSEMBLE").getClass(), long.class, Vec3.class, Vec3.class,
                        Vec3.class, BlockPos.class, BlockPos.class, List.class, Path.class,
                        net.minecraft.resources.ResourceLocation.class, Vec3.class, AABB.class,
                        net.minecraft.world.entity.Pose.class, String.class);
        routeConstructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> reservations = (Map<UUID, Object>) getField(formation, "safeBaseNavigation");
        Method stateMethod = LivingWallCoordinator.class.getDeclaredMethod("state",
                net.minecraft.server.level.ServerLevel.class);
        stateMethod.setAccessible(true);
        Object levelState = stateMethod.invoke(null, helper.getLevel());
        @SuppressWarnings("unchecked")
        Map<UUID, UUID> mobOwners = (Map<UUID, UUID>) getField(levelState, "mobOwners");
        mobOwners.put(worker.getUUID(), (UUID) getField(formation, "id"));
        @SuppressWarnings("unchecked")
        Map<UUID, Object> structuralLanes = (Map<UUID, Object>) getField(formation,
                "structuralApproachReservations");
        structuralLanes.put(worker.getUUID(), newStructuralLane((UUID) getField(formation, "id"), worker));
        worker.getPersistentData().putBoolean("livingBridgeSafeBaseRouteActive", true);
        LivingBridgeNavigationGuardProbe.reset();
        Vec3 before = worker.position();
        for (int x = 3; x <= 5; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.AIR.defaultBlockState());
            }
        }
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.AIR.defaultBlockState());
        List<BlockPos> certifiedNodes = List.of(mutated, middle, staging);
        Method fingerprintMethod = LivingWallCoordinator.class.getDeclaredMethod("routeWorldFingerprint",
                net.minecraft.server.level.ServerLevel.class, List.class);
        fingerprintMethod.setAccessible(true);
        String fingerprint = (String) fingerprintMethod.invoke(null, helper.getLevel(), certifiedNodes);
        Object request = getField(formation, "request");
        long requestRevision = (long) getField(request, "revision");
        Object route = routeConstructor.newInstance((UUID) getField(view.formation, "id"), worker.getUUID(), 0, 0,
                phase("ASSEMBLE"), requestRevision, pivotPosition, new Vec3(1.0D, 0.0D, 0.0D),
                sourceCenter, sourceBucket, staging, certifiedNodes, path,
                helper.getLevel().dimension().location(), worker.position(), worker.getBoundingBox(),
                worker.getPose(), fingerprint);
        reservations.put(worker.getUUID(), route);
        Method edgeCheck = LivingWallCoordinator.class.getDeclaredMethod("safeBaseNavigationEdgeResult",
                net.minecraft.server.level.ServerLevel.class, Mob.class, formation.getClass(), route.getClass(),
                Vec3.class, Vec3.class);
        edgeCheck.setAccessible(true);
        Object edgeResult = edgeCheck.invoke(null, helper.getLevel(), worker, formation, route, before,
                Vec3.atBottomCenterOf(mutated));
        Method rejectionAccessor = edgeResult.getClass().getDeclaredMethod("rejection");
        rejectionAccessor.setAccessible(true);
        String rejection = rejectionAccessor.invoke(edgeResult).toString();

        worker.getNavigation().tick();

        worker.getPersistentData().remove("livingBridgeSafeBaseRouteActive");
        boolean heldPosition = before.equals(worker.position());
        boolean contactedHazard = worker.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(mutated));
        boolean pathStopped = worker.getNavigation().isDone();
        int guardCount = LivingBridgeNavigationGuardProbe.invocationCount();
        boolean replanQueued = pendingReplan(formation, worker.getUUID());
        @SuppressWarnings("unchecked")
        Set<UUID> terminalFailures = (Set<UUID>) getField(formation, "terminalSafeBaseFailures");
        boolean terminalFailureRecorded = terminalFailures.contains(worker.getUUID());
        if (!heldPosition || contactedHazard || !pathStopped || guardCount != 1 || replanQueued
                || !terminalFailureRecorded
                || !"UNSUPPORTED".equals(rejection)
                || !structuralLanes.containsKey(worker.getUUID())) {
            helper.fail("mutated edge guard evidence held=" + heldPosition + " pathStopped=" + pathStopped
                    + " contactedHazard=" + contactedHazard
                    + " guardCount=" + guardCount + " replanQueued=" + replanQueued
                    + " terminalFailureRecorded=" + terminalFailureRecorded + " rejection=" + rejection
                    + " laneRetained=" + structuralLanes.containsKey(worker.getUUID())
                    + " reservationPresent=" + reservations.containsKey(worker.getUUID())
                    + " nextIndex=" + path.getNextNodeIndex()
                    + " nextNode=" + path.getNode(path.getNextNodeIndex()).asBlockPos()
                    + " supportAtMutated=" + helper.getLevel().getBlockState(mutated.below()));
            return;
        }
        SoundAttractMod.LOGGER.info("LIVING_BRIDGE_SAFE_BASE_MUTATED_EDGE_REJECTED");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void legacyV1JoinCallbackHoldsWorkerBeforeTick(GameTestHelper helper) {
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", 1);
        capsule.putString("mode", "WALKING_TO_STAGING");
        worker.getPersistentData().put("livingBridgeControl", capsule.copy());

        LivingBridgeRouteData.onEntityJoinLevel(new EntityJoinLevelEvent(worker, helper.getLevel(), true));

        if (!worker.isNoGravity() || !worker.noPhysics || !worker.getNavigation().isDone()
                || worker.getDeltaMovement().x != 0.0D || worker.getDeltaMovement().z != 0.0D
                || !capsule.equals(worker.getPersistentData().getCompound("livingBridgeControl"))) {
            helper.fail("opaque schema-v1 join did not preserve and hold the worker before its next tick");
            return;
        }
        if (!LivingBridgeRouteData.get(helper.getLevel()).admissionBlocked(
                helper.getLevel().dimension().location().toString())) {
            helper.fail("opaque schema-v1 join did not block its dimension");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void futureEntityCapsuleIsPreservedAndQuarantinedBeforeFirstTick(GameTestHelper helper) {
        Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F, 2.0F, 2.5F);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", 7);
        capsule.putByteArray("futureBytes", new byte[]{3, 0, -1, 91});
        worker.getPersistentData().put("livingBridgeControl", capsule.copy());

        LivingBridgeRouteData.onEntityJoinLevel(new EntityJoinLevelEvent(worker, helper.getLevel(), true));

        if (!worker.isNoGravity() || !worker.noPhysics || !worker.getNavigation().isDone()
                || worker.getDeltaMovement().x != 0.0D || worker.getDeltaMovement().z != 0.0D
                || !capsule.equals(worker.getPersistentData().getCompound("livingBridgeControl"))) {
            helper.fail("future capsule was not preserved under a pre-tick physics hold");
            return;
        }
        LivingBridgeRouteData data = LivingBridgeRouteData.get(helper.getLevel());
        if (!data.admissionBlocked(helper.getLevel().dimension().location().toString())
                || data.quarantineCount() == 0) {
            helper.fail("future capsule quarantine did not retain its dimension barrier");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public void airborneReservationSurvivesChunkUnloadAndReentry(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        int remoteChunkX = 96;
        int remoteChunkZ = 96;
        BlockPos remote = new BlockPos(remoteChunkX * 16 + 8, 120, remoteChunkZ * 16 + 8);
        level.getChunkAt(remote);
        Mob worker = EntityType.ZOMBIE.create(level);
        if (worker == null) {
            helper.fail("could not create remote airborne reservation owner");
            return;
        }
        worker.setPersistenceRequired();
        worker.setPos(remote.getX() + 0.5D, remote.getY(), remote.getZ() + 0.5D);
        if (!level.addFreshEntity(worker)) {
            helper.fail("could not add remote reservation owner");
            return;
        }
        UUID formationId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        LivingBridgeRouteSnapshot snapshot = new LivingBridgeRouteSnapshot(reservationId, worker.getUUID(),
                formationId, level.dimension().location().toString(), 0L, transactionId, 1L,
                "CONTROLLED_RECOVERY", "RECOVERY_REQUIRED", false, false, true,
                List.of(LivingBridgeRouteSnapshot.Point.from(worker.getX(), worker.getY(), worker.getZ())),
                3072L, List.of(LivingBridgeRouteSnapshot.Point.from(worker.getX(), worker.getY(), worker.getZ())),
                0, null, remoteChunkX, remoteChunkZ);
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        data.putSnapshot(snapshot);
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", LivingBridgeRouteData.CURRENT_SCHEMA);
        capsule.put("snapshot", snapshot.toTag());
        capsule.putString("snapshotDigest", snapshot.digest());
        worker.getPersistentData().put("livingBridgeControl", capsule);
        worker.setNoGravity(true);
        worker.noPhysics = true;
        worker.setDeltaMovement(Vec3.ZERO);
        UUID workerId = worker.getUUID();
        EXPECTED_CAPSULES.put(workerId, capsule.copy());
        REENTRY_EVIDENCE.put(workerId, new ReentryEvidence(false, false, false, false, false, false));
        awaitRemoteChunkUnload(helper, level, data, snapshot, workerId, remote, remoteChunkX, remoteChunkZ, 0);
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public void legacyV1CapsuleIsHeldBeforeFirstTickAfterChunkReentry(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        int remoteChunkX = 92;
        int remoteChunkZ = 92;
        BlockPos remote = new BlockPos(remoteChunkX * 16 + 8, 120, remoteChunkZ * 16 + 8);
        level.getChunkAt(remote);
        Mob worker = EntityType.ZOMBIE.create(level);
        if (worker == null) {
            helper.fail("could not create remote opaque-capsule owner");
            return;
        }
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", 1);
        capsule.putString("mode", "OPAQUE_LEGACY_OWNER");
        capsule.putLong("opaqueEvidence", 0x51A7E1L);
        worker.getPersistentData().put("livingBridgeControl", capsule.copy());
        worker.setNoGravity(true);
        worker.noPhysics = true;
        worker.setPersistenceRequired();
        worker.setDeltaMovement(Vec3.ZERO);
        worker.setPos(remote.getX() + 0.5D, remote.getY(), remote.getZ() + 0.5D);
        EXPECTED_CAPSULES.put(worker.getUUID(), capsule.copy());
        REENTRY_EVIDENCE.put(worker.getUUID(), new ReentryEvidence(false, false, false, false, false, false));
        if (!level.addFreshEntity(worker)) {
            helper.fail("could not add remote opaque-capsule owner");
            return;
        }
        awaitLegacyChunkUnload(helper, level, worker.getUUID(), capsule, remote,
                remoteChunkX, remoteChunkZ, 0);
    }

    private static void awaitLegacyChunkUnload(GameTestHelper helper,
                                               net.minecraft.server.level.ServerLevel level,
                                               UUID workerId, CompoundTag capsule, BlockPos remote,
                                               int chunkX, int chunkZ, int elapsedTicks) {
        helper.runAfterDelay(20, () -> {
            if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                if (elapsedTicks >= 1000) {
                    helper.fail("opaque-capsule chunk did not unload without a forced ticket");
                    return;
                }
                awaitLegacyChunkUnload(helper, level, workerId, capsule, remote,
                        chunkX, chunkZ, elapsedTicks + 20);
                return;
            }
            ReentryEvidence evidence = REENTRY_EVIDENCE.get(workerId);
            if (level.getEntity(workerId) != null || evidence == null || !evidence.leftLevel()) {
                helper.fail("chunk status dropped without removing the entity from the live level");
                return;
            }
            helper.runAfterDelay(20, () -> {
                if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    helper.fail("opaque-capsule chunk reloaded before explicit disk-reentry probe");
                    return;
                }
                level.getChunkAt(remote);
                awaitLegacyEntityReentry(helper, level, workerId, capsule, chunkX, chunkZ, 0);
            });
        });
    }

    private static void awaitLegacyEntityReentry(GameTestHelper helper,
                                                  net.minecraft.server.level.ServerLevel level,
                                                  UUID workerId, CompoundTag capsule,
                                                  int chunkX, int chunkZ, int elapsedTicks) {
        helper.runAfterDelay(1, () -> {
            ReentryEvidence diskEvidence = REENTRY_EVIDENCE.get(workerId);
            if (diskEvidence != null && diskEvidence.leftLevel() && diskEvidence.loadedFromDisk()) {
                if (!diskEvidence.heldBeforeTick() || !diskEvidence.rawPreserved() || !diskEvidence.archived()) {
                    helper.fail("legacy disk-join proof failed held=" + diskEvidence.heldBeforeTick()
                            + " rawPreserved=" + diskEvidence.rawPreserved()
                            + " archived=" + diskEvidence.archived());
                    return;
                }
                SoundAttractMod.LOGGER.info("LIVING_BRIDGE_SAFE_BASE_V1_REENTRY_HELD");
                helper.succeed();
                return;
            }
            net.minecraft.world.entity.Entity reentered = level.getEntity(workerId);
            if (!(reentered instanceof Mob worker)) {
                if (elapsedTicks >= 100) {
                    ReentryEvidence evidence = REENTRY_EVIDENCE.get(workerId);
                    helper.fail("opaque-capsule owner did not re-enter after chunk reload; chunkLoaded="
                            + level.getChunkSource().hasChunk(chunkX, chunkZ) + " evidence=" + evidence);
                    return;
                }
                awaitLegacyEntityReentry(helper, level, workerId, capsule, chunkX, chunkZ, elapsedTicks + 1);
                return;
            }
            boolean held = worker.isNoGravity() && worker.noPhysics && worker.getNavigation().isDone()
                    && worker.getDeltaMovement().x == 0.0D && worker.getDeltaMovement().z == 0.0D;
            boolean rawPreserved = capsule.equals(worker.getPersistentData().getCompound("livingBridgeControl"));
            LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
            net.minecraft.nbt.ListTag ledger = data.save(new CompoundTag())
                    .getList("quarantineLedger", net.minecraft.nbt.Tag.TAG_COMPOUND);
            boolean archived = false;
            for (int index = 0; index < ledger.size(); index++) {
                CompoundTag entry = ledger.getCompound(index);
                if (entry.getString("classification").equals("OPAQUE_V1_ENTITY")
                        && entry.getCompound("firstRaw").equals(capsule)) archived = true;
            }
            ReentryEvidence evidence = REENTRY_EVIDENCE.get(workerId);
            if (!held || !rawPreserved || !archived || evidence == null
                    || !evidence.leftLevel() || !evidence.loadedFromDisk() || !evidence.heldBeforeTick()
                    || !evidence.rawPreserved() || !evidence.archived()) {
                helper.fail("legacy capsule reentry evidence held=" + held + " noGravity=" + worker.isNoGravity()
                        + " noPhysics=" + worker.noPhysics + " navDone=" + worker.getNavigation().isDone()
                        + " rawPreserved=" + rawPreserved + " archived=" + archived);
                return;
            }
            SoundAttractMod.LOGGER.info("LIVING_BRIDGE_SAFE_BASE_V1_REENTRY_HELD");
            helper.succeed();
        });
    }

    private static void awaitRemoteChunkUnload(GameTestHelper helper,
                                               net.minecraft.server.level.ServerLevel level,
                                               LivingBridgeRouteData data,
                                               LivingBridgeRouteSnapshot snapshot,
                                               UUID workerId, BlockPos remote,
                                               int chunkX, int chunkZ, int elapsedTicks) {
        helper.runAfterDelay(20, () -> {
            if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                if (elapsedTicks >= 1000) {
                    helper.fail("remote reservation chunk did not unload without a forced ticket");
                    return;
                }
                awaitRemoteChunkUnload(helper, level, data, snapshot, workerId, remote,
                        chunkX, chunkZ, elapsedTicks + 20);
                return;
            }
            ReentryEvidence evidence = REENTRY_EVIDENCE.get(workerId);
            if (level.getEntity(workerId) != null || evidence == null || !evidence.leftLevel()) {
                helper.fail("reservation chunk status dropped without removing the entity from the live level");
                return;
            }
            List<BlockPos> persistedLane = snapshot.corridor().stream()
                    .map(point -> BlockPos.containing(point.x() / 4096.0D,
                            point.y() / 4096.0D, point.z() / 4096.0D))
                    .toList();
            LivingBridgeRouteSnapshot unloadedCopy = data.snapshot(snapshot.reservationId());
            if (unloadedCopy == null || !unloadedCopy.exactlyEquals(snapshot)) {
                helper.fail("SavedData no longer retains the exact route owner while its entity is unloaded");
                return;
            }
            if (!data.admissionBlocked(snapshot.dimension())
                    && (!data.hasConflictingLane(snapshot.dimension(), UUID.randomUUID(), UUID.randomUUID(),
                    persistedLane, snapshot.halfWidth() * 2.0D / 4096.0D, 0.0D)
                    || data.hasConflictingLane(snapshot.dimension(), snapshot.ownerId(), snapshot.reservationId(),
                    persistedLane, snapshot.halfWidth() * 2.0D / 4096.0D, 0.0D))) {
                helper.fail("unloaded route did not retain exclusive lane/self-refresh semantics");
                return;
            }
            helper.runAfterDelay(20, () -> {
                if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    helper.fail("reservation chunk reloaded before explicit disk-reentry probe");
                    return;
                }
                level.getChunkAt(remote);
                awaitRemoteEntityReentry(helper, level, data, snapshot, workerId, 0);
            });
        });
    }

    private static void awaitRemoteEntityReentry(GameTestHelper helper,
                                                 net.minecraft.server.level.ServerLevel level,
                                                 LivingBridgeRouteData data,
                                                 LivingBridgeRouteSnapshot snapshot,
                                                 UUID workerId, int elapsedTicks) {
        helper.runAfterDelay(1, () -> {
            ReentryEvidence diskEvidence = REENTRY_EVIDENCE.get(workerId);
            if (diskEvidence != null && diskEvidence.leftLevel() && diskEvidence.loadedFromDisk()) {
                if (!diskEvidence.heldBeforeTick() || !diskEvidence.rawPreserved()
                        || !diskEvidence.savedAgreement()) {
                    helper.fail("v2 disk-join proof failed held=" + diskEvidence.heldBeforeTick()
                            + " rawPreserved=" + diskEvidence.rawPreserved()
                            + " exactSavedAgreement=" + diskEvidence.savedAgreement());
                    return;
                }
                SoundAttractMod.LOGGER.info("LIVING_BRIDGE_SAFE_BASE_CHUNK_REENTRY_PASS");
                helper.succeed();
                return;
            }
            net.minecraft.world.entity.Entity reentered = level.getEntity(workerId);
            if (!(reentered instanceof Mob worker)) {
                if (elapsedTicks >= 100) {
                    ReentryEvidence evidence = REENTRY_EVIDENCE.get(workerId);
                    helper.fail("saved remote owner did not re-enter within the bounded chunk reload wait; chunkLoaded="
                            + level.getChunkSource().hasChunk(snapshot.lastChunkX(), snapshot.lastChunkZ())
                            + " evidence=" + evidence);
                    return;
                }
                awaitRemoteEntityReentry(helper, level, data, snapshot, workerId, elapsedTicks + 1);
                return;
            }
            CompoundTag capsule = worker.getPersistentData().getCompound("livingBridgeControl");
            boolean heldBeforeTick = worker.isNoGravity() && worker.noPhysics
                    && worker.getNavigation().isDone()
                    && worker.getDeltaMovement().x == 0.0D && worker.getDeltaMovement().z == 0.0D;
            LivingBridgeRouteSnapshot saved = data.snapshot(snapshot.reservationId());
            boolean exactCopies = LivingBridgeRouteSnapshot.fromTag(capsule.getCompound("snapshot"))
                    .exactlyEquals(snapshot) && saved != null && saved.exactlyEquals(snapshot);
            ReentryEvidence evidence = REENTRY_EVIDENCE.get(workerId);
            if (!heldBeforeTick || !exactCopies || evidence == null
                    || !evidence.loadedFromDisk() || !evidence.heldBeforeTick()) {
                helper.fail("reentered owner evidence held=" + heldBeforeTick + " exactCopies=" + exactCopies
                        + " diskJoin=" + (evidence != null && evidence.loadedFromDisk())
                        + " heldAtJoin=" + (evidence != null && evidence.heldBeforeTick()));
                return;
            }
            if (!evidence.rawPreserved() || !evidence.savedAgreement()) {
                helper.fail("reentered owner raw/saved proof failed rawPreserved=" + evidence.rawPreserved()
                        + " exactSavedAgreement=" + evidence.savedAgreement());
                return;
            }
            SoundAttractMod.LOGGER.info("LIVING_BRIDGE_SAFE_BASE_CHUNK_REENTRY_PASS");
            helper.succeed();
        });
    }

    private static ServerLevelView fixtureView(Object fixture) throws Exception {
        return new ServerLevelView((Mob) getField(fixture, "worker"), getField(fixture, "formation"));
    }

    private static boolean pendingReplan(Object formation, UUID workerId) throws Exception {
        @SuppressWarnings("unchecked")
        Set<UUID> pending = (Set<UUID>) getField(formation, "pendingSafeBaseReplans");
        return pending.contains(workerId);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record ServerLevelView(Mob worker, Object formation) {
    }
}
