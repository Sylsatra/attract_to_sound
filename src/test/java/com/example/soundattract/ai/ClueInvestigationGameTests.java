package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("soundattract_clueinvestigation")
public final class ClueInvestigationGameTests {
    @GameTest(template = "empty", timeoutTicks = 180)
    public void stalledClueTriesOneAlternateAndYieldsToForeignPath(GameTestHelper helper) {
        recordSelection("com.example.soundattract.ai.ClueInvestigationGameTests#stalledClueTriesOneAlternateAndYieldsToForeignPath",
                "clueinvestigationgametests.stalledcluetriesonealternateandyieldstoforeignpath");
        var level = helper.getLevel();
        boolean previousBrain = SoundAttractConfig.COMMON.enableSecondBrain.get();
        boolean previousSmartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        SoundAttractConfig.COMMON.enableSecondBrain.set(true);
        SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(false);
        Mob mob = helper.spawn(EntityType.ZOMBIE, 8, 81, 6);
        removeGoals(mob.targetSelector);
        removeGoals(mob.goalSelector);
        // Keep the asynchronously installed production goal from racing the directly driven fixture goal.
        mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
        helper.onEachTick(() -> mob.goalSelector.disableControlFlag(Goal.Flag.MOVE));
        mob.setTarget(null);
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        mob.getNavigation().stop();
        ClueInvestigationGoal goal = new ClueInvestigationGoal(mob);
        BlockPos clue = helper.absolutePos(new BlockPos(22, 81, 6));
        BlockPos alternate = alternateFor(clue, mob.getUUID());
        BlockPos secondClue = helper.absolutePos(new BlockPos(5, 81, 6));
        for (int x = 2; x <= 28; x++) {
            for (int z = 2; z <= 14; z++) {
                for (int y = 81; y <= 84; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                helper.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
            }
        }
        Runnable cleanup = () -> {
            SoundAttractConfig.COMMON.enableSecondBrain.set(previousBrain);
            SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(previousSmartEdge);
            mob.discard();
        };
        try {
            require(SoundAttractionEvents.getCachedAttractedEntityTypes().contains(mob.getType()),
                    "the zombie fixture must remain eligible for attraction");
            require(ClueDirector.onSoundAccepted(level.dimension().location().toString(), clue, 110, 32.0, 1.0, null) != 0L,
                    "the first sound clue must be accepted");
            require(ClueDirector.registerInterest(mob), "the idle zombie must register clue interest");
            awaitFirstHint(helper, mob, clue, 60, cleanup, () -> {
                try {
                    ClueDirector.Hint firstHint = ClueDirector.getActiveHint(mob);
                    require(firstHint != null && firstHint.position().equals(clue),
                            "the director must dispatch the first clue before investigation starts: stats="
                                    + ClueDirector.stats(level.getServer()) + " target=" + mob.getTarget()
                                    + " alive=" + mob.isAlive() + " removed=" + mob.isRemoved()
                                    + " navigationDone=" + mob.getNavigation().isDone()
                                    + " controlled=" + LivingWallCoordinator.isControlled(mob));
                    require(goal.canUse(), "the production investigation goal must consume the first clue lease"
                            + " target=" + mob.getTarget() + " navigationDone=" + mob.getNavigation().isDone()
                            + " path=" + mob.getNavigation().getPath() + " availableGoals="
                            + mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal().getClass().getSimpleName()).toList()
                            + " runningGoals=" + mob.goalSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning)
                            .map(wrapped -> wrapped.getGoal().getClass().getSimpleName()).toList());
                    goal.start();
                    Path initialPath = mob.getNavigation().getPath();
                    require(initialPath != null && !mob.getNavigation().isDone()
                                    && initialPath == field(goal, "ownedNavigationPath")
                                    && Boolean.TRUE.equals(field(goal, "ownsNavigation"))
                                    && clue.equals(field(goal, "moveTarget")),
                            "the goal must immediately own an accepted path for the clue intent");
                    helper.runAfterDelay(19, () -> {
                        try {
                            goal.tick();
                            require(mob.getNavigation().getPath() == initialPath
                                            && Boolean.TRUE.equals(field(goal, "ownsNavigation"))
                                            && clue.equals(field(goal, "moveTarget")),
                                    "the alternate timer must not fire before 20 ticks from the accepted path");
                            helper.runAfterDelay(1, () -> {
                                try {
                                    require(mob.getNavigation().moveTo(initialPath, 1.0D)
                                                    && mob.getNavigation().getPath() == initialPath,
                                            "the fixture must keep the original owned path active while simulating a stalled route");
                                    setField(goal, "lastPosition", mob.position());
                                    setField(goal, "lastProgressTick", level.getGameTime() - 20L);
                                    goal.tick();
                                    Path alternatePath = mob.getNavigation().getPath();
                                    require(alternatePath != null && alternatePath != initialPath
                                                    && Boolean.TRUE.equals(field(goal, "ownsNavigation"))
                                                    && alternate.equals(field(goal, "moveTarget")),
                                            "a stalled owned clue path must issue exactly the deterministic alternate waypoint"
                                                    + " path=" + alternatePath + " initial=" + initialPath
                                                    + " target=" + (alternatePath == null ? null : alternatePath.getTarget())
                                                    + " owned=" + field(goal, "ownedNavigationPath")
                                                    + " ownsNavigation=" + field(goal, "ownsNavigation")
                                                    + " moveTarget=" + field(goal, "moveTarget")
                                                    + " navigationDone=" + mob.getNavigation().isDone()
                                                    + " navOwnsPath=" + (mob.getNavigation().getPath() == field(goal, "ownedNavigationPath"))
                                                    + " stuckTicks=" + field(goal, "stuckTicks")
                                                    + " alternateAttempted=" + field(goal, "alternateAttempted")
                                                    + " lastProgressTick=" + field(goal, "lastProgressTick")
                                                    + " lastPosition=" + field(goal, "lastPosition")
                                                    + " position=" + mob.position()
                                                    + " clueDistanceSqr=" + mob.blockPosition().distSqr(clue));
                                    require(ClueDirector.getActiveHint(mob) == firstHint,
                                            "the original clue lease must remain active during its single alternate attempt");
                                    advanceGoal(helper, goal, 20, () -> {
                                        try {
                                            require(ClueDirector.getActiveHint(mob) == null,
                                                    "a second stalled route must release the clue and enter backoff");
                                            goal.stop();
                                            Path pathAfterFailureStop = mob.getNavigation().getPath();
                                            boolean navigationDoneAfterFailureStop = mob.getNavigation().isDone();
                                            mob.setPos(helper.absolutePos(new BlockPos(24, 81, 10)).getCenter());
                                            require(ClueDirector.onSoundAccepted(level.dimension().location().toString(),
                                                            secondClue, 110, 32.0, 1.0, UUID.randomUUID()) != 0L,
                                                    "a fresh clue in another cell must be accepted after failure");
                                            require(ClueDirector.registerInterest(mob),
                                                    "the mob must register again for a distinct fresh clue");
                                            helper.runAfterDelay(12, () -> {
                                                try {
                                                    ClueDirector.Hint secondHint = ClueDirector.getActiveHint(mob);
                                                    require(secondHint != null && secondHint.position().equals(secondClue),
                                                            "a fresh clue must remain eligible after the first clue backs off");
                                                    mob.getNavigation().stop();
                                                    com.example.soundattract.pathfinding.NavLimiter.clear();
                                                    require(ClueDirector.getActiveHint(mob) == secondHint,
                                                            "the unclaimed fresh clue lease must remain active before route submission");
                                                    boolean secondGoalEligible = goal.canUse();
                                                    require(secondGoalEligible, "the production goal must accept the fresh clue"
                                                            + " target=" + mob.getTarget()
                                                            + " navigationDone=" + mob.getNavigation().isDone()
                                                            + " navigationDoneAfterFailureStop=" + navigationDoneAfterFailureStop
                                                            + " samePathAfterStop=" + (mob.getNavigation().getPath() == pathAfterFailureStop)
                                                            + " remainingPath=" + mob.getNavigation().getPath()
                                                            + " remainingPathTarget=" + (mob.getNavigation().getPath() == null ? null : mob.getNavigation().getPath().getTarget())
                                                            + " controlled=" + LivingWallCoordinator.isControlled(mob)
                                                            + " brainManaged=" + com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat.managesSecondBrainMovement(mob)
                                                            + " hintActive=" + (ClueDirector.getActiveHint(mob) == secondHint));
                                                    goal.start();
                                                    Path ownedPath = mob.getNavigation().getPath();
                                                    require(ownedPath != null && !mob.getNavigation().isDone()
                                                                    && ownedPath == field(goal, "ownedNavigationPath")
                                                                    && Boolean.TRUE.equals(field(goal, "ownsNavigation")),
                                                            "the second investigation must own an active path before preemption");
                                                    BlockPos foreignTarget = helper.absolutePos(new BlockPos(20, 81, 12));
                                                    Path foreignPath = mob.getNavigation().createPath(foreignTarget, 0);
                                                    require(foreignPath != null && foreignPath != ownedPath,
                                                            "the fixture must construct a distinct external path");
                                                    require(mob.getNavigation().moveTo(foreignPath, 1.0),
                                                            "the foreign movement writer must install its own path");
                                                    require(mob.getNavigation().getPath() == foreignPath,
                                                            "the foreign path must replace the goal-owned path");
                                                    goal.tick();
                                                    goal.stop();
                                                    require(mob.getNavigation().getPath() == foreignPath
                                                                    && !mob.getNavigation().isDone(),
                                                            "preemption and goal cleanup must preserve the exact foreign path object");
                                                    require(ClueDirector.getActiveHint(mob) == null,
                                                            "preemption must release the second clue lease");
                                                    helper.succeed();
                                                } catch (Throwable failure) {
                                                    cleanup.run();
                                                    helper.fail("clue path-ownership fixture failed: " + failure.getMessage());
                                                } finally {
                                                    cleanup.run();
                                                }
                                            });
                                        } catch (Throwable failure) {
                                            cleanup.run();
                                            helper.fail("clue alternate/backoff fixture failed: " + failure.getMessage());
                                        }
                                    });
                                } catch (Throwable failure) {
                                    cleanup.run();
                                    helper.fail("clue alternate waypoint fixture failed: " + failure.getMessage());
                                }
                            });
                        } catch (Throwable failure) {
                            cleanup.run();
                            helper.fail("clue accepted-path timing fixture failed: " + failure.getMessage());
                        }
                    });
                } catch (Throwable failure) {
                    cleanup.run();
                    helper.fail("clue first-dispatch fixture failed: " + failure.getMessage());
                }
            });
        } catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static void advanceGoal(GameTestHelper helper, ClueInvestigationGoal goal, int ticks, Runnable complete) {
        if (ticks == 0) {
            complete.run();
            return;
        }
        helper.runAfterDelay(1, () -> {
            goal.tick();
            advanceGoal(helper, goal, ticks - 1, complete);
        });
    }

    private static void awaitFirstHint(GameTestHelper helper, Mob mob, BlockPos expected,
                                       int remainingTicks, Runnable cleanup, Runnable complete) {
        helper.runAfterDelay(1, () -> {
            ClueDirector.Hint hint = ClueDirector.getActiveHint(mob);
            if (hint != null) {
                complete.run();
                return;
            }
            if (remainingTicks <= 1) {
                cleanup.run();
                helper.fail("the director did not dispatch a clue within the bounded wait: stats="
                        + ClueDirector.stats(mob.level().getServer()) + " expected=" + expected);
                return;
            }
            ClueDirector.registerInterest(mob);
            awaitFirstHint(helper, mob, expected, remainingTicks - 1, cleanup, complete);
        });
    }

    private static BlockPos alternateFor(BlockPos clue, UUID id) {
        int direction = Math.floorMod(id.hashCode(), 4);
        int dx = direction == 0 ? 3 : direction == 1 ? -3 : 0;
        int dz = direction == 2 ? 3 : direction == 3 ? -3 : 0;
        return clue.offset(dx, 0, dz);
    }

    private static void removeGoals(net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        List<Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) goals.add(wrapped.getGoal());
        for (Goal goal : goals) selector.removeGoal(goal);
    }

    private static Object field(ClueInvestigationGoal goal, String name) throws Exception {
        var field = ClueInvestigationGoal.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(goal);
    }

    private static void setField(ClueInvestigationGoal goal, String name, Object value) throws Exception {
        var field = ClueInvestigationGoal.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(goal, value);
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
