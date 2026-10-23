package com.example.soundattract.event;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.ai.RaidManager;
import com.example.soundattract.client.AwarenessClientState;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.MobAccessor;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual event ordering, alert selection, and wire/client handoff for overhead markers. */
public final class OverheadAlertGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void loadingAStationaryArrowDoesNotCreateANearMiss(GameTestHelper helper) {
        ArrowSettings settings = new ArrowSettings(false);
        ServerLevel level = helper.getLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 2, 181, 2);
        mob.setNoAi(true);
        ((MobAccessor) mob).getGoalSelector().addGoal(3, new AttractionGoal(mob, 1.0));
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        viewer.setPos(mob.position().add(10, 0, 0));
        Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setOwner(viewer);
        arrow.setPos(mob.position().add(0, mob.getBbHeight() / 2, 1.4));
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(Vec3.ZERO);
        require(level.addFreshEntity(arrow), "Stationary arrow must enter the actual entity-load path");
        var state = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
        require(state != null, "Stationary arrow must initially be tracked");
        helper.runAfterDelay(2, () -> {
            try {
                require(state.notifiedMobIds.isEmpty(), "A stationary loaded arrow must not notify nearby mobs");
                require(!ArrowInvestigationEvents.isTrackedForTesting(level, arrow.getId()),
                        "Stationary arrow must leave the active flight tracker");
                helper.succeed();
            } finally {
                settings.close(); arrow.discard(); mob.discard();
                level.getServer().getPlayerList().remove(viewer);
            }
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void arrowScentDoesNotConsumeTheNearMissFlightSegment(GameTestHelper helper) {
        try (ArrowSettings ignored = new ArrowSettings(true)) {
            ServerLevel level = helper.getLevel();
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 180, 2))));
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(8, 0, 0);
            require(level.addFreshEntity(arrow), "Arrow must enter the registered entity-load path");
            try {
                var state = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
                require(state != null, "Registered entity-load callback must track the arrow");
                Vec3 previousInvestigationPosition = state.prevPos;
                arrow.setPos(arrow.position().add(8, 0, 0));
                ArrowScentEvents.onLevelTick(level);
                require(state.prevPos.equals(previousInvestigationPosition),
                        "Scent callback consumed the near-miss segment: before=" + previousInvestigationPosition
                                + ", after=" + state.prevPos + ", arrow=" + arrow.position());
            } finally {
                arrow.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, required = false)
    public void registeredCallbacksShowFastArrowNearMissWithScentEnabled(GameTestHelper helper) {
        runArrowFlight(helper, true, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, required = false)
    public void registeredCallbacksShowNearMissToShooterTwentyBlocksAway(GameTestHelper helper) {
        runArrowFlight(helper, false, true);
    }

    private static void runArrowFlight(GameTestHelper helper, boolean scentEnabled, boolean distant) {
        ArrowSettings settings = new ArrowSettings(scentEnabled);
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 34; x++) {
            for (int z = 0; z <= 8; z++) helper.setBlock(new BlockPos(x, 180, z), Blocks.STONE);
        }
        Zombie mob = helper.spawn(EntityType.ZOMBIE, distant ? 26 : 10, 181, 4);
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        BlockPos viewerPos = helper.absolutePos(new BlockPos(distant ? 2 : 10, 181, 0));
        viewer.setPos(Vec3.atBottomCenterOf(viewerPos));
        // Production queues native goal additions until its initial 50-tick warm-up.
        // Wait for that configured lifecycle, rather than assuming two ticks enroll a mob.
        helper.runAfterDelay(SoundAttractConfig.COMMON.initialGroupComputationDelay.get() + 2, () -> {
            Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setOwner(viewer);
            try {
                // Begin inside the near-miss radius so the registered flight
                // callback observes a segment that actually crosses it.
                arrow.setPos(mob.getX() - (distant ? 2.0 : 8.0), mob.getY() + mob.getBbHeight() / 2.0,
                        mob.getZ() + 1.4);
                arrow.setNoGravity(true);
                arrow.setDeltaMovement(distant ? 0.7 : 16.0, 0, 0);
                require(level.addFreshEntity(arrow), "Flight arrow must load naturally");
                var state = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
                require(state != null, "Natural arrow load must create tracking state");
                // No direct scanner calls or rewinding of prevPos: registered world callbacks own the flight.
                helper.runAfterDelay(8, () -> {
                    try {
                        require(state.notifiedMobIds.contains(mob.getUUID()),
                                "Registered callbacks missed arrow: scent=" + scentEnabled + ", distant=" + distant
                                        + ", viewer=" + viewer.position() + ", mob=" + mob.position()
                                        + ", arrow=" + arrow.position() + ", scannedFrom=" + state.prevPos
                                        + ", notified=" + state.notifiedMobIds);
                        var alert = scanAlert(level, viewer, mob);
                        require(alert != null && alert.type() == 3,
                                "Actual near miss must reach the overhead snapshot, got " + alert);
                        assertWireAndClient(alert);
                        helper.succeed();
                    } finally {
                        settings.close();
                        arrow.discard();
                        mob.discard();
                        level.getServer().getPlayerList().remove(viewer);
                    }
                });
            } catch (Throwable failure) {
                settings.close();
                arrow.discard();
                mob.discard();
                level.getServer().getPlayerList().remove(viewer);
                throw failure;
            }
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void proximityDetectionAndNearMissUseTheirExpectedPriority(GameTestHelper helper) {
        try (StealthSettings ignored = new StealthSettings()) {
            ServerLevel level = helper.getLevel();
            Zombie mob = helper.spawn(EntityType.ZOMBIE, 2, 181, 2);
            ServerPlayer viewer = survivalViewer(level, mob.position().add(18, 0, 0));
            try {
                mob.setYRot(90); mob.setYHeadRot(90);
                var proximity = createAlert(level, viewer, mob, 20.0, level.getGameTime());
                require(proximity != null && proximity.type() == 1, "Rear FOV edge must select proximity: " + proximity);
                assertWireAndClient(proximity);
                mob.setYRot(-90); mob.setYHeadRot(-90);
                var detected = createAlert(level, viewer, mob, 20.0, level.getGameTime());
                require(detected != null && detected.type() == 2, "Visible player must select detected: " + detected);
                assertWireAndClient(detected);
                AwarenessServerEvents.recordNearMiss(mob);
                var nearMiss = createAlert(level, viewer, mob, 20.0, level.getGameTime());
                require(nearMiss != null && nearMiss.type() == 3, "Near miss must override the red eye: " + nearMiss);
                assertWireAndClient(nearMiss);
                var expired = createAlert(level, viewer, mob, 20.0, level.getGameTime() + 31);
                require(expired != null && expired.type() == 2, "Expired near miss must restore detection: " + expired);
            } finally {
                mob.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void scentMarkerFollowsRunningGoalAndClearsWhenNavigationStops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 20; x++) for (int z = 0; z <= 6; z++)
            helper.setBlock(new BlockPos(x, 180, z), Blocks.STONE);
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 2, 181, 2);
        // Navigation cannot update until the naturally spawned mob has landed.
        helper.runAfterDelay(2, () -> {
            try (StealthSettings ignored = new StealthSettings()) {
                ServerPlayer viewer = survivalViewer(level, mob.position().add(22, 0, 0));
                boolean previousScent = SoundAttractConfig.COMMON.enableScentSystem.get();
                SoundAttractConfig.COMMON.enableScentSystem.set(true);
                try {
                    ScentManager scents = new ScentManager(level);
                    scents.addScentNode(new ScentNode(mob.position().add(8, 0, 0), level.getGameTime(),
                            12.0f, viewer.getUUID(), ScentSourceType.PLAYER_WALK));
                    FollowScentGoal goal = new FollowScentGoal(mob, scents);
                    ((MobAccessor) mob).getGoalSelector().removeAllGoals(g -> true);
                    ((MobAccessor) mob).getGoalSelector().addGoal(0, goal);
                    require(goal.acquireScentForTesting(), "Real scent selection must acquire the owned node");
                    var wrapped = ((MobAccessor) mob).getGoalSelector().getAvailableGoals().iterator().next();
                    wrapped.start();
                    require(goal.isFollowingScent() && !mob.getNavigation().isDone(), "Scent fixture must have active navigation");
                    // Keep the survival viewer behind the mob as well as outside
                    // the configured range; file-watcher reloads can restore defaults.
                    mob.setYRot(90); mob.setYHeadRot(90);
                    var alert = scanAlert(level, viewer, mob);
                    require(alert != null && alert.type() == 4, "Active scent goal must reach the actual awareness scan: " + alert);
                    require(alert.progress() >= 0 && alert.progress() <= 1, "Scent progress must be normalized");
                    assertWireAndClient(alert);
                    wrapped.stop(); mob.getNavigation().stop();
                    var stopped = scanAlert(level, viewer, mob);
                    require(stopped == null || stopped.type() != 4, "Stopped scent following must clear its marker: " + stopped);
                    helper.succeed();
                } finally {
                    SoundAttractConfig.COMMON.enableScentSystem.set(previousScent);
                    mob.discard();
                }
            }
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void raidMarkerChangesColorOnAdvanceAndClearsOnCancellation(GameTestHelper helper) {
        try (StealthSettings ignored = new StealthSettings()) {
            ServerLevel level = helper.getLevel();
            Zombie leader = helper.spawn(EntityType.ZOMBIE, 2, 181, 2);
            leader.setYRot(90); leader.setYHeadRot(90);
            ServerPlayer viewer = survivalViewer(level, leader.position().add(22, 0, 0));
            int previousCountdown = SoundAttractConfig.COMMON.raidCountdownTicks.get();
            boolean previousReinforcements = SoundAttractConfig.COMMON.enableRaidReinforcements.get();
            SoundAttractConfig.COMMON.raidCountdownTicks.set(1);
            SoundAttractConfig.COMMON.enableRaidReinforcements.set(false);
            try {
                require(MobGroupManager.getLeader(leader) == leader, "Raid fixture must be a leader");
                RaidManager.scheduleRaid(leader, leader.blockPosition().offset(8, 0, 0), level.getGameTime());
                var countdown = scanAlert(level, viewer, leader);
                require(countdown != null && countdown.type() == 5 && countdown.color() == 0xFFFFC040,
                        "Raid countdown must emit its amber marker: " + countdown);
                assertWireAndClient(countdown);
                RaidManager.tick(level);
                var advancing = scanAlert(level, viewer, leader);
                require(advancing != null && advancing.type() == 5 && advancing.color() == 0xFFFF3030,
                        "Advancing raid must turn its marker red: " + advancing);
                assertWireAndClient(advancing);
                RaidManager.clearRaid(leader);
                var cancelled = scanAlert(level, viewer, leader);
                require(cancelled == null || cancelled.type() != 5, "Cancelled raid must clear its marker: " + cancelled);
            } finally {
                RaidManager.clearRaid(leader);
                SoundAttractConfig.COMMON.raidCountdownTicks.set(previousCountdown);
                SoundAttractConfig.COMMON.enableRaidReinforcements.set(previousReinforcements);
                leader.discard();
            }
        }
        helper.succeed();
    }

    private static ServerPlayer survivalViewer(ServerLevel level, Vec3 position) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "overhead-probe"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        player.setPos(position);
        return player;
    }

    private static AwarenessSnapshotMessage.Alert createAlert(ServerLevel level, ServerPlayer viewer, Mob mob,
                                                               double range, long now) {
        try {
            var method = AwarenessServerEvents.class.getDeclaredMethod("createAlert", ServerLevel.class,
                    ServerPlayer.class, Mob.class, double.class, double.class, long.class);
            method.setAccessible(true);
            return (AwarenessSnapshotMessage.Alert) method.invoke(null, level, viewer, mob, range, mob.distanceToSqr(viewer), now);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static AwarenessSnapshotMessage.Alert scanAlert(ServerLevel level, ServerPlayer viewer, Mob mob) {
        try {
            var method = AwarenessServerEvents.class.getDeclaredMethod("scanAwareness", ServerLevel.class, ServerPlayer.class);
            method.setAccessible(true);
            Object scan = method.invoke(null, level, viewer);
            var accessor = scan.getClass().getDeclaredMethod("alerts"); accessor.setAccessible(true);
            @SuppressWarnings("unchecked") List<AwarenessSnapshotMessage.Alert> alerts =
                    (List<AwarenessSnapshotMessage.Alert>) accessor.invoke(scan);
            return alerts.stream().filter(a -> a.mobId().equals(mob.getUUID())).findFirst().orElse(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void assertWireAndClient(AwarenessSnapshotMessage.Alert alert) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AwarenessSnapshotMessage.encode(new AwarenessSnapshotMessage(0, 0, 0, false, List.of(alert)), buffer);
            var decoded = AwarenessSnapshotMessage.decode(buffer);
            require(decoded.alerts().equals(List.of(alert)), "Wire format must preserve the triggered alert");
            AwarenessClientState.apply(decoded);
            var client = AwarenessClientState.alert(alert.mobId());
            require(client != null && client.entityId() == alert.entityId() && client.type() == alert.type()
                    && client.color() == alert.color() && client.progress() == alert.progress(),
                    "Triggered alert must reach the client presentation cache");
            require(client.triggerId() == alert.triggerId(), "Wire/client handoff must preserve trigger identity");
        } finally {
            buffer.release();
            AwarenessClientState.clear();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void repeatedNearMissGetsANewEventIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 2, 181, 2);
        ServerPlayer viewer = survivalViewer(level, mob.position().add(22, 0, 0));
        AwarenessServerEvents.recordNearMiss(mob);
        var first = createAlert(level, viewer, mob, 0, level.getGameTime());
        require(first != null && first.triggerId() != 0, "Near miss must have a nonzero event identity");
        assertWireAndClient(first);
        var duplicate = createAlert(level, viewer, mob, 0, level.getGameTime());
        require(duplicate.triggerId() == first.triggerId(), "Duplicate snapshot must retain the event identity");
        helper.runAfterDelay(1, () -> {
            try {
                AwarenessServerEvents.recordNearMiss(mob);
                var second = createAlert(level, viewer, mob, 0, level.getGameTime());
                require(second != null && second.triggerId() != first.triggerId(), "New near miss must have a new identity");
                assertWireAndClient(second);
                helper.succeed();
            } finally { mob.discard(); }
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void legacySnapshotsDecodeAndMalformedExtensionsAreRejected(GameTestHelper helper) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            UUID mobId = UUID.randomUUID();
            buffer.writeFloat(0); buffer.writeFloat(0); buffer.writeByte(0); buffer.writeBoolean(false);
            buffer.writeVarInt(1); buffer.writeUUID(mobId); buffer.writeVarInt(17); buffer.writeByte(3);
            buffer.writeInt(0xFFFFB000); buffer.writeFloat(0);
            int legacyEnd = buffer.writerIndex();
            var legacy = AwarenessSnapshotMessage.decode(buffer);
            require(legacy.alerts().get(0).triggerId() == 0 && legacy.alerts().get(0).mobId().equals(mobId),
                    "Original snapshots must remain readable with default trigger identity");
            buffer.readerIndex(0); buffer.writeVarInt(99);
            boolean invalidRejected = false;
            try { AwarenessSnapshotMessage.decode(buffer); }
            catch (io.netty.handler.codec.DecoderException expected) { invalidRejected = true; }
            require(invalidRejected, "Unknown extension version must be rejected");
            buffer.readerIndex(0); buffer.writerIndex(legacyEnd); buffer.writeVarInt(1);
            boolean truncatedRejected = false;
            try { AwarenessSnapshotMessage.decode(buffer); }
            catch (IndexOutOfBoundsException expected) { truncatedRejected = true; }
            require(truncatedRejected, "Truncated trigger IDs must be rejected");
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(net.minecraft.network.chat.Component.literal(message), 0);
    }

    private static final class ArrowSettings implements AutoCloseable {
        private final boolean enabled = SoundAttractConfig.COMMON.enableArrowInvestigation.get();
        private final boolean scent = SoundAttractConfig.COMMON.enableArrowScentTrail.get();
        private final double radius = SoundAttractConfig.COMMON.arrowNearMissRadius.get();
        ArrowSettings(boolean scentEnabled) {
            SoundAttractConfig.COMMON.enableArrowInvestigation.set(true);
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(scentEnabled);
            SoundAttractConfig.COMMON.arrowNearMissRadius.set(3.0);
        }
        @Override public void close() {
            SoundAttractConfig.COMMON.enableArrowInvestigation.set(enabled);
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(scent);
            SoundAttractConfig.COMMON.arrowNearMissRadius.set(radius);
        }
    }

    private static final class StealthSettings implements AutoCloseable {
        private final boolean enabled = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        private final boolean tiered = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        private final boolean xray = SoundAttractConfig.COMMON.enableXrayTargeting.get();
        StealthSettings() {
            SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(false);
        }
        @Override public void close() {
            SoundAttractConfig.COMMON.enableStealthMechanics.set(enabled);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(tiered);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(xray);
        }
    }
}




