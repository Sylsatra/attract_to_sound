package com.example.soundattract.ai;

import com.example.soundattract.config.SoundAttractConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("soundattract")
public final class FollowLeaderSpreadGameTests {
    private static final String LEADER_PURSUITS_FIELD = "LEADER_PURSUITS";

    @GameTest(template = "empty", timeoutTicks = 100)
    public void activeFollowerPursuitSpreadsToAllFourCardinalRoutes(GameTestHelper helper) {
        double oldArrivalDistance = SoundAttractConfig.COMMON.arrivalDistance.get();
        double oldSpreadThreshold = SoundAttractConfig.COMMON.followLeaderMinSoundWeightToSpreadOut.get();
        double oldSpreadDistance = SoundAttractConfig.COMMON.followLeaderSpreadOutDistance.get();
        boolean oldFlowField = SoundAttractConfig.COMMON.enableFlowField.get();
        Mob leader = null;
        List<Mob> followers = new ArrayList<>();
        List<FollowLeaderGoal> goals = new ArrayList<>();
        List<Integer> expectedDirections = new ArrayList<>();
        try {
            SoundAttractConfig.COMMON.arrivalDistance.set(4.0);
            SoundAttractConfig.COMMON.followLeaderMinSoundWeightToSpreadOut.set(0.0);
            SoundAttractConfig.COMMON.followLeaderSpreadOutDistance.set(6.0);
            SoundAttractConfig.COMMON.enableFlowField.set(false);

            BlockPos soundPos = helper.absolutePos(new BlockPos(7, 81, 7));
            for (int x = 1; x <= 13; x++) {
                for (int z = 1; z <= 13; z++) {
                    helper.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
                    for (int y = 1; y <= 15; y++) {
                        helper.setBlock(new BlockPos(x, y + 80, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }

            leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 81, 3);
            Set<Integer> directions = new HashSet<>();
            for (int attempt = 0; attempt < 48 && directions.size() < 4; attempt++) {
                int[][] spawnOffsets = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                        {-1, -1}, {1, 1}, {1, -1}, {-1, 1}};
                int[] spawnOffset = spawnOffsets[attempt % spawnOffsets.length];
                Mob follower = helper.spawn(EntityType.ZOMBIE, 7 + spawnOffset[0], 81, 7 + spawnOffset[1]);
                int expectedDirection = Math.floorMod(follower.getUUID().hashCode() ^ soundPos.hashCode(), 4);
                if (directions.contains(expectedDirection)) {
                    follower.discard();
                    continue;
                }
                followers.add(follower);
                removeGoals(follower);
                follower.setTarget(null);
                follower.getNavigation().stop();
                FollowLeaderGoal goal = new FollowLeaderGoal(follower, 1.0D);
                setField(goal, "leader", leader);
                goals.add(goal);
                expectedDirections.add(expectedDirection);
                directions.add(expectedDirection);
            }
            require(directions.size() == 4,
                    "test setup must obtain followers for all four directions; observed direction indexes=" + directions);

            Mob finalLeader = leader;
            helper.runAfterDelay(2, () -> {
                Map<Mob, Object> pursuitCache = null;
                try {
                    pursuitCache = pursuitCache();
                    Class<?> pursuitType = Class.forName(FollowLeaderGoal.class.getName() + "$LeaderPursuit");
                    Constructor<?> pursuitConstructor = pursuitType.getDeclaredConstructor(
                            long.class, boolean.class, BlockPos.class, double.class, boolean.class);
                    pursuitConstructor.setAccessible(true);
                    pursuitCache.put(finalLeader, pursuitConstructor.newInstance(
                            helper.getLevel().getGameTime(), true, soundPos, 100.0D, false));

                    int updateInterval = Math.max(1, SoundAttractConfig.COMMON.scanCooldownTicks.get() / 2);
                    Set<Integer> observedDirections = new HashSet<>();
                    for (int i = 0; i < goals.size(); i++) {
                        FollowLeaderGoal goal = goals.get(i);
                        Mob follower = followers.get(i);
                        setField(goal, "dynamicTickCounter", updateInterval - 1);
                        goal.tick();

                        Vec3 destination = (Vec3) getField(goal, "lastRandomDest");
                        require(destination != null,
                                "spread goal must choose a destination after reaching the sound");
                        int dx = BlockPos.containing(destination).getX() - soundPos.getX();
                        int dz = BlockPos.containing(destination).getZ() - soundPos.getZ();
                        require((Math.abs(dx) == 6 && dz == 0) || (dx == 0 && Math.abs(dz) == 6),
                                "spread destination must be six blocks along one cardinal axis, got " + destination);
                        int direction = dx == 0 ? (dz > 0 ? 2 : 3) : (dx > 0 ? 0 : 1);
                        require(direction == expectedDirections.get(i),
                                "spread route must match the follower's stable hash assignment");
                        observedDirections.add(direction);
                        require(follower.getNavigation().getPath() != null && !follower.getNavigation().isDone(),
                                "spread destination must produce an active follower navigation path: " + destination
                                        + " start=" + follower.position() + " target=" + follower.getNavigation().getTargetPos()
                                        + " navDone=" + follower.getNavigation().isDone());
                    }
                    require(observedDirections.size() == 4,
                            "followers must navigate along all four cardinal directions: " + observedDirections);
                    helper.succeed();
                } catch (Throwable failure) {
                    helper.fail("four-direction follower spread test failed: " + failure.getMessage());
                } finally {
                    if (pursuitCache != null) pursuitCache.remove(finalLeader);
                    cleanup(oldArrivalDistance, oldSpreadThreshold, oldSpreadDistance, oldFlowField, followers, finalLeader);
                }
            });
        } catch (Throwable failure) {
            helper.fail("four-direction follower spread test failed: " + failure.getMessage());
            cleanup(oldArrivalDistance, oldSpreadThreshold, oldSpreadDistance, oldFlowField, followers, leader);
        }
    }

    private static void cleanup(double oldArrivalDistance, double oldSpreadThreshold, double oldSpreadDistance,
                                boolean oldFlowField, List<Mob> followers, Mob leader) {
        for (Mob follower : followers) follower.discard();
        if (leader != null) leader.discard();
        SoundAttractConfig.COMMON.arrivalDistance.set(oldArrivalDistance);
        SoundAttractConfig.COMMON.followLeaderMinSoundWeightToSpreadOut.set(oldSpreadThreshold);
        SoundAttractConfig.COMMON.followLeaderSpreadOutDistance.set(oldSpreadDistance);
        SoundAttractConfig.COMMON.enableFlowField.set(oldFlowField);
    }

    @SuppressWarnings("unchecked")
    private static Map<Mob, Object> pursuitCache() throws ReflectiveOperationException {
        Field field = FollowLeaderGoal.class.getDeclaredField(LEADER_PURSUITS_FIELD);
        field.setAccessible(true);
        return (Map<Mob, Object>) field.get(null);
    }

    private static void removeGoals(Mob mob) {
        List<Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) goals.add(wrapped.getGoal());
        for (Goal goal : goals) mob.goalSelector.removeGoal(goal);
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setField(Object target, String name, int value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
