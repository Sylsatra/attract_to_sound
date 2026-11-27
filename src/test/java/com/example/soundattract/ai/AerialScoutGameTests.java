package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.example.soundattract.worker.WorkerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
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

@GameTestHolder("soundattract_aerialscout")
public final class AerialScoutGameTests {
    @GameTest(template = "empty", timeoutTicks = 800)
    public void completedSwoopSamplesScentOnceAndDoesNotRetryDeniedBudget(GameTestHelper helper) {
        recordSelection("com.example.soundattract.ai.AerialScoutGameTests#completedSwoopSamplesScentOnceAndDoesNotRetryDeniedBudget",
                "aerialscoutgametests.completedswoopsamplesscentonceanddoesnotretrydeniedbudget");
        var level = helper.getLevel();
        boolean previousBrain = SoundAttractConfig.COMMON.enableSecondBrain.get();
        boolean previousSmartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        boolean previousScent = SoundAttractConfig.COMMON.enableScentSystem.get();
        int previousCueBudget = SoundAttractConfig.COMMON.cueScansPerTick.get();
        int previousScentBudget = SoundAttractConfig.COMMON.scentScanBudgetPerTick.get();
        List<? extends String> previousEligible = SoundAttractConfig.COMMON.scentEligibleMobs.get();
        List<String> eligible = new ArrayList<>(previousEligible);
        if (!eligible.contains("minecraft:parrot")) eligible.add("minecraft:parrot");
        SoundAttractConfig.COMMON.enableSecondBrain.set(true);
        SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(false);
        SoundAttractConfig.COMMON.enableScentSystem.set(false);
        SoundAttractConfig.COMMON.cueScansPerTick.set(128);
        SoundAttractConfig.COMMON.scentScanBudgetPerTick.set(128);
        SoundAttractConfig.COMMON.scentEligibleMobs.set(eligible);

        Mob leader = helper.spawn(EntityType.ZOMBIE, 10, 138, 9);
        Mob parrot = helper.spawn(EntityType.PARROT, 8, 139, 8);
        parrot.setNoGravity(true);
        removeGoals(leader);
        removeGoals(parrot);
        SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(true);
        SoundAttractConfig.COMMON.enableScentSystem.set(true);
        ScentManager manager = ScentManager.get(level).orElse(null);
        AerialEdgeScoutGoal goal = new AerialEdgeScoutGoal(parrot);
        BlockPos landingPad = helper.absolutePos(new BlockPos(8, 125, 8));
        long[] baselineAccepted = new long[1];
        long[] phaseDeadline = new long[1];
        int[] phase = new int[1];
        long[] firstTick = {level.getGameTime()};
        boolean[] started = new boolean[1];
        boolean[] finished = new boolean[1];
        Path[] ownedPath = new Path[1];
        int[] startedCount = new int[1];

        for (int x = 3; x <= 20; x++) {
            for (int z = 3; z <= 18; z++) {
                for (int y = 125; y <= 143; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                helper.setBlock(new BlockPos(x, 124, z), Blocks.STONE.defaultBlockState());
            }
        }
        BlockPos landingFloor = landingPad.below();
        require(!level.getBlockState(landingFloor).isAir(), "the aerial fixture must place a floor below its landing pad");
        int heightmapLandingY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                landingPad.getX(), landingPad.getZ());
        require(heightmapLandingY == landingPad.getY(), "scout heightmap landing must match its scent pad: heightmapY="
                + heightmapLandingY + " scentPad=" + landingPad);

        Runnable cleanup = () -> {
            SoundAttractConfig.COMMON.enableSecondBrain.set(previousBrain);
            SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(previousSmartEdge);
            SoundAttractConfig.COMMON.enableScentSystem.set(previousScent);
            SoundAttractConfig.COMMON.cueScansPerTick.set(previousCueBudget);
            SoundAttractConfig.COMMON.scentScanBudgetPerTick.set(previousScentBudget);
            SoundAttractConfig.COMMON.scentEligibleMobs.set(previousEligible);
            leader.discard();
            parrot.discard();
        };

        try {
            require(manager != null, "GameTest level must expose the scent capability");
            setGroup(leader, List.of(parrot));
            require(MobGroupManager.getLeader(parrot) == leader && MobGroupManager.isEdgeMob(parrot),
                    "parrot must be installed as an edge member of the test leader");
            manager.addScentNode(new ScentNode(Vec3.atCenterOf(landingPad), level.getGameTime(), 1.0F,
                    UUID.randomUUID(), ScentSourceType.PLAYER_WALK));
            baselineAccepted[0] = ClueDirector.stats(level.getServer()).accepted();
            require(baselineAccepted[0] > 0L, "real player scent must enter the director as baseline raw evidence");
        } catch (Throwable failure) {
            cleanup.run();
            throw new AssertionError(failure);
        }

        Runnable[] advance = new Runnable[1];
        advance[0] = () -> {
            if (finished[0]) return;
            try {
                long now = level.getGameTime();
                setGroup(leader, List.of(parrot));
                if (phase[0] == 0 && !started[0]) {
                    parrot.getNavigation().stop();
                    parrot.setDeltaMovement(Vec3.ZERO);
                    parrot.setPos(landingPad.getX() + 0.5, landingPad.getY() + 2.0, landingPad.getZ() + 0.5);
                }
                if (phase[0] == 0 && now - firstTick[0] > 100L && !field(goal, "initialized")) {
                    throw new AssertionError("aerial scout did not become eligible: alive=" + parrot.isAlive()
                            + " removed=" + parrot.isRemoved() + " vehicle=" + parrot.isVehicle()
                            + " sleeping=" + parrot.isSleeping() + " target=" + parrot.getTarget()
                            + " nav=" + parrot.getNavigation().getClass().getSimpleName() + "/"
                            + parrot.getNavigation().isInProgress() + " leader=" + MobGroupManager.getLeader(parrot)
                            + " edge=" + MobGroupManager.isEdgeMob(parrot) + " controlled="
                            + LivingWallCoordinator.isControlled(parrot) + " smart="
                            + SoundAttractConfig.COMMON.edgeMobSmartBehavior.get() + " scent="
                            + SoundAttractConfig.COMMON.enableScentSystem.get() + " eligible="
                            + SoundAttractConfig.COMMON.scentEligibleMobs.get().contains("minecraft:parrot"));
                }
                if (phase[0] == 0 || phase[0] == 1 || phase[0] == 3) {
                    if (phase[0] == 1 && ClueDirector.getActiveHint(leader) != null) {
                        ClueDirector.Hint hint = ClueDirector.getActiveHint(leader);
                        require(hint != null && hint.position().equals(landingPad),
                                "the leader must receive the selected scout scent as its group clue");
                        ClueDirector.invalidatePolicy();
                        ClueDirector.stats(level.getServer());
                        manager.clear();
                        parrot.getNavigation().stop();
                        parrot.setDeltaMovement(Vec3.ZERO);
                        parrot.setPos(landingPad.getX() + 0.5, landingPad.getY() + 2.0, landingPad.getZ() + 0.5);
                        phase[0] = 2;
                        setSwoopSchedule(goal, now);
                        return;
                    }
                    if (phase[0] != 1 && !started[0] && goal.canUse() && field(goal, "swooping")) {
                        if (phase[0] == 2) {
                            manager.addScentNode(new ScentNode(Vec3.atCenterOf(landingPad), level.getGameTime(), 1.0F,
                                    UUID.randomUUID(), ScentSourceType.PLAYER_WALK));
                            baselineAccepted[0] = ClueDirector.stats(level.getServer()).accepted();
                        }
                        goal.start();
                        started[0] = true;
                        startedCount[0]++;
                        ownedPath[0] = (Path) fieldValue(goal, "ownedNavigationPath");
                        require(ownedPath[0] != null && parrot.getNavigation().getPath() == ownedPath[0],
                                "the production aerial goal must own a real flying path before descent");
                        require(ownedPath[0].canReach() && parrot.position().distanceToSqr(landingPad.getX() + 0.5,
                                        landingPad.getY(), landingPad.getZ() + 0.5) > 2.25,
                                "the scout must begin outside landing range with a path that reaches its pad");
                    }
                    if (started[0]) {
                        goal.tick();
                        boolean continuing = goal.canContinueToUse();
                        if (!continuing) {
                            boolean arrived = field(goal, "arrived");
                            if (phase[0] == 3) {
                                require(!arrived, "foreign path preemption must not count as a completed swoop");
                                require(parrot.getNavigation().getPath() == ownedPath[0],
                                        "foreign path must remain installed after the scout goal yields");
                                goal.stop();
                                require(parrot.getNavigation().getPath() == ownedPath[0],
                                        "stopping the scout goal must preserve the exact foreign path object");
                                require(ClueDirector.stats(level.getServer()).accepted() == baselineAccepted[0],
                                        "an aborted foreign-path swoop must not publish scent evidence");
                                finished[0] = true;
                                helper.succeed();
                                cleanup.run();
                                return;
                            }
                            require(arrived, "scent sampling must follow an accepted path that reached its landing radius: pos="
                                    + parrot.position() + " destination=" + fieldValue(goal, "destination")
                                    + " edge=" + MobGroupManager.isEdgeMob(parrot) + " leader="
                                    + MobGroupManager.getLeader(parrot) + " navInProgress="
                                    + parrot.getNavigation().isInProgress() + " path="
                                    + parrot.getNavigation().getPath() + " owned=" + ownedPath[0]);
                            require(parrot.getNavigation().getPath() == ownedPath[0],
                                    "the completed landing must be reached on the exact path owned by the scout");
                            if (phase[0] == 0) {
                                goal.stop();
                                require(ClueDirector.stats(level.getServer()).accepted() == baselineAccepted[0] + 1L,
                                        "one completed landing must publish exactly one scoped scout scent report");
                                ClueDirector.registerInterest(leader);
                                phase[0] = 1;
                            } else {
                                SoundAttractConfig.COMMON.cueScansPerTick.set(1);
                                SoundAttractConfig.COMMON.scentScanBudgetPerTick.set(1);
                                while (CueScanBudget.tryAcquire(leader)) {}
                                require(!CueScanBudget.tryAcquire(leader),
                                        "the test must exhaust the shared per-server cue slot before the denied landing sample");
                                goal.stop();
                                require(ClueDirector.stats(level.getServer()).accepted() == baselineAccepted[0],
                                        "a budget-denied landing must not publish a scoped scent report");
                                phase[0] = 4;
                                phaseDeadline[0] = now + 35L;
                            }
                            started[0] = false;
                        }
                    }
                } else if (phase[0] == 2) {
                    if (!started[0] && goal.canUse() && field(goal, "swooping")) {
                        manager.addScentNode(new ScentNode(Vec3.atCenterOf(landingPad), level.getGameTime(), 1.0F,
                                UUID.randomUUID(), ScentSourceType.PLAYER_WALK));
                        baselineAccepted[0] = ClueDirector.stats(level.getServer()).accepted();
                        goal.start();
                        started[0] = true;
                        startedCount[0]++;
                        ownedPath[0] = (Path) fieldValue(goal, "ownedNavigationPath");
                        require(ownedPath[0] != null && parrot.getNavigation().getPath() == ownedPath[0],
                                "second production swoop must own its path before the denied sample phase");
                        require(ownedPath[0].canReach(), "the denied-budget swoop must use a complete route to the landing pad");
                    }
                    if (!started[0]) {
                        parrot.setDeltaMovement(Vec3.ZERO);
                        parrot.setPos(landingPad.getX() + 0.5, landingPad.getY() + 2.0, landingPad.getZ() + 0.5);
                    }
                    if (started[0]) {
                        goal.tick();
                        boolean continuing = goal.canContinueToUse();
                        if (!continuing) {
                            require(field(goal, "arrived"), "budget denial must be measured at a real landing");
                            SoundAttractConfig.COMMON.cueScansPerTick.set(1);
                            SoundAttractConfig.COMMON.scentScanBudgetPerTick.set(1);
                            while (CueScanBudget.tryAcquire(leader)) {}
                            require(!CueScanBudget.tryAcquire(leader),
                                    "the test must exhaust the shared per-server cue slot before the denied landing sample");
                            goal.stop();
                            require(ClueDirector.stats(level.getServer()).accepted() == baselineAccepted[0],
                                    "a budget-denied landing must not publish a scoped scent report");
                            phase[0] = 4;
                            phaseDeadline[0] = now + 35L;
                            started[0] = false;
                        }
                    }
                } else if (phase[0] == 4) {
                    require(now <= phaseDeadline[0], "the aerial goal exceeded its post-sample yield window");
                    if (goal.canUse()) {
                        require(!field(goal, "swooping"), "a budget-denied sample must not retry during the yield interval");
                    }
                    require(field(goal, "sampleAttempted"),
                            "a denied scent search must remain latched for the current swoop until its next scheduled cycle");
                    if (now >= phaseDeadline[0]) {
                        parrot.getNavigation().stop();
                        parrot.setDeltaMovement(Vec3.ZERO);
                        parrot.setPos(landingPad.getX() + 0.5, landingPad.getY() + 2.0, landingPad.getZ() + 0.5);
                        phase[0] = 5;
                        setSwoopSchedule(goal, now);
                        started[0] = false;
                    }
                } else if (phase[0] == 5) {
                    if (!started[0] && goal.canUse() && field(goal, "swooping")) {
                        goal.start();
                        started[0] = true;
                        startedCount[0]++;
                        ownedPath[0] = (Path) fieldValue(goal, "ownedNavigationPath");
                        require(ownedPath[0] != null, "foreign-path phase must begin with a scout-owned path");
                        parrot.getNavigation().stop();
                        BlockPos foreignTarget = helper.absolutePos(new BlockPos(16, 90, 14));
                        Path foreignPath = parrot.getNavigation().createPath(foreignTarget, 0);
                        require(foreignPath != null && foreignPath != ownedPath[0]
                                        && parrot.getNavigation().moveTo(foreignPath, 1.0D),
                                "the fixture must install a distinct foreign path to preempt the scout");
                        ownedPath[0] = foreignPath;
                        phase[0] = 3;
                    }
                    if (!started[0]) {
                        parrot.setDeltaMovement(Vec3.ZERO);
                        parrot.setPos(landingPad.getX() + 0.5, landingPad.getY() + 2.0, landingPad.getZ() + 0.5);
                    }
                }
            } catch (Throwable failure) {
                cleanup.run();
                helper.fail("aerial scout fixture failed in phase " + phase[0] + " after " + startedCount[0]
                        + " starts: " + failure.getMessage());
            }
        };
        helper.onEachTick(advance[0]);
        helper.runAfterDelay(790, () -> {
            if (finished[0]) return;
            try {
                helper.fail("aerial scout phase stalled: phase=" + phase[0] + " started=" + started[0]
                        + " starts=" + startedCount[0] + " pos=" + parrot.position()
                        + " nextScout=" + fieldValue(goal, "nextScoutTick")
                        + " nextSwoop=" + fieldValue(goal, "nextSwoopTick")
                        + " now=" + level.getGameTime() + " navigationInProgress=" + parrot.getNavigation().isInProgress()
                        + " controlled=" + LivingWallCoordinator.isControlled(parrot)
                        + " edge=" + MobGroupManager.isEdgeMob(parrot)
                        + " smart=" + SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()
                        + " flyingNavigation=" + (parrot.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation)
                        + " path=" + parrot.getNavigation().getTargetPos());
            } catch (Throwable failure) {
                helper.fail("aerial scout phase stalled in phase " + phase[0] + ": " + failure.getMessage());
            }
            cleanup.run();
        });
    }

    private static void setGroup(Mob leader, List<Mob> followers) throws Exception {
        Map<UUID, UUID> assignments = new HashMap<>();
        assignments.put(leader.getUUID(), leader.getUUID());
        for (Mob follower : followers) assignments.put(follower.getUUID(), leader.getUUID());
        Field generationField = MobGroupManager.class.getDeclaredField("serverGeneration");
        generationField.setAccessible(true);
        long generation = ((AtomicLong) generationField.get(null)).get();
        Map<UUID, Set<UUID>> edges = Map.of(leader.getUUID(), Set.copyOf(
                followers.stream().map(Mob::getUUID).toList()));
        WorkerScheduler.GroupComputeResult edgedResult = new WorkerScheduler.GroupComputeResult(
                leader.level().dimension().location(), assignments, edges, Set.of(), generation);
        MobGroupManager.applyGroupResult((net.minecraft.server.level.ServerLevel) leader.level(), edgedResult);
    }

    private static void removeGoals(Mob mob) {
        List<net.minecraft.world.entity.ai.goal.Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) goals.add(wrapped.getGoal());
        for (var goal : goals) mob.goalSelector.removeGoal(goal);
        mob.getNavigation().stop();
    }

    private static boolean field(AerialEdgeScoutGoal goal, String name) throws Exception {
        return (boolean) fieldValue(goal, name);
    }

    private static Object fieldValue(AerialEdgeScoutGoal goal, String name) throws Exception {
        Field field = AerialEdgeScoutGoal.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(goal);
    }

    private static void setSwoopSchedule(AerialEdgeScoutGoal goal, long tick) throws Exception {
        for (String name : List.of("nextScoutTick", "nextSwoopTick")) {
            Field field = AerialEdgeScoutGoal.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setLong(goal, tick);
        }
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
