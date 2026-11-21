package com.example.soundattract.ai;

import com.example.soundattract.worker.WorkerScheduler;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("soundattract_highweight")
public final class HighWeightOverrideGameTest {
    @GameTest(template = "empty", batch = "highWeightOverride", timeoutTicks = 100)
    public void overrideLookupTracksLiveGoalSelectorMembership(GameTestHelper helper) throws Exception {
        Mob leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Mob follower = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        setGroup(leader, follower);
        require(MobGroupManager.getLeader(follower) == leader, "fixture did not install the follower leader");
        require(!MobGroupManager.isMobInHighWeightOverride(follower), "inactive goals reported an override");

        FollowerEdgeRelayGoal leaderEdge = new FollowerEdgeRelayGoal(leader, 1.0D) {
            @Override
            public boolean isHighWeightOverrideActive() {
                return true;
            }
        };
        leader.goalSelector.addGoal(4, leaderEdge);
        require(MobGroupManager.isMobInHighWeightOverride(leader), "active edge relay was missed on its owner");
        require(!MobGroupManager.isMobInHighWeightOverride(follower),
                "leader edge relay should not be inherited as a group override");
        leader.goalSelector.removeAllGoals(goal -> goal == leaderEdge);

        LeaderAttractionGoal leaderOverride = new LeaderAttractionGoal(leader, 1.0D) {
            @Override
            public boolean isHighWeightOverrideActive() {
                return true;
            }
        };
        leader.goalSelector.addGoal(4, leaderOverride);
        require(MobGroupManager.isMobInHighWeightOverride(follower),
                "active leader attraction override was not inherited immediately");
        leader.goalSelector.removeAllGoals(goal -> goal == leaderOverride);
        require(!MobGroupManager.isMobInHighWeightOverride(follower),
                "removed leader goal remained visible as an override");

        AttractionGoal ownOverride = new AttractionGoal(follower, 1.0D) {
            @Override
            public boolean isHighWeightOverrideActive() {
                return true;
            }
        };
        follower.goalSelector.addGoal(4, ownOverride);
        require(MobGroupManager.isMobInHighWeightOverride(follower), "active own attraction override was missed");
        follower.goalSelector.removeAllGoals(goal -> goal == ownOverride);
        require(!MobGroupManager.isMobInHighWeightOverride(follower),
                "removed own goal remained visible as an override");
        helper.succeed();
    }

    private static void setGroup(Mob leader, Mob follower) throws Exception {
        Map<UUID, UUID> assignments = new HashMap<>();
        assignments.put(leader.getUUID(), leader.getUUID());
        assignments.put(follower.getUUID(), leader.getUUID());
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
}
