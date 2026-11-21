package com.example.soundattract.ai;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.separate.GeneralConfig;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.worker.WorkerScheduler;
import com.example.soundattract.pathfinding.PursuitNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder("soundattract_targetsharing")
public final class GroupTargetSharingGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public void confirmedSightingSharesTargetPastOccludingFollowerAndKeepsRangeLimit(GameTestHelper helper) throws Exception {
        recordSelection("com.example.soundattract.ai.GroupTargetSharingGameTests#confirmedSightingSharesTargetPastOccludingFollowerAndKeepsRangeLimit",
                "grouptargetsharinggametests.confirmedsightingsharestargetpastoccludingfollowerandkeepsrangelimit");
        var level = helper.getLevel();
        double previousMaxRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        double previousStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        boolean previousOptimizedLos = SoundAttractConfig.COMMON.enableOptimizedLos.get();
        boolean previousSecondBrain = SoundAttractConfig.COMMON.enableSecondBrain.get();
        SoundAttractConfig.COMMON.maxStealthDetectionRange.set(8.0);
        SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(8.0);
        SoundAttractConfig.COMMON.enableOptimizedLos.set(false);
        SoundAttractConfig.COMMON.enableSecondBrain.set(false);

        var source = helper.spawn(EntityType.ZOMBIE, 8, 81, 6);
        var nearFollower = helper.spawn(EntityType.ZOMBIE, 8, 81, 5);
        var farFollower = helper.spawn(EntityType.ZOMBIE, 24, 81, 8);
        ServerPlayer player = makeTestPlayer(level);
        player.setPos(source.getX(), source.getY(), source.getZ() + 4.5);
        var playerPosition = player.position();
        var nearFollowerPosition = nearFollower.position();
        Runnable cleanup = () -> {
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(previousMaxRange);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(previousStandingRange);
            SoundAttractConfig.COMMON.enableOptimizedLos.set(previousOptimizedLos);
            SoundAttractConfig.COMMON.enableSecondBrain.set(previousSecondBrain);
            player.discard();
            source.discard();
            nearFollower.discard();
            farFollower.discard();
        };

        for (int x = 5; x <= 25; x++) {
            for (int z = 2; z <= 11; z++) {
                for (int y = 81; y <= 84; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                helper.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
            }
        }

        removeTargetAcquisitionGoals(nearFollower);
        removeTargetAcquisitionGoals(farFollower);
        source.setYRot(0.0F);
        source.setXRot(0.0F);
        setGroup(source, List.of(nearFollower, farFollower));

        try {
            require(MobGroupManager.getGroupRoster(source) != null
                            && MobGroupManager.getGroupRoster(source).members().contains(nearFollower.getUUID())
                            && MobGroupManager.getGroupRoster(source).members().contains(farFollower.getUUID()),
                    "fixture must install a live same-group roster");
            require(StealthDetectionEvents.canReceiveSharedTarget(source, player),
                    "source must pass player range/eligibility at source=" + source.position() + " player=" + player.position());
            require(FovEvents.isTargetInFov(source, player, false),
                    "source must face the player at source=" + source.position() + " player=" + player.position()
                            + " yaw=" + source.getYRot());
            BlockHitResult sourceRay = level.clip(new ClipContext(source.getEyePosition(), player.getEyePosition(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
            require(sourceRay.getType() == HitResult.Type.MISS,
                    "vanilla eye ray hit " + sourceRay.getBlockPos() + " block=" + level.getBlockState(sourceRay.getBlockPos())
                            + " sourceEye=" + source.getEyePosition() + " playerEye=" + player.getEyePosition());
            require(FovEvents.isTargetInFov(source, player, true),
                    "source's block/smoke sight check must pass at source=" + source.position() + " player=" + player.position());
            require(StealthDetectionEvents.hasDirectGroupSight(source, player),
                    "source sight line must be clear before target acquisition at source=" + source.position()
                            + " player=" + player.position());
            require(!StealthDetectionEvents.hasDirectGroupSight(nearFollower, player),
                    "near follower must fail direct sight while the source mob blocks its rays");
            var sourcePosition = source.position();
            source.setPos(source.getX() + 12.0, source.getY(), source.getZ());
            require(StealthDetectionEvents.hasDirectGroupSight(nearFollower, player),
                    "near follower positive control must gain direct sight after the source leaves its ray");
            source.setPos(sourcePosition.x, sourcePosition.y, sourcePosition.z);
            require(!StealthDetectionEvents.hasDirectGroupSight(nearFollower, player),
                    "near follower must lose direct sight when the source returns between it and the player");
            require(StealthDetectionEvents.canReceiveSharedTarget(nearFollower, player),
                    "occluded near follower must pass its own detection-range check");
            require(!StealthDetectionEvents.canReceiveSharedTarget(farFollower, player),
                    "far follower must remain outside its own detection range");

            source.setTarget(player);
            require(source.getTarget() == player, "source must retain its directly acquired player target");
            require(nearFollower.getTarget() == null, "follower target assignment must wait for report confirmation");

            helper.runAfterDelay(2, () -> {
                try {
                    require(source.getTarget() == player, "source must still own the direct target during confirmation");
                    require(nearFollower.getTarget() == player && GroupTargetSharingManager.ownsTarget(nearFollower, player),
                            "confirmed group sight must assign the player and lease to the blocked in-range follower");
                    require(farFollower.getTarget() == null,
                            "group report must not grant a combat target outside that follower's detection range");
                    require(player.blockPosition().equals(GroupTargetSharingManager.getFollowPosition(farFollower, source)),
                            "out-of-range follower must receive the group's report position for movement");
                    require(GroupTargetSharingManager.allowSharedMelee(nearFollower, player),
                            "recipient must pass combat validation before the target moves");
                    player.setPos(playerPosition.x, playerPosition.y, playerPosition.z + 128.0);
                    require(!GroupTargetSharingManager.allowSharedMelee(nearFollower, player),
                            "same-tick target movement outside perception must invalidate a cached combat permit");
                    player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
                    require(GroupTargetSharingManager.allowSharedMelee(nearFollower, player),
                            "same-tick target return must restore the recipient's combat permit");
                    source.setPos(source.getX() + 10.0, source.getY(), source.getZ());
                    require(StealthDetectionEvents.hasDirectGroupSight(nearFollower, player),
                            "recipient must have a clear pursuit path after the blocking source moves away");
                    require(PursuitNavigation.maybeMoveTo(nearFollower, player.position(), 1.0),
                            "shared-target recipient must be able to request a normal pursuit path while leased");
                    require(nearFollower.getNavigation().getPath() != null && !nearFollower.getNavigation().isDone(),
                            "normal pursuit request must install an active path toward the shared target");

                    FollowLeaderGoal followGoal = new FollowLeaderGoal(farFollower, 1.0);
                    require(followGoal.canUse(), "out-of-range follower must be eligible to follow the confirmed report");
                    followGoal.start();
                    followGoal.tick();
                    require(farFollower.getNavigation().getTargetPos() != null,
                            "follow goal must issue movement toward the group's confirmed report");
                    var recipientStart = nearFollower.position();
                    double playerDistanceStart = nearFollower.distanceToSqr(player);
                    helper.runAfterDelay(8, () -> {
                        try {
                            require(nearFollower.position().distanceToSqr(recipientStart) > 0.25
                                            && nearFollower.distanceToSqr(player) < playerDistanceStart
                                            && GroupTargetSharingManager.ownsTarget(nearFollower, player),
                                    "in-range recipient must pursue the shared player while its lease remains active; moved="
                                            + nearFollower.position().distanceToSqr(recipientStart) + " distanceStart="
                                            + playerDistanceStart + " distanceNow=" + nearFollower.distanceToSqr(player)
                                            + " target=" + (nearFollower.getTarget() == player) + " lease="
                                            + GroupTargetSharingManager.ownsTarget(nearFollower, player) + " path="
                                            + nearFollower.getNavigation().getTargetPos());
                            boolean floorCreakBefore = SoundAttractConfig.COMMON.enableFloorCreek.get();
                            withReloadedGeneralSetting("floor_creek.enableFloorCreek", !floorCreakBefore, () -> {
                                require(SoundAttractConfig.COMMON.enableFloorCreek.get() != floorCreakBefore,
                                        "the unrelated floor creak setting must change through the Forge reload");
                                tickGroupSharing(helper);
                                require(nearFollower.getTarget() == player && GroupTargetSharingManager.ownsTarget(nearFollower, player),
                                        "an unrelated general.toml reload must preserve the active sharing lease");
                            });
                            require(SoundAttractConfig.COMMON.enableFloorCreek.get() == floorCreakBefore,
                                    "the original floor creak setting must be restored after the isolated reload");

                            assertGroupDistanceReload(helper, source, nearFollower, player);

                            source.getNavigation().stop();
                            source.setPos(sourcePosition.x, sourcePosition.y, sourcePosition.z);
                            source.setDeltaMovement(Vec3.ZERO);
                            source.setYRot(0.0F);
                            source.setXRot(0.0F);
                            nearFollower.getNavigation().stop();
                            nearFollower.setPos(nearFollowerPosition.x, nearFollowerPosition.y, nearFollowerPosition.z);
                            nearFollower.setDeltaMovement(Vec3.ZERO);
                            player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
                            player.setDeltaMovement(Vec3.ZERO);
                            setGroup(source, List.of(farFollower));
                            setGroup(source, List.of(nearFollower, farFollower));
                            source.setTarget(null);
                            require(StealthDetectionEvents.hasDirectGroupSight(source, player),
                                    "the restored config must allow the source's clear sight line");
                            source.setTarget(player);
                            require(source.getTarget() == player,
                                    "source must directly acquire the target again before the roster change");
                            helper.runAfterDelay(2, () -> {
                                try {
                                    require(nearFollower.getTarget() == player
                                                    && GroupTargetSharingManager.ownsTarget(nearFollower, player),
                                            "the restored config must allow a new lease before the roster change");
                                    require(GroupTargetSharingManager.allowSharedMelee(nearFollower, player),
                                            "the combat permit must be valid before the roster changes");
                                    setGroup(source, List.of(farFollower));
                                    MobGroupManager.GroupRoster changedRoster = MobGroupManager.getGroupRoster(source);
                                    require(changedRoster != null && !changedRoster.members().contains(nearFollower.getUUID()),
                                            "test roster change must remove the former recipient");
                                    require(!GroupTargetSharingManager.allowSharedMelee(nearFollower, player),
                                            "same-tick roster changes must invalidate a cached combat permit");
                                    tickGroupSharing(helper);
                                    require(nearFollower.getTarget() == null && !GroupTargetSharingManager.ownsTarget(nearFollower, player),
                                            "recipient must lose its shared target when the roster epoch changes");
                                    helper.succeed();
                                } catch (Throwable failure) {
                                    helper.fail("target-sharing roster fixture failed: " + failure);
                                } finally {
                                    cleanup.run();
                                }
                            });
                        } catch (Throwable failure) {
                            cleanup.run();
                            helper.fail("target-sharing pursuit fixture failed: " + failure);
                        }
                    });
                } catch (Throwable failure) {
                    cleanup.run();
                    helper.fail("target-sharing fixture failed: " + failure.getMessage());
                }
            });
        } catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static void assertGroupDistanceReload(GameTestHelper helper, Mob source, Mob recipient, ServerPlayer player)
            throws Exception {
        require(recipient.getTarget() == player && GroupTargetSharingManager.ownsTarget(recipient, player),
                "the group distance reload must start with a live sharing lease on the same recipient");
        double previousGroupDistance = SoundAttractConfig.COMMON.groupDistance.get();
        double distanceSquared = recipient.distanceToSqr(source);
        require(distanceSquared > 1.0 && distanceSquared <= previousGroupDistance * previousGroupDistance,
                "the recipient must be outside the reduced group distance only");
        MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(source);
        require(roster != null && roster.members().contains(recipient.getUUID()),
                "the relevant reload must start with the recipient in the source roster");
        withReloadedGeneralSetting("groups.groupDistance", 1.0, () -> {
            require(SoundAttractConfig.COMMON.groupDistance.get() == 1.0,
                    "the group distance must shrink through the Forge reload");
            require(MobGroupManager.getGroupRoster(source) == roster,
                    "the relevant reload fixture must keep the same roster epoch");
            require(StealthDetectionEvents.canReceiveSharedTarget(recipient, player),
                    "the recipient must still pass its own perception range after the group distance changes");
            tickGroupSharing(helper);
            require(recipient.getTarget() == null && !GroupTargetSharingManager.ownsTarget(recipient, player),
                    "a reduced group distance must revoke the shared lease while the recipient still perceives the target");
            require(source.getTarget() == player, "the reload must preserve the source's directly acquired target");
        });
        require(SoundAttractConfig.COMMON.groupDistance.get() == previousGroupDistance,
                "the original group distance must be restored after the isolated reload");
    }

    private static void tickGroupSharing(GameTestHelper helper) {
        GroupTargetSharingManager.onServerTick(new TickEvent.ServerTickEvent(
                TickEvent.Phase.END, () -> true, helper.getLevel().getServer()));
    }

    private static void withReloadedGeneralSetting(String key, Object value, Runnable check) throws Exception {
        ModConfig config = ConfigTracker.INSTANCE.fileMap().get("soundattract/general.toml");
        require(config != null && config.getSpec() == GeneralConfig.SPEC && config.getConfigData() != null,
                "Forge must have loaded the registered general.toml");
        CommentedConfig original = config.getConfigData();
        var setConfigData = ModConfig.class.getDeclaredMethod("setConfigData", CommentedConfig.class);
        setConfigData.setAccessible(true);
        var fireEvent = ModConfig.class.getDeclaredMethod("fireEvent", IConfigEvent.class);
        fireEvent.setAccessible(true);
        var watcherType = Class.forName("net.minecraftforge.fml.config.ConfigFileTypeHandler$ConfigWatcher");
        var watcherConstructor = watcherType.getDeclaredConstructor(ModConfig.class, CommentedFileConfig.class, ClassLoader.class);
        watcherConstructor.setAccessible(true);
        var directory = Files.createTempDirectory("soundattract-general-reload-");
        var path = directory.resolve("general.toml");
        try (CommentedFileConfig staged = CommentedFileConfig.builder(path).sync().build()) {
            TomlFormat.instance().createWriter().write(original, path, WritingMode.REPLACE);
            staged.load();
            try {
                setConfigData.invoke(config, staged);
                staged.set(key, value);
                staged.save();
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Runnable watcher = (Runnable) watcherConstructor.newInstance(config, staged, loader);
                watcher.run();
                check.run();
            } finally {
                setConfigData.invoke(config, original);
                fireEvent.invoke(config, IConfigEvent.reloading(config));
            }
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }

    private static void removeTargetAcquisitionGoals(Mob mob) {
        List<net.minecraft.world.entity.ai.goal.Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof TargetGoal) goals.add(wrapped.getGoal());
        }
        for (var goal : goals) mob.targetSelector.removeGoal(goal);
    }

    private static ServerPlayer makeTestPlayer(net.minecraft.server.level.ServerLevel level) throws Exception {
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(java.util.UUID.randomUUID(), "target-sharing-test-player"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        Field channelField = Connection.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(connection, new EmbeddedChannel());
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    private static void setGroup(Mob leader, List<Mob> followers) throws Exception {
        Map<java.util.UUID, java.util.UUID> assignments = new HashMap<>();
        assignments.put(leader.getUUID(), leader.getUUID());
        for (Mob follower : followers) assignments.put(follower.getUUID(), leader.getUUID());
        Field generationField = MobGroupManager.class.getDeclaredField("serverGeneration");
        generationField.setAccessible(true);
        long generation = ((AtomicLong) generationField.get(null)).get();
        WorkerScheduler.GroupComputeResult result = new WorkerScheduler.GroupComputeResult(
                leader.level().dimension().location(), assignments, Map.of(), Set.of(), generation);
        MobGroupManager.applyGroupResult((net.minecraft.server.level.ServerLevel) leader.level(), result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void recordSelection(String selector, String registryName) {
        long registered = GameTestRegistry.getAllTestFunctions().stream()
                .filter(function -> function.getTestName().equalsIgnoreCase(registryName)).count();
        require(registered == 1L, "expected one registered GameTest for " + selector + " but found " + registered);
        SoundAttractMod.LOGGER.info("GAMETEST_SELECTION selector={} registered={}", selector, registered);
    }
}
