package com.example.soundattract.gametest;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.client.AlertAnimationFrames;
import com.example.soundattract.client.AwarenessClientState;
import com.example.soundattract.event.ArrowInvestigationEvents;
import com.example.soundattract.util.ArrowInvestigationHelper;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.example.soundattract.pathfinding.HazardClassifier;
import com.example.soundattract.pathfinding.FlowFieldManager;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.Locale;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.UUID;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** In-world regression checks for gameplay systems that can be asserted headlessly. */
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public final class CoreMechanicsGameTests {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface CoreTest { int timeoutTicks(); String template() default "core_mechanics"; }
    private static final String TEMPLATE = "core_mechanics";

    private CoreMechanicsGameTests() {}

    @CoreTest(timeoutTicks = 20)
    public static void alertAnimationUsesStableWrappingFrames(GameTestHelper helper) {
        for (int frame = 0; frame < 8; frame++) {
            long atFrame = frame * 1800L / 8;
            if (AlertAnimationFrames.frameIndex(atFrame, 1800L, 8) != frame) {
                helper.fail("Eye atlas selected the wrong frame at " + atFrame + "ms");
                return;
            }
        }
        if (AlertAnimationFrames.frameIndex(1800L, 1800L, 8) != 0
                || AlertAnimationFrames.frameIndex(-1L, 1800L, 8) != 7) {
            helper.fail("Eye atlas did not wrap cleanly at the animation boundary");
            return;
        }
        for (String texture : List.of("assets/soundattract/textures/mob_alert/detected.png",
                "assets/soundattract/textures/mob_alert/proximity.png")) {
            if (!eyeAtlasFramesStayCentered(texture)) {
                helper.fail("Eye atlas frames are not centered consistently: " + texture);
                return;
            }
        }
        for (String animation : List.of("detected", "proximity", "near_miss", "scent", "raid")) {
            if (!manualAnimationMetadataIsValid("assets/soundattract/textures/mob_alert/" + animation + ".png.mcmeta")) {
                helper.fail("Manually selected alert frames still have conflicting metadata: " + animation);
                return;
            }
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void arrowNearMissSweepCoversSkippedProjectileTicks(GameTestHelper helper) {
        if (!ArrowInvestigationHelper.shouldScanProjectileSegment(false, true)
                || ArrowInvestigationHelper.shouldScanProjectileSegment(false, false)
                || !ArrowInvestigationHelper.shouldScanProjectileSegment(true, false)) {
            helper.fail("An arrow segment near an eligible mob must be scanned even when far from players");
            return;
        }
        Vec3 lastSample = new Vec3(0.0, 64.0, 0.0);
        Vec3 current = new Vec3(12.0, 64.0, 0.0);
        Vec3 mobCenter = new Vec3(6.0, 66.9, 0.0);
        double radiusSquared = 3.0 * 3.0;
        double fullSweepDistance = ArrowInvestigationHelper.segmentToPointSquaredDistance(lastSample, current, mobCenter);
        double incorrectlyShortenedDistance = ArrowInvestigationHelper.segmentToPointSquaredDistance(
                new Vec3(9.0, 64.0, 0.0), current, mobCenter);
        if (fullSweepDistance > radiusSquared || incorrectlyShortenedDistance <= radiusSquared) {
            helper.fail("The regression geometry must intersect only when the last sampled flight segment is retained");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void trackedProjectileNearMissFindsSoundPursuerWithoutNearbyPlayer(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        zombie.goalSelector.addGoal(0, new AttractionGoal(zombie, 1.0));
        AbstractArrow arrow = null;
        long now = helper.getLevel().getGameTime();
        for (int attempt = 0; attempt < 4; attempt++) {
            AbstractArrow candidate = (AbstractArrow) EntityType.ARROW.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (candidate == null) break;
            candidate.setNoGravity(true);
            BlockPos arrowStart = helper.absolutePos(new BlockPos(0, 10, 4));
            candidate.setPos(arrowStart.getX() + 0.5, arrowStart.getY(), arrowStart.getZ() + 0.5);
            candidate.setDeltaMovement(1.0, 0.0, 0.0);
            if (!helper.getLevel().addFreshEntity(candidate)
                    || !ArrowInvestigationEvents.ARROWS.containsKey(candidate.getId())) {
                helper.fail("The watched projectile was not registered by the server join event");
                return;
            }
            if ((candidate.getId() + (int) now) % 4 == 0) {
                arrow = candidate;
                break;
            }
            candidate.discard();
            ArrowInvestigationEvents.ARROWS.remove(candidate.getId());
        }
        if (arrow == null) {
            helper.fail("Could not align the tracked arrow with the server's four-tick sweep interval");
            return;
        }
        BlockPos arrowEnd = helper.absolutePos(new BlockPos(4, 10, 4));
        arrow.setPos(arrowEnd.getX() + 0.5, arrowEnd.getY(), arrowEnd.getZ() + 0.5);
        new ArrowInvestigationEvents().onServerTick(new LevelTickEvent.Post(() -> true, helper.getLevel()));
        ArrowInvestigationEvents.ArrowState state = ArrowInvestigationEvents.ARROWS.get(arrow.getId());
        if (state != null && state.notifiedMobIds.contains(zombie.getUUID())) {
            String dimension = helper.getLevel().dimension().identifier().toString();
            boolean investigationSoundAdded = SoundTracker.getNearbySounds(helper.getLevel(), dimension,
                            zombie.blockPosition(), null).stream()
                    .anyMatch(record -> record instanceof SoundTracker.VirtualSoundRecord virtual
                            && "arrow_investigation".equals(virtual.animationClass));
            arrow.discard();
            if (!investigationSoundAdded) {
                helper.fail("The near-miss sweep found the pursuer but did not dispatch its investigation sound");
                return;
            }
            helper.succeed();
            return;
        }
        arrow.discard();
        helper.fail("The scheduled sweep did not alert the pursuer or dispatch its investigation sound");
    }

    private static boolean manualAnimationMetadataIsValid(String resource) {
        try (var input = CoreMechanicsGameTests.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return false;
            String metadata = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return metadata.contains("\"texture\"") && !metadata.contains("\"animation\"");
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean eyeAtlasFramesStayCentered(String resource) {
        try (var input = CoreMechanicsGameTests.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return false;
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() != 64 || image.getHeight() != 512) return false;
            for (int frame = 0; frame < 8; frame++) {
                double alphaTotal = 0.0;
                double xTotal = 0.0;
                double yTotal = 0.0;
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int alpha = image.getRGB(x, frame * 64 + y) >>> 24;
                        alphaTotal += alpha;
                        xTotal += x * alpha;
                        yTotal += y * alpha;
                    }
                }
                if (alphaTotal == 0.0) continue; // Proximity intentionally starts with an empty frame.
                double centerX = xTotal / alphaTotal;
                double centerY = yTotal / alphaTotal;
                if (Math.abs(centerX - 31.5) > 1.0 || Math.abs(centerY - 31.5) > 1.0) return false;
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @CoreTest(timeoutTicks = 20)
    public static void awarenessAlertsSurviveSnapshotGapsAndRestartForNewEvents(GameTestHelper helper) {
        UUID mobId = UUID.randomUUID();
        try {
            AwarenessClientState.clear();
            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 2, 0xFFFF3030, 1.0f))), 1_000L);
            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false, List.of()), 1_250L);
            AwarenessClientState.MobAlert retained = AwarenessClientState.alert(mobId);
            if (retained == null || retained.startedAt() != 1_000L) {
                helper.fail("A one-snapshot gap removed or restarted the eye animation");
                return;
            }

            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 2, 0xFFFF3030, 1.0f))), 1_500L);
            if (AwarenessClientState.alert(mobId).startedAt() != 1_000L) {
                helper.fail("The same alert event restarted after a missing snapshot");
                return;
            }

            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 3, 0xFFFFB000, 0.0f, 1L))), 1_600L);
            if (AwarenessClientState.alert(mobId).startedAt() != 1_600L) {
                helper.fail("A new near-miss event did not start its animation");
                return;
            }
            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 3, 0xFFFFB000, 0.0f, 2L))), 1_700L);
            if (AwarenessClientState.alert(mobId).startedAt() != 1_700L) {
                helper.fail("A repeated near-miss event did not restart its animation");
                return;
            }

            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false, List.of()), 2_451L);
            if (AwarenessClientState.alert(mobId) != null) {
                helper.fail("An alert remained after the missing-snapshot grace period");
                return;
            }
            helper.succeed();
        } finally {
            AwarenessClientState.clear();
        }
    }

    @CoreTest(timeoutTicks = 20)
    public static void awarenessSnapshotCodecPreservesTriggerIdsAndReadsLegacyPackets(GameTestHelper helper) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf legacy = Unpooled.buffer();
        try {
            UUID mobId = UUID.randomUUID();
            AwarenessSnapshotMessage message = new AwarenessSnapshotMessage(0.5f, 0.25f, 2, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 3, 0xFFFFB000, 0.0f, 1234L)));
            AwarenessSnapshotMessage.STREAM_CODEC.encode(encoded, message);
            AwarenessSnapshotMessage decoded = AwarenessSnapshotMessage.STREAM_CODEC.decode(encoded);
            if (decoded.alerts().size() != 1 || decoded.alerts().get(0).triggerId() != 1234L) {
                helper.fail("New awareness packet did not preserve the near-miss trigger ID");
                return;
            }

            legacy.writeFloat(0.5f);
            legacy.writeFloat(0.25f);
            legacy.writeByte(2);
            legacy.writeBoolean(false);
            legacy.writeByte(1); // one alert
            legacy.writeLong(mobId.getMostSignificantBits());
            legacy.writeLong(mobId.getLeastSignificantBits());
            legacy.writeByte(42); // entity ID varint
            legacy.writeByte(3); // alert type
            legacy.writeInt(0xFFFFB000);
            legacy.writeFloat(0.0f);
            AwarenessSnapshotMessage legacyDecoded = AwarenessSnapshotMessage.STREAM_CODEC.decode(legacy);
            if (legacyDecoded.alerts().size() != 1 || legacyDecoded.alerts().get(0).triggerId() != 0L) {
                helper.fail("Legacy awareness packet did not decode with the default trigger ID");
                return;
            }
            helper.succeed();
        } finally {
            encoded.release();
            legacy.release();
        }
    }

    @CoreTest(timeoutTicks = 20)
    public static void livingBridgeTiltOnlyAllowsItsLandingSupport(GameTestHelper helper) {
        BlockPos landingFeet = helper.absolutePos(new BlockPos(5, 10, 5));
        if (!LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, landingFeet.below())) {
            helper.fail("The block directly supporting the final landing was rejected");
            return;
        }

        BlockPos wallFace = landingFeet.west();
        BlockPos ledgeAbove = landingFeet.above();
        BlockPos nearbyBlock = landingFeet.offset(1, -2, 1);
        if (LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, wallFace)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, ledgeAbove)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, nearbyBlock)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(null, landingFeet)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, null)) {
            helper.fail("A wall, ledge, or nearby scanned obstacle was incorrectly exempted from tilt collision checks");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void livingBridgeFoundationUsesAdmittedLandingSupport(GameTestHelper helper) {
        BlockPos landingFeet = new BlockPos(5, 9, 5);
        BlockPos plannedPivot = new BlockPos(5, 9, 4);
        helper.setBlock(landingFeet.below(), Blocks.STONE);
        helper.setBlock(plannedPivot.below(), Blocks.AIR);

        Vec3 admittedTarget = Vec3.atBottomCenterOf(helper.absolutePos(landingFeet));
        Vec3 plannedTarget = Vec3.atBottomCenterOf(helper.absolutePos(plannedPivot));
        Vec3 supportTarget = LivingWallCoordinator.foundationSupportTarget(
                java.util.Map.of(), 0, 0, admittedTarget, plannedTarget);
        if (!supportTarget.equals(admittedTarget)
                || !LivingWallCoordinator.hasFoundationTerrainSupport(helper.getLevel(), supportTarget)) {
            helper.fail("Foundation preflight did not use the recruited landing cell as its support target");
            return;
        }
        if (LivingWallCoordinator.hasFoundationTerrainSupport(helper.getLevel(), plannedTarget)) {
            helper.fail("The test pivot unexpectedly has terrain support");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void livingBridgeUpperLayerRepairPreservesFoundationSlot(GameTestHelper helper) {
        java.util.Map<Integer, Vec3> structuralSlots = new java.util.HashMap<>();
        java.util.Map<Integer, Vec3> articulatedRoutePositions = new java.util.HashMap<>();
        Vec3 foundation = new Vec3(10.5D, 63.0D, 10.5D);
        Vec3 upperBend = new Vec3(12.5D, 67.0D, 12.5D);
        Vec3 upperPillarLayer = new Vec3(10.5D, 76.65D, 10.5D);
        structuralSlots.put(0, foundation);

        LivingWallCoordinator.putArticulatedSlotPosition(articulatedRoutePositions, 7, upperBend);
        Vec3 retainedFoundation = LivingWallCoordinator.foundationSupportTarget(
                structuralSlots, 0, -1, null, Vec3.ZERO);
        Vec3 assemblyLayer = LivingWallCoordinator.structuralSlotPositionForTest(
                structuralSlots, 7, upperPillarLayer);
        if (!foundation.equals(retainedFoundation) || !upperPillarLayer.equals(assemblyLayer)
                || structuralSlots.containsKey(7) || !upperBend.equals(articulatedRoutePositions.get(7))) {
            helper.fail("An articulated route bend replaced a required upright assembly layer");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void livingBridgeArticulatedRepairKeepsPillarBeforeTilt(GameTestHelper helper) {
        Vec3 pillarLayer = new Vec3(10.5D, 64.95D, 10.5D);
        Vec3 articulatedBridgePosition = new Vec3(12.5D, 67.0D, 12.5D);
        Vec3 atPillarStage = LivingWallCoordinator.interpolateFormationSlotPosition(
                pillarLayer, articulatedBridgePosition, 0.0D);
        Vec3 duringTilt = LivingWallCoordinator.interpolateFormationSlotPosition(
                pillarLayer, articulatedBridgePosition, 0.5D);
        Vec3 afterTilt = LivingWallCoordinator.interpolateFormationSlotPosition(
                pillarLayer, articulatedBridgePosition, 1.0D);
        if (!atPillarStage.equals(pillarLayer)
                || duringTilt.equals(pillarLayer) || duringTilt.equals(articulatedBridgePosition)
                || !afterTilt.equals(articulatedBridgePosition)) {
            helper.fail("An articulated bridge slot did not remain in the pillar until tilt progress began");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void livingBridgeDirectSpanKeepsFoundationAnchored(GameTestHelper helper) {
        Vec3 pivot = new Vec3(10.5D, 64.0D, 10.5D);
        Vec3 stagedFoundation = new Vec3(12.5D, 64.0D, 10.5D);
        Vec3 routeBase = LivingWallCoordinator.preserveSourceFoundation(
                pivot, pivot, stagedFoundation, 0.0D);
        Vec3 routeEnd = new Vec3(14.5D, 62.0D, 10.5D);
        Vec3 convergedEnd = LivingWallCoordinator.preserveSourceFoundation(
                routeEnd, pivot, stagedFoundation, 1.0D);
        Vec3 duringSpan = LivingWallCoordinator.interpolateFormationSlotPosition(
                stagedFoundation, routeBase, 0.5D);
        if (!routeBase.equals(stagedFoundation) || !convergedEnd.equals(routeEnd)
                || !duringSpan.equals(stagedFoundation)) {
            helper.fail("A direct span moved its staged foundation or failed to converge onto its planned endpoint");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void livingBridgeMovementStopsAtBlocksAndAllowsDetour(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(4, 9, 2), Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 9, 2));
        Vec3 start = zombie.position();
        Vec3 acrossWall = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 9, 2)));
        Vec3 sideStep = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 9, 4)));
        Vec3 pastWall = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 9, 4)));

        try {
            if (LivingWallCoordinator.bridgeMovementClearForTest(helper.getLevel(), zombie, start, acrossWall)) {
                helper.fail("A structural bridge move swept through a solid block");
                return;
            }
            if (!LivingWallCoordinator.bridgeMovementClearForTest(helper.getLevel(), zombie, start, sideStep)
                    || !LivingWallCoordinator.bridgeMovementClearForTest(helper.getLevel(), zombie, sideStep, pastWall)
                    || !LivingWallCoordinator.bridgeMovementClearForTest(helper.getLevel(), zombie, pastWall, acrossWall)) {
                helper.fail("The movement clearance check rejected a clear route around the obstacle");
                return;
            }
        } catch (Throwable error) {
            helper.fail("Movement collision GameTest threw " + error.getClass().getName() + ": " + error.getMessage());
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void zombieAttractionGoalAcquiresTrackedSound(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        BlockPos source = helper.absolutePos(new BlockPos(7, 9, 2));
        String dimension = helper.getLevel().dimension().identifier().toString();
        String soundId = "minecraft:entity.generic.explode#gametest_" + source.asLong();

        try {
            SoundTracker.addSound(null, source, dimension, 32.0, 8.0, 200, soundId);
            AttractionGoal goal = new AttractionGoal(zombie, 1.0);
            if (!goal.canUse()) {
                helper.fail("An eligible zombie did not acquire the nearby tracked sound");
                return;
            }
            if (!source.equals(goal.getTargetSoundPos())) {
                helper.fail("Attraction goal selected " + goal.getTargetSoundPos() + " instead of " + source);
                return;
            }

            goal.tick();
            if (!source.equals(goal.getTargetSoundPos())) {
                helper.fail("Attraction goal discarded its tracked sound while updating pursuit");
                return;
            }
            helper.succeed();
        } finally {
            SoundTracker.removeSoundAt(source, dimension);
        }
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeWrapsPlannedBlockAndAnchors(GameTestHelper helper) {
        verifyBridgeContact(helper, 0);
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeFirstLayerOwnsMovementBetweenControllerSteps(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 5, 5), Blocks.STONE);
        java.util.ArrayList<Mob> members = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Mob member = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
            member.setInvulnerable(true);
            members.add(member);
        }
        Mob control = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        control.setInvulnerable(true);
        control.setNoAi(false);
        int[] controlWrites = {0}, workerWrites = {0};
        installWalkingProbe(control, controlWrites);
        Mob upper = members.get(1);
        upper.setNoAi(false);
        installWalkingProbe(upper, workerWrites);
        LivingWallCoordinator.ensureBridgeWorkGoal(upper);
        if (upper.goalSelector.getAvailableGoals().stream().noneMatch(goal ->
                goal.getGoal() instanceof com.example.soundattract.ai.BridgeWorkGoal)) {
            helper.fail("Live fixture is missing its production bridge controller"); return;
        }
        try {
        try (var session = new LivingWallCoordinator.DeploymentTestSession(helper.getLevel(), members,
                helper.absolutePos(new BlockPos(3, 2, 2)), helper.absolutePos(new BlockPos(4, 6, 5)))) {
            Vec3 controlStart = control.position();
            for (int step = 0; step < 6; step++) control.tick();
            if (controlWrites[0] == 0 || control.position().distanceTo(controlStart) < 0.05) {
                helper.fail("Native AI walking probe did not move the unowned positive control"); return;
            }
            boolean traversed = false;
            for (int step = 0; step < 400; step++) {
                session.step();
                Vec3 certified = upper.position();
                upper.tick();
                if (workerWrites[0] != 0 || upper.position().distanceTo(certified) > 1.0E-6) {
                    helper.fail("Placed first layer escaped between controller steps: phase=" + session.phase()
                            + " writes=" + workerWrites[0] + " from=" + certified + " to=" + upper.position()); return;
                }
                if (session.phase().equals("TRAVERSE")) { traversed = true; break; }
                if (session.phase().startsWith("RELEASED")) { helper.fail("Live AI bridge released"); return; }
            }
            if (!traversed) { helper.fail("Live AI bridge did not finish tilting"); return; }
        }
        Vec3 released = upper.position();
        for (int step = 0; step < 6; step++) upper.tick();
        if (workerWrites[0] == 0 || upper.position().distanceTo(released) < 0.05)
            helper.fail("Released structural worker did not regain native movement");
        else helper.succeed();
        } finally { control.discard(); members.forEach(Mob::discard); }
    }

    private static void installWalkingProbe(Mob mob, int[] writes) {
        mob.goalSelector.removeAllGoals(goal -> !(goal instanceof com.example.soundattract.ai.BridgeWorkGoal));
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.Goal() {
            { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK)); }
            public boolean canUse() { return true; }
            public boolean canContinueToUse() { return true; }
            public void tick() {
                writes[0]++;
                mob.getMoveControl().setWantedPosition(mob.getX() + 2, mob.getY(), mob.getZ(), 1);
            }
        });
    }

    @CoreTest(timeoutTicks = 40, template = "pillar_diagonal")
    public static void livingBridgeCompletedPillarSurvivesMissingPlanner(GameTestHelper helper) {
        verifyRecentPillar(helper, true);
    }

    @CoreTest(timeoutTicks = 40, template = "pillar_straight")
    public static void livingBridgeCompletedPillarReachesLanding(GameTestHelper helper) {
        verifyRecentPillar(helper, false);
    }

    private static void verifyRecentPillar(GameTestHelper helper, boolean diagonal) {
        BlockPos pivot = diagonal ? new BlockPos(6, 2, 4) : new BlockPos(4, 2, 4);
        BlockPos anchor = diagonal ? new BlockPos(2, 6, 8) : new BlockPos(4, 6, 8);
        BlockPos base = diagonal ? new BlockPos(6, 2, 2) : new BlockPos(4, 2, 3);
        java.util.ArrayList<Mob> members = new java.util.ArrayList<>();
        Mob planner = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pivot);
        for (int i = 0; i < (diagonal ? 11 : 8); i++) {
            Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, base);
            mob.setInvulnerable(true);
            members.add(mob);
        }
        try (var session = new LivingWallCoordinator.DeploymentTestSession(helper.getLevel(), members,
                helper.absolutePos(pivot), helper.absolutePos(anchor), planner)) {
            session.commitAssembledFoundation(Vec3.atBottomCenterOf(helper.absolutePos(base)));
            if (diagonal) planner.discard();
            int required = session.requiredBodiesForTest();
            if (required != (diagonal ? 14 : 10)) {
                helper.fail("Committed foundation did not resize the actual span: " + required); return;
            }
            while (members.size() < required) {
                Mob worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, base);
                worker.setInvulnerable(true);
                members.add(worker);
                session.addAssemblyWorkerForTest(worker);
                int assembled = 0;
                for (int step = 0; step < 400; step++) {
                    assembled = session.assembleStep();
                    if (assembled >= members.size()) break;
                }
                if (assembled < members.size()) { helper.fail("Extra worker did not finish real assembly"); return; }
            }
            for (Mob member : members) if (helper.getLevel().getBlockCollisions(member,
                    member.getBoundingBox().deflate(1.0E-7)).iterator().hasNext()) {
                helper.fail("Logged source pillar fixture intersects terrain: " + member.position()); return;
            }
            String rejection = session.assemblyClearanceForTest();
            if (rejection != null) { helper.fail("Completed logged pillar rejected: " + rejection); return; }
            Vec3 foundation = members.getFirst().position();
            List<Vec3> previous = members.stream().map(Mob::position).toList();
            boolean settled = false, rotated = false, anchored = false;
            for (int step = 0; step < 600; step++) {
                session.step();
                settled |= session.phase().equals("SETTLE");
                rotated |= session.phase().equals("ROTATE");
                anchored |= session.phase().equals("ANCHOR");
                if (session.phase().startsWith("RELEASED")) {
                    helper.fail("Logged pillar released: " + session.phase()); return;
                }
                if (!foundation.equals(members.getFirst().position())) { helper.fail("Foundation moved"); return; }
                for (int slot = 0; slot < members.size(); slot++) {
                    Mob member = members.get(slot);
                    if (member.position().distanceTo(previous.get(slot)) > 0.251
                            || helper.getLevel().getBlockCollisions(member, member.getBoundingBox().deflate(1.0E-7)).iterator().hasNext()
                            || (slot > 0 && !member.getBoundingBox().inflate(0.081).intersects(members.get(slot - 1).getBoundingBox()))) {
                        helper.fail("Logged deployment jumped, penetrated or disconnected slot=" + slot + " phase=" + session.phase()); return;
                    }
                }
                previous = members.stream().map(Mob::position).toList();
                if (session.phase().equals("TRAVERSE")) {
                    if (!settled || !rotated || !anchored
                            || members.getLast().position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(anchor))) > 0.01)
                        helper.fail("Logged pillar skipped deployment or missed landing");
                    else helper.succeed();
                    return;
                }
            }
            helper.fail("Logged pillar did not deploy: " + session.phase());
        } finally { members.forEach(Mob::discard); planner.discard(); }
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeFirstLayerFollowsOffsetFoundation(GameTestHelper helper) {
        verifyOffsetFoundationAssembly(helper, new Vec3(0, 0, 1), Vec3.ZERO);
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeFirstLayerDoesNotDoubleStagingOffset(GameTestHelper helper) {
        verifyOffsetFoundationAssembly(helper, new Vec3(1, 0, -1), new Vec3(0.5, 0, 0.5));
    }

    private static void verifyOffsetFoundationAssembly(GameTestHelper helper, Vec3 offset, Vec3 stagingOffset) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 5, 5), Blocks.STONE);
        java.util.ArrayList<Mob> members = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) members.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 2)));
        try (var session = new LivingWallCoordinator.DeploymentTestSession(helper.getLevel(), members,
                helper.absolutePos(new BlockPos(3, 2, 2)), helper.absolutePos(new BlockPos(4, 6, 5)))) {
            Vec3 foundation = members.getFirst().position().add(offset);
            session.prepareFoundationAssembly(foundation, stagingOffset);
            boolean placed = false;
            for (int step = 0; step < 120; step++) {
                if (session.assembleStep() >= 2) { placed = true; break; }
            }
            if (!placed) { helper.fail("The first upper layer did not finish real assembly"); return; }
            Mob base = members.getFirst(), upper = members.get(1);
            if (!base.position().equals(foundation)
                    || Math.abs(upper.getX() - base.getX()) > 1.0E-6
                    || Math.abs(upper.getZ() - base.getZ()) > 1.0E-6
                    || Math.abs(upper.getBoundingBox().minY - base.getBoundingBox().maxY) > 0.08) {
                helper.fail("First upper layer floats beside its base: base=" + base.position() + " upper=" + upper.position());
                return;
            }
            helper.succeed();
        } finally { members.forEach(Mob::discard); }
    }

    @CoreTest(timeoutTicks = 40, template = "orphan_recovery")
    public static void livingBridgeRecoversTwentyBlockOrphan(GameTestHelper helper) {
        com.example.soundattract.ai.LivingBridgeOrphanRecoveryGameTests.verify(helper, false);
    }

    @CoreTest(timeoutTicks = 40, template = "orphan_recovery")
    public static void livingBridgeOrphanRecoveryWaitsForClearRoute(GameTestHelper helper) {
        com.example.soundattract.ai.LivingBridgeOrphanRecoveryGameTests.verify(helper, true);
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeOrphanRecoveryPreservesLiveWorker(GameTestHelper helper) {
        com.example.soundattract.ai.LivingBridgeOrphanRecoveryGameTests.verifyLiveOwner(helper);
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeWrapsSlabAndAnchors(GameTestHelper helper) {
        verifyBridgeContact(helper, 1);
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeWrapsStairsAndAnchors(GameTestHelper helper) {
        verifyBridgeContact(helper, 2);
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeReplansFromLivePoseWithoutRewind(GameTestHelper helper) {
        verifyBridgeContact(helper, 3);
    }

    @CoreTest(timeoutTicks = 40)
    public static void livingBridgeHoldsAndReleasesImpossibleDetour(GameTestHelper helper) {
        verifyBridgeContact(helper, 4);
    }

    private static void verifyBridgeContact(GameTestHelper helper, int scenario) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var obstacle = (scenario == 1 ? Blocks.STONE_SLAB : scenario == 2 ? Blocks.STONE_BRICK_STAIRS : Blocks.STONE).defaultBlockState();
        if (scenario < 3) helper.setBlock(new BlockPos(3, 6, 4), obstacle);
        helper.setBlock(new BlockPos(4, 5, 5), Blocks.STONE);
        java.util.ArrayList<Mob> members = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
            mob.setInvulnerable(true);
            members.add(mob);
        }
        try (var session = new LivingWallCoordinator.DeploymentTestSession(helper.getLevel(), members,
                helper.absolutePos(new BlockPos(3, 2, 2)), helper.absolutePos(new BlockPos(4, 6, 5)))) {
            Vec3 foundation = members.getFirst().position();
            List<Vec3> previous = members.stream().map(Mob::position).toList();
            boolean rotated = false, anchored = false, wrapped = false;
            for (int tick = 0; tick < 400; tick++) {
                if (tick == 15 && scenario >= 3) {
                    if (scenario == 3) helper.setBlock(new BlockPos(3, 6, 4), obstacle);
                    else for (int x = 0; x < 9; x++) for (int y = 2; y < 21; y++)
                        helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
                }
                session.step();
                if (session.phase().startsWith("RELEASED")) {
                    if (scenario != 4 || tick < 55) helper.fail("Bridge released unexpectedly: " + session.phase());
                    else {
                        for (Mob member : members) if (member.noPhysics || member.isNoGravity()) {
                            helper.fail("Blocked deployment retained control after release"); return;
                        }
                        helper.succeed();
                    }
                    return;
                }
                rotated |= session.phase().equals("ROTATE");
                anchored |= session.phase().equals("ANCHOR");
                if (!members.getFirst().position().equals(foundation)) {
                    helper.fail("Deployment moved the foundation"); return;
                }
                for (int slot = 0; slot < members.size(); slot++) {
                    Mob mob = members.get(slot);
                    if (helper.getLevel().getBlockCollisions(mob, mob.getBoundingBox().deflate(1.0E-7)).iterator().hasNext()) {
                        helper.fail("Deployment penetrated a block at slot " + slot + " tick " + tick); return;
                    }
                    if (mob.position().distanceTo(previous.get(slot)) > 0.251) {
                        helper.fail("Deployment jumped/rewound at slot " + slot); return;
                    }
                    if (slot > 0 && !mob.getBoundingBox().inflate(0.081).intersects(members.get(slot - 1).getBoundingBox())) {
                        helper.fail("Deployment disconnected slot " + slot + " tick " + tick); return;
                    }
                    BlockPos block = helper.absolutePos(new BlockPos(3, 6, 4));
                    for (var shape : obstacle.getCollisionShape(helper.getLevel(), block).toAabbs()) {
                        var surface = shape.move(block);
                        wrapped |= slot > 0 && Math.abs(mob.getBoundingBox().minY - surface.maxY) < 0.08
                                && mob.getBoundingBox().intersects(surface.move(0, 0.1, 0));
                    }
                }
                previous = members.stream().map(Mob::position).toList();
                if (session.phase().equals("TRAVERSE")) {
                    if (scenario == 4) helper.fail("Bridge crossed an impossible barrier");
                    else if (!rotated || !anchored || !wrapped) helper.fail("Bridge skipped contact/wrap or anchor: " + rotated + "/" + anchored + "/" + wrapped);
                    else helper.succeed();
                    return;
                }
            }
            helper.fail("Bridge did not finish deployment: " + session.phase());
        } finally { members.forEach(Mob::discard); }
    }

    @CoreTest(timeoutTicks = 100)
    public static void trackedSoundExpiresAfterItsLifetime(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 9, 3));
        String dimension = helper.getLevel().dimension().identifier().toString();
        String soundId = "minecraft:entity.generic.explode#expiry_" + source.asLong();

        SoundTracker.addSound(null, source, dimension, 24.0, 3.0, 1, soundId);
        if (SoundTracker.getNearbySounds(helper.getLevel(), dimension, source, null).stream()
                .noneMatch(record -> soundId.equals(record.soundId))) {
            helper.fail("Newly tracked sound was not available to nearby listeners");
            return;
        }
        SoundTracker.tick();
        if (SoundTracker.getNearbySounds(helper.getLevel(), dimension, source, null).stream()
                .anyMatch(record -> soundId.equals(record.soundId))) {
            helper.fail("Expired tracked sound remained in the spatial index");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void stealthDetectionUsesTheSuppliedRange(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(new BlockPos(5, 9, 2));
        player.snapTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, 180.0f, 0.0f);

        if (!StealthDetectionEvents.canMobDetectPlayerWithRange(zombie, player, 64.0)) {
            helper.fail("A nearby visible survival player was rejected inside the supplied detection range");
            return;
        }
        if (StealthDetectionEvents.canMobDetectPlayerWithRange(zombie, player, 0.25)) {
            helper.fail("A player outside the supplied detection range was accepted");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void latestPointBlankAttachmentApiMatchesIntegrationContract(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("pointblank")) {
            helper.fail("Point Blank is not present in the GameTest runtime");
            return;
        }
        try {
            ClassLoader loader = CoreMechanicsGameTests.class.getClassLoader();
            Class<?> attachments = loader.loadClass("com.vicmatskiv.pointblank.attachment.Attachments");
            Class<?> attachment = loader.loadClass("com.vicmatskiv.pointblank.attachment.Attachment");
            Class<?> category = loader.loadClass("com.vicmatskiv.pointblank.attachment.AttachmentCategory");
            attachments.getMethod("getAttachments", net.minecraft.world.item.ItemStack.class, boolean.class);
            attachment.getMethod("getCategory");
            category.getMethod("getName");
            category.getField("MUZZLE");
        } catch (ReflectiveOperationException exception) {
            helper.fail("Latest Point Blank attachment API no longer matches the integration reflection contract: "
                    + exception.getMessage());
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void optionalIntegrationModsAreLoadedForCompatibilityRun(GameTestHelper helper) {
        if (!com.example.soundattract.config.DebugLoggingConfig.SPEC.isLoaded()) {
            helper.fail("Debug logging config spec was not registered and loaded for this runtime");
            return;
        }
        String[] expectedMods = {
                "geckolib", "glitchcore", "plasmovoice", "pointblank", "quantified",
                "smartbrainlib", "spoiled", "toughasnails", "voicechat"
        };
        java.util.List<String> missing = java.util.Arrays.stream(expectedMods)
                .filter(modId -> !net.neoforged.fml.ModList.get().isLoaded(modId))
                .toList();
        if (!missing.isEmpty()) {
            helper.fail("Optional integration jars are not loaded in GameTest runtime: " + missing);
            return;
        }

        try {
            ClassLoader loader = CoreMechanicsGameTests.class.getClassLoader();
            String[] integrationApiClasses = {
                    "su.plo.voice.api.addon.AddonInitializer",
                    "com.vicmatskiv.pointblank.attachment.Attachments",
                    "com.vicmatskiv.pointblank.attachment.Attachment",
                    "com.vicmatskiv.pointblank.attachment.AttachmentCategory",
                    "org.admany.quantified.api.QuantifiedAPI",
                    "net.tslat.smartbrainlib.api.core.SmartBrain",
                    "de.maxhenkel.voicechat.api.VoicechatPlugin",
                    "toughasnails.api.temperature.TemperatureHelper"
            };
            for (String apiClass : integrationApiClasses) {
                Class.forName(apiClass, false, loader);
            }
        } catch (ClassNotFoundException exception) {
            helper.fail("An optional integration API class is unavailable: " + exception.getMessage());
            return;
        }

        if (!com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge.isAvailable()) {
            helper.fail("Quantified is loaded, but its optional compute/cache bridge did not resolve the 2.0 API");
            return;
        }

        try {
            Class<?> attachments = Class.forName("com.vicmatskiv.pointblank.attachment.Attachments");
            var attachmentMap = attachments.getMethod("getAttachments", net.minecraft.world.item.ItemStack.class, boolean.class)
                    .invoke(null, net.minecraft.world.item.ItemStack.EMPTY, true);
            if (!(attachmentMap instanceof java.util.Map<?, ?>)) {
                helper.fail("Point Blank attachment enumeration returned an unexpected value");
                return;
            }
        } catch (ReflectiveOperationException exception) {
            helper.fail("Point Blank attachment enumeration failed against the installed API: " + exception.getMessage());
            return;
        }

        var wind = com.example.soundattract.integration.weather2.Weather2WindCompat.sample(
                helper.getLevel(), helper.absolutePos(new BlockPos(3, 9, 3)), true);
        if (wind.direction() == null || !Double.isFinite(wind.speed()) || wind.speed() < 0.0
                || !Double.isFinite(wind.direction().x) || !Double.isFinite(wind.direction().z)) {
            helper.fail("Weather2 wind adapter returned an invalid sample");
            return;
        }
        if (!com.example.soundattract.integration.spoiled.SpoiledScentIntegration.isLoaded()
                || com.example.soundattract.integration.spoiled.SpoiledScentIntegration.spoilageProgress(
                        net.minecraft.world.item.ItemStack.EMPTY) != 0.0) {
            helper.fail("Spoiled integration did not safely handle an empty item stack");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void woolOcclusionReducesSoundReach(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(2, 10, 2));
        BlockPos listener = helper.absolutePos(new BlockPos(7, 10, 2));
        helper.setBlock(new BlockPos(4, 10, 2), Blocks.WHITE_WOOL);

        net.minecraft.world.level.Level level = helper.getLevel();
        com.example.soundattract.los.OptimizedLOS.resetMufflingBudget();
        double[] muffled = SoundTracker.applyBlockMuffling(
                level, source, listener, 40.0, 10.0, "soundattract:gametest/wool_wall", false);
        if (!(muffled[0] < 40.0 || muffled[1] < 10.0)) {
            helper.fail("A wool wall did not reduce the tracked sound's range or weight");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void scentNodesPersistAndWindReplacementIsOwnerScoped(GameTestHelper helper) {
        ScentManager manager = new ScentManager(helper.getLevel());
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        long now = helper.getLevel().getGameTime();
        ScentNode ordinary = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 9, 2))), now, 0.7f, ownerA,
                ScentSourceType.PLAYER_WALK);
        ScentNode windA = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 9, 2))), now, 0.9f, ownerA,
                ScentSourceType.PLAYER_WIND);
        ScentNode windB = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 9, 2))), now, 0.8f, ownerB,
                ScentSourceType.PLAYER_WIND);
        manager.addScentNode(ordinary);
        manager.replaceWindNodes(ownerA, List.of(windA));
        manager.replaceWindNodes(ownerB, List.of(windB));

        manager.replaceWindNodes(ownerA, List.of(new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 9, 2))), now, 1.0f,
                ownerA, ScentSourceType.PLAYER_WIND)));
        List<ScentNode> current = manager.getAllNodes();
        if (current.size() != 3 || current.stream().anyMatch(node -> node == windA)) {
            helper.fail("Replacing one owner's wind trail did not replace only that owner's prior nodes");
            return;
        }

        ScentManager restored = new ScentManager(helper.getLevel());
        restored.deserializeNBT(manager.serializeNBT());
        List<ScentNode> saved = restored.getAllNodes();
        if (saved.size() != current.size()
                || saved.stream().noneMatch(node -> node.getOwnerUUID().equals(ownerB)
                    && node.getSourceType() == ScentSourceType.PLAYER_WIND)
                || saved.stream().noneMatch(node -> node.getOwnerUUID().equals(ownerA)
                    && node.getSourceType() == ScentSourceType.PLAYER_WALK)) {
            helper.fail("Scent NBT round-trip lost nodes or their owner/source metadata");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void eligibleZombieAcquiresNearbyScentTrail(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        ScentManager manager = ScentManager.get(helper.getLevel()).orElseThrow();
        manager.clear();
        UUID owner = UUID.randomUUID();
        BlockPos trail = helper.absolutePos(new BlockPos(5, 9, 2));
        manager.addScentNode(new ScentNode(Vec3.atCenterOf(trail), helper.getLevel().getGameTime(), 1.0f,
                owner, ScentSourceType.PLAYER_WALK));

        FollowScentGoal goal = new FollowScentGoal((net.minecraft.world.entity.PathfinderMob) zombie);
        boolean acquired = false;
        for (int attempt = 0; attempt < 100 && !acquired; attempt++) {
            acquired = goal.canUse();
        }
        if (!acquired || !goal.isFollowingScent()
                || !Vec3.atCenterOf(trail).equals(goal.getTargetPosition())) {
            helper.fail("Eligible zombie did not acquire the nearby player's scent trail");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void hazardClassifierRecognizesSafeLavaTrapdoorAndDrop(GameTestHelper helper) {
        HazardClassifier.clear();
        BlockPos safeFeet = helper.absolutePos(new BlockPos(1, 9, 1));
        helper.setBlock(new BlockPos(1, 8, 1), Blocks.STONE);
        if (HazardClassifier.classify(helper.getLevel(), safeFeet) != HazardClassifier.HazardType.SAFE) {
            helper.fail("A solid supported position was not classified as safe");
            return;
        }

        BlockPos lavaFeet = helper.absolutePos(new BlockPos(3, 9, 1));
        helper.setBlock(new BlockPos(3, 9, 1), Blocks.LAVA);
        if (HazardClassifier.classify(helper.getLevel(), lavaFeet) != HazardClassifier.HazardType.LAVA) {
            helper.fail("A lava position was not classified as lava");
            return;
        }

        BlockPos trapdoorFeet = helper.absolutePos(new BlockPos(5, 9, 1));
        helper.setBlock(new BlockPos(5, 9, 1), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true));
        if (HazardClassifier.classify(helper.getLevel(), trapdoorFeet)
                != HazardClassifier.HazardType.TRAPDOOR_DROP) {
            helper.fail("An open trapdoor over a drop was not classified as a trapdoor hazard");
            return;
        }

        BlockPos dropFeet = helper.absolutePos(new BlockPos(7, 9, 1));
        if (HazardClassifier.classify(helper.getLevel(), dropFeet) != HazardClassifier.HazardType.DEEP_DROP) {
            helper.fail("An unsupported three-block drop was not classified as dangerous");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 400)
    public static void flowFieldReturnsProgressTowardTarget(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        BlockPos targetBlock = zombie.blockPosition().offset(0, 0, 4);
        Vec3 target = Vec3.atBottomCenterOf(targetBlock);
        helper.runAfterDelay(1, () -> checkFlowFieldResult(helper, zombie, target));
    }

    private static void checkFlowFieldResult(GameTestHelper helper, Mob zombie, Vec3 target) {
        if (zombie.isRemoved() || !zombie.isAlive()) {
            helper.fail("Flow-field requester did not remain alive while its scan was pending");
            return;
        }

        Vec3 direction = FlowFieldManager.getNextStep(zombie, target);
        if (direction == null) {
            helper.runAfterDelay(1, () -> checkFlowFieldResult(helper, zombie, target));
            return;
        }

        Vec3 toTarget = target.subtract(zombie.position()).normalize();
        if (!Double.isFinite(direction.x) || !Double.isFinite(direction.y) || !Double.isFinite(direction.z)
                || direction.dot(toTarget) <= 0.5 || direction.lengthSqr() < 0.9 || direction.lengthSqr() > 1.1) {
            helper.fail("Flow field returned invalid or non-progressing direction " + direction);
            return;
        }
        helper.succeed();
    }
    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "core_mechanics"),
                new TestEnvironmentDefinition.AllOf());
        for (Method method : CoreMechanicsGameTests.class.getDeclaredMethods()) {
            CoreTest metadata = method.getAnnotation(CoreTest.class);
            if (metadata == null) continue;
            Identifier structure = Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID,
                    "coremechanicsgametests." + metadata.template());
            TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(environment, structure,
                    metadata.timeoutTicks(), 0, true);
            Identifier testId = Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID,
                    "core_mechanics/" + toSnakeCase(method.getName()));
            event.registerTest(testId, testData -> new CallbackGameTestInstance(testData, method), data);
        }
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static final class CallbackGameTestInstance extends GameTestInstance {
        private final Method testMethod;

        private CallbackGameTestInstance(TestData<Holder<TestEnvironmentDefinition>> data, Method testMethod) {
            super(data);
            this.testMethod = testMethod;
        }

        @Override
        public void run(GameTestHelper helper) {
            try {
                testMethod.invoke(null, helper);
            } catch (ReflectiveOperationException exception) {
                if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
                if (exception.getCause() instanceof Error error) throw error;
                helper.fail("Could not execute test " + testMethod.getName() + ": " + exception.getMessage());
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal(testMethod.getName());
        }
    }

}
