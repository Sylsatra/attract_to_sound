package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.worker.WorkerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder("soundattract_scouthandoff")
public final class ScoutHandoffGameTests {
    @GameTest(template = "empty", timeoutTicks = 180)
    public void validatedSoundAndScentStayWithCurrentGroupLeaderAndFollowersTrailLeader(GameTestHelper helper) {
        recordSelection("com.example.soundattract.ai.ScoutHandoffGameTests#validatedSoundAndScentStayWithCurrentGroupLeaderAndFollowersTrailLeader",
                "scouthandoffgametests.validatedsoundandscentstaywithcurrentgroupleaderandfollowerstrailleader");
        var level = helper.getLevel();
        boolean previousBrain = SoundAttractConfig.COMMON.enableSecondBrain.get();
        boolean previousSmartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        boolean previousScent = SoundAttractConfig.COMMON.enableScentSystem.get();
        int previousCueBudget = SoundAttractConfig.COMMON.cueScansPerTick.get();
        int previousScentBudget = SoundAttractConfig.COMMON.scentScanBudgetPerTick.get();
        SoundAttractConfig.COMMON.enableSecondBrain.set(true);
        SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(true);
        SoundAttractConfig.COMMON.enableScentSystem.set(true);
        SoundAttractConfig.COMMON.cueScansPerTick.set(128);
        SoundAttractConfig.COMMON.scentScanBudgetPerTick.set(128);

        Mob leader = helper.spawn(EntityType.ZOMBIE, 8, 81, 6);
        Mob scout = helper.spawn(EntityType.ZOMBIE, 9, 81, 8);
        Mob follower = helper.spawn(EntityType.ZOMBIE, 8, 81, 14);
        Mob outsider = helper.spawn(EntityType.ZOMBIE, 22, 81, 8);
        Mob outsiderMember = helper.spawn(EntityType.ZOMBIE, 22, 81, 12);
        for (int x = 4; x <= 25; x++) {
            for (int z = 3; z <= 16; z++) {
                for (int y = 81; y <= 85; y++) {
                    helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
                helper.setBlock(new BlockPos(x, 80, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
        }
        removeGoals(leader);
        removeGoals(scout);
        removeGoals(follower);
        removeGoals(outsider);
        removeGoals(outsiderMember);
        try {
            installGroups(level, leader, scout, follower, outsider, outsiderMember);
            require(MobGroupManager.getLeader(scout) == leader && MobGroupManager.isEdgeMob(scout),
                    "fixture must designate the sound/scent observer as the current edge scout");
            require(MobGroupManager.getGroupRoster(leader) != null
                            && MobGroupManager.getGroupRoster(leader).members().contains(follower.getUUID()),
                    "fixture follower must share the scout leader's current roster");

            BlockPos soundPosition = helper.absolutePos(new BlockPos(18, 81, 8));
            SoundTracker.addSound(null, soundPosition, level.dimension().location().toString(),
                    40.0, 1.0, 120, "soundattract:player_action.walking");
            FollowerEdgeRelayGoal relayGoal = new FollowerEdgeRelayGoal(scout, 1.0);
            require(relayGoal.canUse(), "the real edge relay goal must select the live SoundTracker record");
            relayGoal.start();
            require(scout.getTarget() == null, "a scout cue must not assign a direct combat target");
            require(ClueDirector.registerInterest(leader), "current group leader must register clue interest");
            require(ClueDirector.registerInterest(outsider), "separate leader must register as a valid negative control");

            helper.runAfterDelay(22, () -> {
                try {
                    ClueDirector.Hint soundHint = ClueDirector.getActiveHint(leader);
                    require(soundHint != null && soundHint.type() == ClueDirector.Type.SOUND
                                    && soundHint.position().equals(soundPosition),
                            "a SoundTracker-selected edge cue must reach its current leader; stats="
                                    + ClueDirector.stats(level.getServer()) + " leader=" + MobGroupManager.getLeader(scout)
                                    + " roster=" + MobGroupManager.getGroupRoster(leader)
                                    + " perf=" + StealthDetectionEvents.getPerfTier(level.getServer()));
                    require(ClueDirector.getActiveHint(outsider) == null,
                            "a valid leader outside the scout roster must not receive the cue");
                    require(leader.getTarget() == null && follower.getTarget() == null && outsider.getTarget() == null,
                            "shared clue handoff and interest must leave combat target assignment untouched");

                    LeaderAttractionGoal leaderGoal = new LeaderAttractionGoal(leader, 1.0);
                    leader.goalSelector.addGoal(3, leaderGoal);
                    require(leaderGoal.canUse(), "the leader's normal attraction goal must consume the director hint");
                    leaderGoal.start();
                    leaderGoal.tick();
                    require(LeaderAttractionGoal.ownsActiveHintPath(leader, soundHint),
                            "the leader must own an active path for its clue before followers trail it");
                    BlockPos trail = GroupTargetSharingManager.getClueTrailPosition(follower, leader);
                    require(trail != null && trail.distSqr(soundHint.position()) > 4.0
                                    && trail.distSqr(leader.blockPosition()) <= 25.0,
                            "follower trail destination must stay near the clue-owning leader, not the clue itself");
                    FollowLeaderGoal followGoal = new FollowLeaderGoal(follower, 1.0);
                    follower.goalSelector.addGoal(4, followGoal);
                    require(followGoal.canUse(), "same-roster follower must become eligible to trail the clue owner");
                    followGoal.start();
                    followGoal.tick();
                    require(follower.getTarget() == null,
                            "following a leader's clue path must not set a combat target on the follower");
                    require(follower.getNavigation().getPath() != null
                                    && follower.getNavigation().getTargetPos() != null
                                    && follower.getNavigation().getTargetPos().distSqr(soundHint.position()) > 4.0,
                            "follower navigation must trail the moving leader instead of pathing directly to its clue");

                    followGoal.stop();
                    leaderGoal.stop();
                    ClueDirector.cancelInterest(leader);
                    ClueDirector.cancelInterest(outsider);
                    relayGoal.stop();
                    SoundTracker.removeSoundAt(soundPosition, level.dimension().location().toString(),
                            "soundattract:player_action.walking");

                    try {
                        ScentManager scentManager = ScentManager.get(level).orElse(null);
                        require(scentManager != null, "GameTest level must expose the production scent capability");
                        BlockPos scentPosition = helper.absolutePos(new BlockPos(12, 81, 8));
                        ScentNode scentNode = new ScentNode(Vec3.atCenterOf(scentPosition), level.getGameTime(),
                                1.0f, UUID.randomUUID(), ScentSourceType.PLAYER_WALK);
                        scentManager.addScentNode(scentNode);
                        FollowScentGoal scentGoal = new FollowScentGoal((net.minecraft.world.entity.PathfinderMob) scout);
                        boolean selected = false;
                        for (int attempt = 0; attempt < 80 && !selected; attempt++) {
                            selected = scentGoal.canUse();
                        }
                        require(selected, "the production scent goal must select an eligible fresh scent node");
                        require(scout.getTarget() == null, "scent selection must not assign a direct combat target");
                        require(ClueDirector.registerInterest(leader), "current leader must register for the selected scent cue");
                        require(ClueDirector.registerInterest(outsider), "outsider must remain a valid scent negative control");
                        helper.runAfterDelay(12, () -> {
                            try {
                                ClueDirector.Hint scentHint = ClueDirector.getActiveHint(leader);
                                require(scentHint != null && scentHint.type() == ClueDirector.Type.SCENT
                                                && scentHint.position().equals(scentPosition),
                                        "the exact node selected by FollowScentGoal must reach its current leader");
                                require(ClueDirector.getActiveHint(outsider) == null,
                                        "a different roster must not receive a selected scent node");
                                require(leader.getTarget() == null && scout.getTarget() == null
                                                && follower.getTarget() == null && outsider.getTarget() == null,
                                        "scout scent handoff must remain an investigation clue, not a combat target");
                                helper.succeed();
                            } catch (Throwable failure) {
                                helper.fail("scout scent handoff fixture failed: " + failure.getMessage());
                            } finally {
                                cleanup(previousBrain, previousSmartEdge, previousScent, previousCueBudget, previousScentBudget,
                                        leader, scout, follower, outsider, outsiderMember);
                            }
                        });
                    } catch (Throwable failure) {
                        helper.fail("scout scent setup failed: " + failure.getMessage());
                        cleanup(previousBrain, previousSmartEdge, previousScent, previousCueBudget, previousScentBudget,
                                leader, scout, follower, outsider, outsiderMember);
                    }
                } catch (Throwable failure) {
                    helper.fail("scout sound handoff fixture failed: " + failure.getMessage());
                    cleanup(previousBrain, previousSmartEdge, previousScent, previousCueBudget, previousScentBudget,
                            leader, scout, follower, outsider, outsiderMember);
                }
            });
        } catch (Throwable failure) {
            cleanup(previousBrain, previousSmartEdge, previousScent, previousCueBudget, previousScentBudget,
                    leader, scout, follower, outsider, outsiderMember);
            helper.fail("scout handoff setup failed: " + failure.getMessage());
        }
    }

    private static void installGroups(net.minecraft.server.level.ServerLevel level, Mob leader, Mob scout,
                                      Mob follower, Mob outsider, Mob outsiderMember) throws Exception {
        Map<UUID, UUID> assignments = new HashMap<>();
        assignments.put(leader.getUUID(), leader.getUUID());
        assignments.put(scout.getUUID(), leader.getUUID());
        assignments.put(follower.getUUID(), leader.getUUID());
        assignments.put(outsider.getUUID(), outsider.getUUID());
        assignments.put(outsiderMember.getUUID(), outsider.getUUID());
        Map<UUID, Set<UUID>> edges = Map.of(leader.getUUID(), Set.of(scout.getUUID()));
        Field generationField = MobGroupManager.class.getDeclaredField("serverGeneration");
        generationField.setAccessible(true);
        long generation = ((AtomicLong) generationField.get(null)).get();
        WorkerScheduler.GroupComputeResult result = new WorkerScheduler.GroupComputeResult(
                level.dimension().location(), assignments, edges, Set.of(), generation);
        MobGroupManager.applyGroupResult(level, result);
    }

    private static void removeGoals(Mob mob) {
        List<net.minecraft.world.entity.ai.goal.Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) goals.add(wrapped.getGoal());
        for (var goal : goals) mob.targetSelector.removeGoal(goal);
        goals.clear();
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) goals.add(wrapped.getGoal());
        for (var goal : goals) mob.goalSelector.removeGoal(goal);
    }

    private static void cleanup(boolean previousBrain, boolean previousSmartEdge, boolean previousScent,
                                int previousCueBudget, int previousScentBudget,
                                Mob leader, Mob scout, Mob follower, Mob outsider, Mob outsiderMember) {
        SoundAttractConfig.COMMON.enableSecondBrain.set(previousBrain);
        SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(previousSmartEdge);
        SoundAttractConfig.COMMON.enableScentSystem.set(previousScent);
        SoundAttractConfig.COMMON.cueScansPerTick.set(previousCueBudget);
        SoundAttractConfig.COMMON.scentScanBudgetPerTick.set(previousScentBudget);
        leader.discard();
        scout.discard();
        follower.discard();
        outsider.discard();
        outsiderMember.discard();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void recordSelection(String selector, String registryName) {
        long registered = GameTestRegistry.getAllTestFunctions().stream()
                .filter(function -> function.testName().equalsIgnoreCase(registryName)).count();
        require(registered == 1L, "expected one registered GameTest for " + selector + " but found " + registered);
        SoundAttractMod.LOGGER.info("GAMETEST_SELECTION selector={} registered={}", selector, registered);
    }
}
