package com.example.soundattract.ai;

import com.example.soundattract.config.separate.LivingWallConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("soundattract_bridge_recovery")
@PrefixGameTestTemplate(false)
public final class LivingBridgeOrphanRecoveryGameTests {
    private static final String CONTROL = "livingBridgeControl";
    private static final String MARKER = "soundattract_living_bridge";
    private static final String RECOVERING = "RECOVERING_TO_SAFE_ENDPOINT";
    private static final String COMPLETE = "SAFE_RECOVERY_COMPLETE";
    private static final int MAX_RECOVERY_TICKS = 220;
    private static final int MAX_LONG_RECOVERY_TICKS = 420;
    private static final double FOOT_CONTACT = 0.01D;
    private static final double MAX_RECOVERY_STEP = 0.2D + Math.sqrt(3.0D) / 4096.0D;

    @GameTest(template = "empty", setupTicks = 60, timeoutTicks = MAX_RECOVERY_TICKS + 5)
    public void displacedDurableOwnersRecoverToGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        boolean previouslyEnabled = LivingWallConfig.ENABLE_LIVING_WALL.get();
        List<Worker> workers = new ArrayList<>();
        LivingWallConfig.ENABLE_LIVING_WALL.set(true);
        try {
            Worker walking = diskJoinWorker(helper, data, workers, "walking", 3, 21, false, false, false);
            Worker recovering = diskJoinWorker(helper, data, workers, "recovering", 10, 21, true, false, false);
            Worker recoveringWithPositionDrift = diskJoinWorker(helper, data, workers,
                    "recovering with position drift", 31, 3.0D, true, true, false,
                    new Vec3(0.3D, 0.0D, 0.0D), EntityType.SHEEP);
            Worker airborne = diskJoinWorker(helper, data, workers, "on-route airborne", 17, 3.0D, true, true, false);
            Worker blocked = diskJoinWorker(helper, data, workers, "lava-blocked", 24, 21, false, false, true);
            Worker nearEndpoint = diskJoinSameBlockAirborne(helper, data, workers);
            assertOffCenterOrdinaryRecoveryCompletes(helper, data);
            assertOrphanEndpointIsRevalidated(helper, data);
            Map<UUID, Vec3> previousPositions = new HashMap<>();
            awaitRecovery(helper, data, workers, walking, recovering, recoveringWithPositionDrift,
                    airborne, blocked, nearEndpoint, previousPositions, previouslyEnabled, 1);
        } catch (RuntimeException | AssertionError failure) {
            restoreFixture(workers, previouslyEnabled);
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "longRecovery", setupTicks = 40,
            timeoutTicks = MAX_LONG_RECOVERY_TICKS + 10)
    public void highOrphanRecoveryUsesBoundedRouteSegments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        boolean previouslyEnabled = LivingWallConfig.ENABLE_LIVING_WALL.get();
        List<Worker> workers = new ArrayList<>();
        LivingWallConfig.ENABLE_LIVING_WALL.set(true);
        try {
            sealRecoveryTube(helper, 8, 4, 62);
            Worker worker = diskJoinWorker(helper, data, workers, "long drop", 8, 62,
                    false, false, false);
            invokeRecoveryTick(level, worker.mob());
            awaitLongRecovery(helper, data, worker, workers, previouslyEnabled, null, 1);
        } catch (RuntimeException | AssertionError failure) {
            restoreFixture(workers, previouslyEnabled);
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "longRecovery", setupTicks = 40,
            timeoutTicks = MAX_LONG_RECOVERY_TICKS + 10)
    public void displacedOrphanUsesItsActualFootprintForLanding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        boolean previouslyEnabled = LivingWallConfig.ENABLE_LIVING_WALL.get();
        List<Worker> workers = new ArrayList<>();
        LivingWallConfig.ENABLE_LIVING_WALL.set(true);
        try {
            Worker worker = diskJoinWorker(helper, data, workers, "single-block ledge", 8, 20,
                    true, false, false, Vec3.ZERO, EntityType.ZOMBIE, 40);
            BlockPos base = BlockPos.containing(worker.landing());
            BlockPos support = base.offset(-1, -1, 0);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 4; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(support, Blocks.GRASS_BLOCK.defaultBlockState());
            Vec3 expectedPosition = Vec3.atBottomCenterOf(support.above());
            helper.assertTrue(level.getBlockState(support).is(Blocks.GRASS_BLOCK),
                    "single-block ledge was not written at its absolute world position");
            helper.assertTrue(!isStrictOrphanLanding(level, worker.mob(), support.above()),
                    "single-block ledge fixture unexpectedly satisfies the strict 3x3 landing rule");
            helper.assertTrue(isRecoveryFootprintLanding(level, worker.mob(), support.above(), expectedPosition),
                    "single-block ledge fixture fails the recovery footprint validator");
            worker.mob().getPersistentData().remove(MARKER);
            invokeRecoveryTick(level, worker.mob());
            awaitFootprintRoutePlanned(helper, level, data, worker, workers, previouslyEnabled,
                    expectedPosition, support.above(), 1);
        } catch (RuntimeException | AssertionError failure) {
            restoreFixture(workers, previouslyEnabled);
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "disabledRecovery", setupTicks = 60,
            timeoutTicks = MAX_RECOVERY_TICKS + 5)
    public void disabledBridgeStillCompletesProtectedRecovery(GameTestHelper helper) {
        LivingBridgeRouteData data = LivingBridgeRouteData.get(helper.getLevel());
        boolean previouslyEnabled = LivingWallConfig.ENABLE_LIVING_WALL.get();
        List<Worker> workers = new ArrayList<>();
        LivingWallConfig.ENABLE_LIVING_WALL.set(false);
        try {
            assertDisabledGroundedWalkerCompletes(helper, data);
            Worker worker = diskJoinWorker(helper, data, workers, "disabled bridge recovery", 10,
                    3.0D, true, true, false);
            awaitDisabledRecovery(helper, data, worker, workers, previouslyEnabled, 1);
        } catch (RuntimeException | AssertionError failure) {
            restoreFixture(workers, previouslyEnabled);
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "journalCapacity", setupTicks = 20)
    public void fullJournalStillAllowsExistingRecoveryCommit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        BlockPos endpoint = helper.absolutePos(new BlockPos(8, 3, 8));
        Vec3 end = Vec3.atBottomCenterOf(endpoint);
        Vec3 start = end.add(0.0D, 2.0D, 0.0D);
        Mob mob = EntityType.ZOMBIE.create(level);
        helper.assertTrue(mob != null, "journal capacity owner could not be created");
        mob.setPos(start.x, start.y, start.z);
        UUID formation = UUID.randomUUID();
        LivingBridgeRouteSnapshot walking = new LivingBridgeRouteSnapshot(UUID.randomUUID(), mob.getUUID(),
                formation, level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(point(start), point(end)), Math.round(mob.getBbWidth() * 2048.0D),
                List.of(), 0, point(end), mob.chunkPosition().x, mob.chunkPosition().z);
        helper.assertTrue(data.beginInitialClaim(walking, mob), "journal capacity owner claim failed");
        mob.setNoGravity(true);
        mob.noPhysics = true;
        level.addLegacyChunkEntities(Stream.of(mob));
        Map<UUID, CompoundTag> journal = null;
        Map<UUID, CompoundTag> originalJournal = null;
        try {
            var journalField = LivingBridgeRouteData.class.getDeclaredField("transitionJournal");
            journalField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, CompoundTag> loadedJournal = (Map<UUID, CompoundTag>) journalField.get(data);
            journal = loadedJournal;
            originalJournal = new HashMap<>(journal);
            for (int index = journal.size(); index < 512; index++) {
                CompoundTag row = new CompoundTag();
                row.putUUID("reservationId", UUID.randomUUID());
                journal.put(row.getUUID("reservationId"), row);
            }

            LivingBridgeRouteSnapshot recovering = LivingBridgeRouteData.recoveringSnapshot(walking,
                    List.of(point(start), point(end)), 1, mob.chunkPosition().x, mob.chunkPosition().z);
            helper.assertTrue(data.commitRecoveryTransition(walking, recovering, mob),
                    "full journal rejected an update to an existing recovery owner");
            LivingBridgeRouteSnapshot saved = data.snapshot(walking.reservationId());
            CompoundTag capsule = mob.getPersistentData().getCompound("livingBridgeControl");
            helper.assertTrue(saved != null && saved.exactlyEquals(recovering)
                            && recovering.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(capsule.getCompound("snapshot")))
                            && LivingBridgeRouteSnapshot.fromTag(journal.get(walking.reservationId()).getCompound("to"))
                            .exactlyEquals(recovering)
                            && data.save(new CompoundTag(), null).getList("transitionJournal", Tag.TAG_COMPOUND).size() == 512,
                    "full-journal recovery update did not keep entity, saved-data, and journal copies aligned");

            Mob additional = EntityType.ZOMBIE.create(level);
            helper.assertTrue(additional != null, "new journal capacity claimant could not be created");
            additional.setPos(start.x + 3.0D, start.y, start.z);
            LivingBridgeRouteSnapshot anotherClaim = new LivingBridgeRouteSnapshot(UUID.randomUUID(), additional.getUUID(),
                    UUID.randomUUID(), level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                    "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                    List.of(point(start), point(end)), Math.round(additional.getBbWidth() * 2048.0D),
                    List.of(), 0, point(end), additional.chunkPosition().x, additional.chunkPosition().z);
            helper.assertTrue(!data.beginInitialClaim(anotherClaim, additional),
                    "full journal allowed a new reservation beyond its configured bound");
            additional.discard();
            helper.succeed();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not populate the bounded recovery journal fixture", failure);
        } finally {
            if (journal != null && originalJournal != null) {
                journal.clear();
                journal.putAll(originalJournal);
            }
            mob.discard();
        }
    }

    private static void assertDisabledGroundedWalkerCompletes(GameTestHelper helper,
                                                               LivingBridgeRouteData data) {
        ServerLevel level = helper.getLevel();
        BlockPos localFeet = new BlockPos(20, 3, 16);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            helper.setBlock(localFeet.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 3; y++) helper.setBlock(localFeet.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        BlockPos feet = helper.absolutePos(localFeet);
        Vec3 endpoint = Vec3.atBottomCenterOf(feet);
        Vec3 routeStart = endpoint.add(-1.0D, 0.0D, 0.0D);
        Mob original = EntityType.ZOMBIE.create(level);
        helper.assertTrue(original != null, "disabled grounded walker zombie could not be created");
        original.setPos(endpoint.x, endpoint.y, endpoint.z);
        helper.assertTrue(hasFootContact(level, original) && level.noCollision(original, original.getBoundingBox()),
                "disabled grounded walker must be at clear supported endpoint");
        UUID formation = UUID.randomUUID();
        LivingBridgeRouteSnapshot walking = new LivingBridgeRouteSnapshot(UUID.randomUUID(), original.getUUID(),
                formation, level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(point(routeStart), point(endpoint)), Math.round(original.getBbWidth() * 2048.0D),
                List.of(), 0, point(endpoint), original.chunkPosition().x, original.chunkPosition().z);
        helper.assertTrue(data.beginInitialClaim(walking, original),
                "disabled grounded walker could not commit its persisted route");
        CompoundTag marker = new CompoundTag();
        marker.putUUID("session", formation);
        marker.putBoolean("originalGravity", true);
        marker.putBoolean("originalNoPhysics", false);
        original.getPersistentData().put(MARKER, marker);
        original.setNoGravity(true);
        original.noPhysics = true;
        CompoundTag savedEntity = new CompoundTag();
        helper.assertTrue(original.save(savedEntity), "disabled grounded walker did not serialize");
        Mob reloaded = (Mob) EntityType.loadEntityRecursive(savedEntity, level, Function.identity());
        helper.assertTrue(reloaded != null, "disabled grounded walker did not reload");
        level.addLegacyChunkEntities(Stream.of(reloaded));
        LivingBridgeRouteSnapshot joined = data.snapshot(walking.reservationId());
        helper.assertTrue(joined != null && !COMPLETE.equals(joined.lifecycleState())
                        && reloaded.isNoGravity() && reloaded.noPhysics,
                "disabled grounded walker was completed synchronously during entity join");
        helper.runAfterDelay(1, () -> {
            LivingBridgeRouteSnapshot completed = data.snapshot(walking.reservationId());
            helper.assertTrue(completed != null && COMPLETE.equals(completed.lifecycleState())
                            && !reloaded.isNoGravity() && !reloaded.noPhysics
                            && hasFootContact(level, reloaded) && level.noCollision(reloaded, reloaded.getBoundingBox()),
                    "disabled grounded walker did not complete from the queued server-tick recovery");
            reloaded.discard();
        });
    }

    private static void awaitDisabledRecovery(GameTestHelper helper, LivingBridgeRouteData data,
                                               Worker worker, List<Worker> workers,
                                               boolean previouslyEnabled, int elapsedTicks) {
        helper.runAfterDelay(1, () -> {
            try {
                LivingBridgeRouteSnapshot state = assertCurrentCopies(helper, data, worker);
                helper.assertTrue(!LivingWallConfig.ENABLE_LIVING_WALL.get(),
                        "disabled bridge recovery unexpectedly re-enabled construction");
                if (LivingBridgeRouteData.isTerminalLaneState(state.lifecycleState())) {
                    helper.assertTrue(assertGroundedRecoveryIfTerminal(helper, data, worker, state),
                            "disabled bridge recovery reached an unexpected terminal path");
                    restoreFixture(workers, previouslyEnabled);
                    helper.succeed();
                } else if (elapsedTicks >= MAX_RECOVERY_TICKS) {
                    throw new AssertionError("disabled bridge did not complete protected recovery: "
                            + describe(worker, state));
                } else {
                    helper.assertTrue(RECOVERING.equals(state.lifecycleState())
                                    && worker.mob().isNoGravity() && worker.mob().noPhysics,
                            "disabled bridge did not keep an unfinished durable owner protected");
                    awaitDisabledRecovery(helper, data, worker, workers, previouslyEnabled, elapsedTicks + 1);
                }
            } catch (RuntimeException | AssertionError failure) {
                restoreFixture(workers, previouslyEnabled);
                helper.fail(failure.getMessage());
            }
        });
    }

    private static Worker diskJoinWorker(GameTestHelper helper, LivingBridgeRouteData data, List<Worker> workers,
                                         String label, int localX, double height, boolean recovering,
                                         boolean onRoute, boolean lava) {
        return diskJoinWorker(helper, data, workers, label, localX, height, recovering, onRoute, lava, Vec3.ZERO);
    }

    private static Worker diskJoinWorker(GameTestHelper helper, LivingBridgeRouteData data, List<Worker> workers,
                                         String label, int localX, double height, boolean recovering,
                                         boolean onRoute, boolean lava, Vec3 savedPositionOffset) {
        return diskJoinWorker(helper, data, workers, label, localX, height, recovering, onRoute, lava,
                savedPositionOffset, EntityType.ZOMBIE);
    }

    private static Worker diskJoinWorker(GameTestHelper helper, LivingBridgeRouteData data, List<Worker> workers,
                                         String label, int localX, double height, boolean recovering,
                                         boolean onRoute, boolean lava, Vec3 savedPositionOffset,
                                         EntityType<? extends Mob> entityType) {
        return diskJoinWorker(helper, data, workers, label, localX, height, recovering, onRoute, lava,
                savedPositionOffset, entityType, 3);
    }

    private static Worker diskJoinWorker(GameTestHelper helper, LivingBridgeRouteData data, List<Worker> workers,
                                         String label, int localX, double height, boolean recovering,
                                         boolean onRoute, boolean lava, Vec3 savedPositionOffset,
                                         EntityType<? extends Mob> entityType, int localY) {
        ServerLevel level = helper.getLevel();
        BlockPos localFeet = new BlockPos(localX, localY, 4);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 4; z++) {
                helper.setBlock(localFeet.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= Math.ceil(height) + 2; y++) {
                    helper.setBlock(localFeet.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        int ceilingOffset = (int) Math.ceil(height) + 3;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            helper.setBlock(localFeet.offset(x, ceilingOffset, z), Blocks.STONE.defaultBlockState());
        }
        BlockPos feet = helper.absolutePos(localFeet);
        BlockPos obstruction = feet.above(4);
        if (lava) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                helper.setBlock(localFeet.offset(x, 4, z), Blocks.LAVA.defaultBlockState());
            }
        }
        Vec3 landing = Vec3.atBottomCenterOf(feet);
        Vec3 origin = landing.add(0.0D, height, 0.0D);
        List<Vec3> staging = onRoute
                ? List.of(landing.add(0.0D, 3.0D, 0.0D), landing.add(0.0D, 2.0D, 1.0D),
                        landing.add(0.0D, 1.0D, 2.0D), landing.add(0.0D, 0.0D, 3.0D))
                : List.of(landing.add(1.0D, 0.0D, 1.0D), landing.add(1.0D, 0.0D, 2.0D));
        List<BlockPos> temporarySteps = onRoute
                ? List.of(localFeet.above(2), localFeet.offset(0, 1, 1), localFeet.offset(0, 0, 2))
                : List.of();
        for (BlockPos step : temporarySteps) helper.setBlock(step, Blocks.STONE.defaultBlockState());
        Vec3 originalStart = staging.get(0);
        Vec3 originalEndpoint = staging.get(staging.size() - 1);

        Mob original = entityType.create(level);
        helper.assertTrue(original != null, label + " zombie could not be created");
        original.setPos(originalStart.x, originalStart.y, originalStart.z);
        original.setPersistenceRequired();
        original.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        helper.assertTrue(hasFootContact(level, original) && level.noCollision(original, original.getBoundingBox()),
                label + " staging claim must start on loaded, clear support");

        UUID formation = UUID.randomUUID();
        LivingBridgeRouteSnapshot claim = new LivingBridgeRouteSnapshot(UUID.randomUUID(), original.getUUID(),
                formation, level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                staging.stream().map(LivingBridgeOrphanRecoveryGameTests::point).toList(),
                Math.round(original.getBbWidth() * 2048.0D),
                List.of(), 0, point(originalEndpoint),
                original.chunkPosition().x, original.chunkPosition().z);
        helper.assertTrue(data.beginInitialClaim(claim, original), label + " could not commit its route claim");
        for (BlockPos step : temporarySteps) helper.setBlock(step, Blocks.AIR.defaultBlockState());
        if (recovering) {
            Vec3 recoveryStart = onRoute ? origin : originalStart;
            original.setPos(recoveryStart.x, recoveryStart.y, recoveryStart.z);
            LivingBridgeRouteSnapshot transitioning = LivingBridgeRouteData.recoveringSnapshot(claim,
                    sampledRoute(recoveryStart, staging), 1, original.chunkPosition().x, original.chunkPosition().z);
            helper.assertTrue(data.commitRecoveryTransition(claim, transitioning, original),
                    label + " could not journal its transition from staging to recovery");
            claim = transitioning;
        }

        Vec3 savedOrigin = origin.add(savedPositionOffset);
        original.setPos(savedOrigin.x, savedOrigin.y, savedOrigin.z);
        original.setOnGround(true);
        helper.assertTrue(level.isLoaded(feet) && level.isLoaded(BlockPos.containing(origin))
                        && level.noCollision(original, original.getBoundingBox()) && !hasFootContact(level, original),
                label + " fixture requires loaded, collision-free air at its unsupported starting position");
        CompoundTag marker = new CompoundTag();
        marker.putUUID("session", formation);
        marker.putBoolean("originalGravity", true);
        marker.putBoolean("originalNoPhysics", false);
        original.getPersistentData().put(MARKER, marker);
        original.setNoGravity(true);
        original.noPhysics = true;

        CompoundTag savedEntity = new CompoundTag();
        helper.assertTrue(original.save(savedEntity) && savedEntity.getBoolean("OnGround"),
                label + " could not persist the stale ground flag");
        Mob reloaded = (Mob) EntityType.loadEntityRecursive(savedEntity, level, Function.identity());
        helper.assertTrue(reloaded != null && reloaded.onGround() && !hasFootContact(level, reloaded),
                label + " reloaded entity must retain its stale ground flag in free air");
        reloaded.setPersistenceRequired();
        Worker worker = new Worker(label, reloaded, savedOrigin, landing, obstruction, claim);
        workers.add(worker);
        level.addLegacyChunkEntities(Stream.of(reloaded));
        helper.assertTrue(level.getEntity(claim.ownerId()) == reloaded && reloaded.isAlive(),
                label + " entity did not enter the world through the disk join path");
        reloaded.goalSelector.removeAllGoals(goal -> true);
        reloaded.targetSelector.removeAllGoals(goal -> true);
        helper.assertTrue(reloaded.isNoGravity() && reloaded.noPhysics && reloaded.getNavigation().isDone(),
                label + " disk join did not establish the pre-tick ownership hold");
        assertCurrentCopies(helper, data, worker);
        return worker;
    }

    private static void sealRecoveryTube(GameTestHelper helper, int centerX, int centerZ, int height) {
        for (int y = 0; y <= height + 5; y++) {
            for (int offset = -3; offset <= 3; offset++) {
                helper.setBlock(new BlockPos(centerX + offset, y, centerZ - 3), Blocks.STONE.defaultBlockState());
                helper.setBlock(new BlockPos(centerX + offset, y, centerZ + 5), Blocks.STONE.defaultBlockState());
                if (offset > -3 && offset < 3) {
                    helper.setBlock(new BlockPos(centerX - 3, y, centerZ + offset), Blocks.STONE.defaultBlockState());
                    helper.setBlock(new BlockPos(centerX + 3, y, centerZ + offset), Blocks.STONE.defaultBlockState());
                }
            }
        }
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                helper.setBlock(new BlockPos(x, height + 5, z), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static void awaitLongRecovery(GameTestHelper helper, LivingBridgeRouteData data, Worker worker,
                                         List<Worker> workers, boolean previouslyEnabled,
                                         Vec3 previousPosition, int elapsedTicks) {
        awaitLongRecovery(helper, data, worker, workers, previouslyEnabled, previousPosition, elapsedTicks, null);
    }

    private static void awaitLongRecovery(GameTestHelper helper, LivingBridgeRouteData data, Worker worker,
                                         List<Worker> workers, boolean previouslyEnabled,
                                         Vec3 previousPosition, int elapsedTicks, BlockPos expectedLanding) {
        helper.runAfterDelay(1, () -> {
            try {
                LivingBridgeRouteSnapshot snapshot = assertCurrentCopies(helper, data, worker);
                Vec3 position = worker.mob().position();
                if (previousPosition != null) {
                    helper.assertTrue(previousPosition.distanceTo(position) <= MAX_RECOVERY_STEP,
                            "high recovery exceeded its bounded per-tick movement step");
                }
                helper.assertTrue(position.y <= worker.origin().y + FOOT_CONTACT
                                && worker.origin().y - position.y <= elapsedTicks * MAX_RECOVERY_STEP + FOOT_CONTACT,
                        "high recovery left its vertical lane or exceeded its per-tick step bound");
                if (LivingBridgeRouteData.isTerminalLaneState(snapshot.lifecycleState())) {
                    helper.assertTrue(assertGroundedRecoveryIfTerminal(helper, data, worker, snapshot),
                            "high recovery completed without verified ground contact");
                    if (expectedLanding != null) {
                        helper.assertTrue(BlockPos.containing(position).equals(expectedLanding),
                                "recovery completed at an unexpected footprint landing " + position);
                    }
                    restoreFixture(workers, previouslyEnabled);
                    helper.succeed();
                } else if (elapsedTicks >= MAX_LONG_RECOVERY_TICKS) {
                    throw new AssertionError("high orphan recovery did not reach verified ground: "
                            + describe(worker, snapshot));
                } else {
                    helper.assertTrue(RECOVERING.equals(snapshot.lifecycleState())
                                    && worker.mob().isNoGravity() && worker.mob().noPhysics,
                            "high orphan recovery lost durable physics control before reaching ground: "
                                    + describe(worker, snapshot));
                    awaitLongRecovery(helper, data, worker, workers, previouslyEnabled, position, elapsedTicks + 1,
                            expectedLanding);
                }
            } catch (RuntimeException | AssertionError failure) {
                restoreFixture(workers, previouslyEnabled);
                helper.fail(failure.getMessage());
            }
        });
    }

    private static void awaitFootprintRoutePlanned(GameTestHelper helper, ServerLevel level,
                                                    LivingBridgeRouteData data, Worker worker,
                                                    List<Worker> workers, boolean previouslyEnabled,
                                                    Vec3 expectedPosition, BlockPos expectedLanding,
                                                    int attempts) {
        helper.runAfterDelay(1, () -> {
            try {
                invokeRecoveryTick(level, worker.mob());
                LivingBridgeRouteSnapshot snapshot = assertCurrentCopies(helper, data, worker);
                if (!snapshot.safeEndpoint().equals(point(expectedPosition))) {
                    if (attempts >= 24) {
                        throw new AssertionError("displaced orphan did not replan to the verified footprint ledge: "
                                + describe(worker, snapshot));
                    }
                    awaitFootprintRoutePlanned(helper, level, data, worker, workers, previouslyEnabled,
                            expectedPosition, expectedLanding, attempts + 1);
                    return;
                }
                awaitLongRecovery(helper, data, worker, workers, previouslyEnabled,
                        worker.mob().position(), 1, expectedLanding);
            } catch (RuntimeException | AssertionError failure) {
                restoreFixture(workers, previouslyEnabled);
                helper.fail(failure.getMessage());
            }
        });
    }

    private static boolean isStrictOrphanLanding(ServerLevel level, Mob mob, BlockPos feet) {
        try {
            var method = LivingWallCoordinator.class.getDeclaredMethod("isLoadedSafeOrphanLanding",
                    ServerLevel.class, Mob.class, BlockPos.class, Vec3.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, level, mob, feet, Vec3.atBottomCenterOf(feet));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not inspect strict orphan landing rule", failure);
        }
    }

    private static boolean isRecoveryFootprintLanding(ServerLevel level, Mob mob, BlockPos feet, Vec3 position) {
        try {
            var method = LivingWallCoordinator.class.getDeclaredMethod("isLoadedRecoveryFootprintLanding",
                    ServerLevel.class, Mob.class, BlockPos.class, Vec3.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, level, mob, feet, position);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not inspect recovery footprint landing rule", failure);
        }
    }

    private static void invokeRecoveryTick(ServerLevel level, Mob mob) {
        try {
            var recovery = LivingWallCoordinator.class.getDeclaredMethod(
                    "recoverStaleControlledMob", ServerLevel.class, Mob.class, String.class);
            recovery.setAccessible(true);
            recovery.invoke(null, level, mob, "gametest_long_recovery");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not invoke the durable recovery tick", failure);
        }
    }

    private static Worker diskJoinSameBlockAirborne(GameTestHelper helper, LivingBridgeRouteData data,
                                                     List<Worker> workers) {
        ServerLevel level = helper.getLevel();
        BlockPos localFeet = new BlockPos(3, 3, 16);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 3; z++) {
            helper.setBlock(localFeet.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 4; y++) helper.setBlock(localFeet.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        BlockPos feet = helper.absolutePos(localFeet);
        Vec3 landing = Vec3.atBottomCenterOf(feet);
        Vec3 lifted = landing.add(0.0D, 0.04D, 0.0D);
        Mob original = EntityType.ZOMBIE.create(level);
        helper.assertTrue(original != null, "same-block airborne zombie could not be created");
        original.setPos(landing.x, landing.y, landing.z);
        helper.assertTrue(hasFootContact(level, original), "same-block case must begin with real support");
        UUID formation = UUID.randomUUID();
        LivingBridgeRouteSnapshot claim = new LivingBridgeRouteSnapshot(UUID.randomUUID(), original.getUUID(),
                formation, level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(point(landing.add(-1.0D, 0.0D, 0.0D)), point(landing)),
                Math.round(original.getBbWidth() * 2048.0D), List.of(), 0, point(landing),
                original.chunkPosition().x, original.chunkPosition().z);
        helper.assertTrue(data.beginInitialClaim(claim, original), "same-block case could not commit staging claim");
        LivingBridgeRouteSnapshot recovering = LivingBridgeRouteData.recoveringSnapshot(claim,
                List.of(point(lifted), point(landing)), 2, original.chunkPosition().x, original.chunkPosition().z);
        helper.assertTrue(data.commitRecoveryTransition(claim, recovering, original),
                "same-block case could not journal its recovery cursor");
        original.setPos(lifted.x, lifted.y, lifted.z);
        original.setOnGround(true);
        CompoundTag marker = new CompoundTag();
        marker.putUUID("session", formation);
        marker.putBoolean("originalGravity", true);
        marker.putBoolean("originalNoPhysics", false);
        original.getPersistentData().put(MARKER, marker);
        original.setNoGravity(true);
        original.noPhysics = true;
        CompoundTag savedEntity = new CompoundTag();
        helper.assertTrue(original.save(savedEntity), "same-block entity did not serialize");
        Mob reloaded = (Mob) EntityType.loadEntityRecursive(savedEntity, level, Function.identity());
        helper.assertTrue(reloaded != null && reloaded.blockPosition().equals(feet)
                        && !hasFootContact(level, reloaded),
                "same-block entity must reload unsupported within the endpoint block");
        Worker worker = new Worker("same-block airborne", reloaded, lifted, landing, feet.above(4), recovering);
        workers.add(worker);
        level.addLegacyChunkEntities(Stream.of(reloaded));
        reloaded.goalSelector.removeAllGoals(goal -> true);
        reloaded.targetSelector.removeAllGoals(goal -> true);
        assertCurrentCopies(helper, data, worker);
        return worker;
    }

    private static void assertOffCenterOrdinaryRecoveryCompletes(GameTestHelper helper,
                                                                  LivingBridgeRouteData data) {
        ServerLevel level = helper.getLevel();
        BlockPos localFeet = new BlockPos(10, 3, 18);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            helper.setBlock(localFeet.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 3; y++) helper.setBlock(localFeet.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        BlockPos feet = helper.absolutePos(localFeet);
        Vec3 endpoint = Vec3.atBottomCenterOf(feet);
        Vec3 routeStart = endpoint.add(-1.0D, 0.0D, 0.0D);
        Vec3 offCenter = endpoint.add(0.2D, 0.0D, 0.0D);
        Mob mob = EntityType.ZOMBIE.create(level);
        helper.assertTrue(mob != null, "off-center ordinary recovery zombie could not be created");
        mob.setPos(routeStart.x, routeStart.y, routeStart.z);
        UUID formation = UUID.randomUUID();
        LivingBridgeRouteSnapshot claim = new LivingBridgeRouteSnapshot(UUID.randomUUID(), mob.getUUID(),
                formation, level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(point(routeStart), point(endpoint)), Math.round(mob.getBbWidth() * 2048.0D),
                List.of(), 0, point(endpoint), mob.chunkPosition().x, mob.chunkPosition().z);
        helper.assertTrue(data.beginInitialClaim(claim, mob), "off-center ordinary recovery claim failed");
        LivingBridgeRouteSnapshot recovering = LivingBridgeRouteData.recoveringSnapshot(claim,
                List.of(point(routeStart), point(endpoint)), 2, mob.chunkPosition().x, mob.chunkPosition().z);
        helper.assertTrue(data.commitRecoveryTransition(claim, recovering, mob),
                "off-center ordinary recovery route could not be journaled");
        mob.setPos(offCenter.x, offCenter.y, offCenter.z);
        mob.setNoGravity(true);
        mob.noPhysics = true;
        helper.assertTrue(hasFootContact(level, mob) && mob.blockPosition().equals(feet),
                "ordinary recovery control must stand off-center with real support in the endpoint block");
        try {
            var complete = LivingWallCoordinator.class.getDeclaredMethod("completeSafeRecoveryAtPersistedEndpoint",
                    ServerLevel.class, Mob.class, LivingBridgeRouteData.class);
            complete.setAccessible(true);
            helper.assertTrue((Boolean) complete.invoke(null, level, mob, data),
                    "grounded off-center ordinary recovery was not completed");
            LivingBridgeRouteSnapshot terminal = data.snapshot(claim.reservationId());
            helper.assertTrue(terminal != null && COMPLETE.equals(terminal.lifecycleState())
                            && !mob.isNoGravity() && !mob.noPhysics,
                    "off-center ordinary recovery did not commit completion and restore physics");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not run the ordinary endpoint compatibility check", failure);
        } finally {
            mob.discard();
        }
    }

    private static void assertOrphanEndpointIsRevalidated(GameTestHelper helper, LivingBridgeRouteData data) {
        ServerLevel level = helper.getLevel();
        BlockPos localFeet = new BlockPos(18, 3, 18);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            helper.setBlock(localFeet.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 3; y++) helper.setBlock(localFeet.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        BlockPos feet = helper.absolutePos(localFeet);
        Vec3 endpoint = Vec3.atBottomCenterOf(feet);
        Vec3 routeStart = endpoint.add(-0.5D, 0.0D, 0.0D);
        Mob mob = EntityType.ZOMBIE.create(level);
        helper.assertTrue(mob != null, "orphan endpoint revalidation zombie could not be created");
        mob.setPos(routeStart.x, routeStart.y, routeStart.z);
        UUID formation = UUID.randomUUID();
        LivingBridgeRouteSnapshot claim = new LivingBridgeRouteSnapshot(UUID.randomUUID(), mob.getUUID(),
                formation, level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(point(routeStart), point(endpoint)), Math.round(mob.getBbWidth() * 2048.0D),
                List.of(), 0, point(endpoint), mob.chunkPosition().x, mob.chunkPosition().z);
        helper.assertTrue(data.beginInitialClaim(claim, mob), "orphan endpoint revalidation claim failed");
        LivingBridgeRouteSnapshot recovering = LivingBridgeRouteData.orphanRecoveringSnapshot(claim,
                List.of(point(routeStart), point(endpoint)), 2, point(endpoint),
                mob.chunkPosition().x, mob.chunkPosition().z);
        helper.assertTrue(data.commitRecoveryTransition(claim, recovering, mob),
                "orphan endpoint revalidation route could not be journaled");
        mob.setPos(endpoint.x, endpoint.y, endpoint.z);
        mob.setNoGravity(true);
        mob.noPhysics = true;
        helper.assertTrue(hasFootContact(level, mob), "orphan endpoint control must have physical support");
        helper.setBlock(localFeet, Blocks.FIRE.defaultBlockState());
        helper.assertTrue(level.getBlockState(feet).is(Blocks.FIRE),
                "orphan endpoint fixture fire must remain present after placement");
        try {
            var complete = LivingWallCoordinator.class.getDeclaredMethod("completeSafeRecoveryAtPersistedEndpoint",
                    ServerLevel.class, Mob.class, LivingBridgeRouteData.class);
            complete.setAccessible(true);
            helper.assertTrue(!(Boolean) complete.invoke(null, level, mob, data),
                    "orphan terminal cursor bypassed revalidation after fire appeared at its endpoint");
            helper.assertTrue(recovering.exactlyEquals(data.snapshot(claim.reservationId()))
                            && mob.isNoGravity() && mob.noPhysics,
                    "rejected orphan endpoint changed its durable owner or physics hold");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not run the orphan endpoint revalidation check", failure);
        } finally {
            mob.discard();
        }
    }

    private static void awaitRecovery(GameTestHelper helper, LivingBridgeRouteData data, List<Worker> workers,
                                      Worker walking, Worker recovering, Worker recoveringWithPositionDrift,
                                      Worker airborne, Worker blocked,
                                      Worker nearEndpoint, Map<UUID, Vec3> previousPositions,
                                      boolean previouslyEnabled, int elapsedTicks) {
        helper.runAfterDelay(1, () -> {
            try {
                LivingBridgeRouteSnapshot walkingState = assertCurrentCopies(helper, data, walking);
                LivingBridgeRouteSnapshot recoveringState = assertCurrentCopies(helper, data, recovering);
                LivingBridgeRouteSnapshot driftedRecoveryState = assertCurrentCopies(helper, data, recoveringWithPositionDrift);
                LivingBridgeRouteSnapshot airborneState = assertCurrentCopies(helper, data, airborne);
                LivingBridgeRouteSnapshot blockedState = assertCurrentCopies(helper, data, blocked);
                LivingBridgeRouteSnapshot nearEndpointState = assertCurrentCopies(helper, data, nearEndpoint);
                for (Worker displaced : List.of(walking, recovering, recoveringWithPositionDrift)) {
                    LivingBridgeRouteSnapshot displacedState = displaced == walking ? walkingState
                            : displaced == recovering ? recoveringState : driftedRecoveryState;
                    if (LivingBridgeRouteData.isTerminalLaneState(displacedState.lifecycleState())) continue;
                    Vec3 previous = previousPositions.put(displaced.mob().getUUID(), displaced.mob().position());
                    if (previous != null) {
                        helper.assertTrue(previous.distanceTo(displaced.mob().position()) <= MAX_RECOVERY_STEP,
                                displaced.label() + " moved more than one recovery step in a server tick");
                    }
                    double dropped = displaced.origin().y - displaced.mob().getY();
                    double maxDrop = elapsedTicks * MAX_RECOVERY_STEP + FOOT_CONTACT;
                    double horizontalTolerance = displaced.label().equals("recovering with position drift")
                            ? 0.01D : 1.0D / 4096.0D;
                    helper.assertTrue(Math.abs(displaced.mob().getX() - displaced.origin().x) <= horizontalTolerance
                                    && Math.abs(displaced.mob().getZ() - displaced.origin().z) <= horizontalTolerance
                                    && dropped >= -FOOT_CONTACT && dropped <= maxDrop,
                            displaced.label() + " recovery left its bounded vertical lane or exceeded its per-tick step: "
                                    + displaced.mob().position() + " after " + elapsedTicks + " ticks");
                }
                helper.assertTrue(!LivingBridgeRouteData.isTerminalLaneState(blockedState.lifecycleState())
                                && blocked.mob().isNoGravity() && blocked.mob().noPhysics
                                && blocked.mob().position().distanceToSqr(blocked.origin()) < 1.0E-8D
                                && blocked.claim().exactlyEquals(blockedState)
                                && helper.getLevel().getFluidState(blocked.obstruction()).is(FluidTags.LAVA)
                                && !blocked.mob().isInLava(),
                        blocked.label() + " owner left its hold despite lava below it: " + blocked.mob().position());
                helper.assertTrue(airborne.claim().safeEndpoint().equals(airborneState.safeEndpoint())
                                && airborne.claim().corridor().equals(airborneState.corridor())
                                && airborne.claim().recoveryRoute().equals(airborneState.recoveryRoute()),
                        airborne.label() + " owner was diverted from its certified recovery route");
                if (!hasFootContact(helper.getLevel(), airborne.mob())) {
                    helper.assertTrue(RECOVERING.equals(airborneState.lifecycleState())
                                    && airborne.mob().isNoGravity() && airborne.mob().noPhysics
                                    && airborne.mob().getY() <= airborne.origin().y + FOOT_CONTACT,
                            airborne.label() + " owner lost valid recovery control before reaching solid support");
                }
                boolean walkingRecovered = assertGroundedRecoveryIfTerminal(helper, data, walking, walkingState);
                boolean recoveringRecovered = assertGroundedRecoveryIfTerminal(helper, data, recovering, recoveringState);
                boolean driftedRecoveryRecovered = assertGroundedRecoveryIfTerminal(helper, data,
                        recoveringWithPositionDrift, driftedRecoveryState);
                boolean sameBlockRecovered = assertGroundedRecoveryIfTerminal(helper, data, nearEndpoint, nearEndpointState);
                boolean walkingSafelyBlockedByWater = elapsedTicks >= MAX_RECOVERY_TICKS
                        && RECOVERING.equals(walkingState.lifecycleState()) && walking.mob().isNoGravity()
                        && walking.mob().noPhysics
                        && helper.getLevel().getFluidState(walking.mob().blockPosition().below()).is(FluidTags.WATER);
                if ((walkingRecovered || walkingSafelyBlockedByWater) && recoveringRecovered
                        && driftedRecoveryRecovered && sameBlockRecovered) {
                    restoreFixture(workers, previouslyEnabled);
                    helper.succeed();
                } else if (elapsedTicks >= MAX_RECOVERY_TICKS) {
                    throw new AssertionError("displaced durable owners did not reach verified ground within "
                            + elapsedTicks + " ticks: " + describe(walking, walkingState) + ", "
                            + describe(recovering, recoveringState) + ", "
                            + describe(recoveringWithPositionDrift, driftedRecoveryState) + ", "
                            + describe(nearEndpoint, nearEndpointState));
                } else {
                    awaitRecovery(helper, data, workers, walking, recovering, recoveringWithPositionDrift,
                            airborne, blocked, nearEndpoint, previousPositions, previouslyEnabled, elapsedTicks + 1);
                }
            } catch (RuntimeException | AssertionError failure) {
                restoreFixture(workers, previouslyEnabled);
                helper.fail(failure.getMessage());
            }
        });
    }

    private static LivingBridgeRouteSnapshot assertCurrentCopies(GameTestHelper helper, LivingBridgeRouteData data,
                                                                 Worker worker) {
        Mob mob = worker.mob();
        LivingBridgeRouteSnapshot saved = data.snapshot(worker.claim().reservationId());
        boolean completed = saved != null && LivingBridgeRouteData.isTerminalLaneState(saved.lifecycleState());
        helper.assertTrue((mob.isAlive() || completed) && saved != null && saved.ownerId().equals(mob.getUUID())
                        && !saved.originalNoGravity() && !saved.originalNoPhysics(),
                worker.label() + " ownership, entity lifetime, or captured original physics changed: alive="
                        + mob.isAlive() + " saved=" + saved + " mob=" + mob.getUUID()
                        + " noGravity=" + mob.isNoGravity() + " noPhysics=" + mob.noPhysics);
        CompoundTag root = mob.getPersistentData();
        if (!LivingBridgeRouteData.isTerminalLaneState(saved.lifecycleState()) || root.contains(CONTROL)) {
            CompoundTag capsule = root.getCompound(CONTROL);
            helper.assertTrue(capsule.getInt("schema") == LivingBridgeRouteData.CURRENT_SCHEMA
                            && saved.digest().equals(capsule.getString("snapshotDigest"))
                            && saved.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(capsule.getCompound("snapshot"))),
                    worker.label() + " entity capsule diverged from the durable route");
        }
        return saved;
    }

    private static boolean assertGroundedRecoveryIfTerminal(GameTestHelper helper, LivingBridgeRouteData data,
                                                            Worker worker, LivingBridgeRouteSnapshot state) {
        if (!LivingBridgeRouteData.isTerminalLaneState(state.lifecycleState())) return false;
        helper.assertTrue(COMPLETE.equals(state.lifecycleState()),
                worker.label() + " owner left recovery via " + state.lifecycleState());
        Mob mob = worker.mob();
        ServerLevel level = helper.getLevel();
        LivingBridgeRouteSnapshot.Point endpoint = state.safeEndpoint();
        Vec3 endpointPosition = endpoint == null ? null : new Vec3(endpoint.x() / 4096.0D,
                endpoint.y() / 4096.0D, endpoint.z() / 4096.0D);
        helper.assertTrue(endpointPosition != null && !state.recoveryRequired(),
                worker.label() + " terminal recovery did not persist its verified landing endpoint");
        if (mob.isAlive()) {
            helper.assertTrue(hasFootContact(level, mob) && level.noCollision(mob, mob.getBoundingBox())
                            && mob.getY() <= worker.landing().y + FOOT_CONTACT && mob.getY() < worker.origin().y
                            && !mob.isNoGravity() && !mob.noPhysics
                            && endpointPosition.distanceToSqr(mob.position()) <= 0.25D,
                    worker.label() + " terminal recovery did not restore normal physics at its persisted landing: "
                            + mob.position() + " endpoint=" + endpoint);
        }
        boolean journalMatches = data.save(new CompoundTag(), null).getList("transitionJournal", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast)
                .anyMatch(row -> row.hasUUID("reservationId") && state.reservationId().equals(row.getUUID("reservationId"))
                        && row.hasUUID("transactionId") && state.transactionId().equals(row.getUUID("transactionId"))
                        && row.getBoolean("hasFrom") && state.commitGeneration() > worker.claim().commitGeneration()
                        && state.commitGeneration() == row.getLong("commitGeneration")
                        && state.digest().equals(row.getString("toDigest"))
                        && state.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(row.getCompound("to"))));
        helper.assertTrue(journalMatches, worker.label() + " terminal recovery was not committed to the route journal");
        return true;
    }

    private static boolean hasFootContact(ServerLevel level, Mob mob) {
        return level.getBlockCollisions(mob, mob.getBoundingBox().move(0.0D, -FOOT_CONTACT, 0.0D)).iterator().hasNext();
    }

    private static LivingBridgeRouteSnapshot.Point point(Vec3 position) {
        return LivingBridgeRouteSnapshot.Point.from(position.x, position.y, position.z);
    }

    private static List<LivingBridgeRouteSnapshot.Point> sampledRoute(Vec3 start, List<Vec3> staging) {
        List<LivingBridgeRouteSnapshot.Point> route = new ArrayList<>();
        route.add(point(start));
        Vec3 from = start;
        for (int node = 1; node < staging.size(); node++) {
            Vec3 to = staging.get(node);
            int steps = (int) Math.ceil(from.distanceTo(to) / 0.18D);
            for (int step = 1; step <= steps; step++) {
                route.add(point(from.lerp(to, (double) step / steps)));
            }
            from = to;
        }
        return route;
    }

    private static String describe(Worker worker, LivingBridgeRouteSnapshot snapshot) {
        Mob mob = worker.mob();
        int index = snapshot.recoveryIndex();
        LivingBridgeRouteSnapshot.Point next = index >= 0 && index < snapshot.recoveryRoute().size()
                ? snapshot.recoveryRoute().get(index) : null;
        BlockPos currentBlock = mob.blockPosition();
        BlockPos nextBlock = next == null ? currentBlock : BlockPos.containing(
                next.x() / 4096.0D, next.y() / 4096.0D, next.z() / 4096.0D);
        return worker.label() + " pos=" + mob.position() + " state=" + snapshot.lifecycleState()
                + " mode=" + snapshot.movementMode()
                + " recovery=" + index + "/" + snapshot.recoveryRoute().size() + " next=" + next
                + " blocks=" + mob.level().getBlockState(currentBlock.below()) + "/"
                + mob.level().getBlockState(currentBlock) + "/" + mob.level().getBlockState(currentBlock.above())
                + " nextBlock=" + mob.level().getBlockState(nextBlock)
                + " noGravity=" + mob.isNoGravity() + " noPhysics=" + mob.noPhysics;
    }

    private static void restoreFixture(List<Worker> workers, boolean previouslyEnabled) {
        for (Worker worker : workers) worker.mob().discard();
        LivingWallConfig.ENABLE_LIVING_WALL.set(previouslyEnabled);
    }

    private record Worker(String label, Mob mob, Vec3 origin, Vec3 landing, BlockPos obstruction,
                          LivingBridgeRouteSnapshot claim) {
    }
}
