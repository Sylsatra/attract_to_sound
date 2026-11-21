package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("soundattract_secondbrain")
public final class SecondBrainGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public void queuedSoundClueMovesMobAndToggleRequiresFreshEvidence(GameTestHelper helper) {
        recordSelection("com.example.soundattract.ai.SecondBrainGameTests#queuedSoundClueMovesMobAndToggleRequiresFreshEvidence",
                "secondbraingametests.queuedsoundcluemovesmobandtogglerequiresfreshevidence");
        var level = helper.getLevel();
        boolean previousSecondBrain = SoundAttractConfig.COMMON.enableSecondBrain.get();
        boolean previousSmartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        SoundAttractConfig.COMMON.enableSecondBrain.set(true);
        SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(false);
        Mob mob = helper.spawn(EntityType.ZOMBIE, 8, 81, 6);
        removeTargetAcquisitionGoals(mob);
        removeMovementGoals(mob);
        mob.getNavigation().stop();
        mob.setTarget(null);
        var initialPosition = mob.position();
        BlockPos firstClue = helper.absolutePos(new BlockPos(22, 81, 6));
        BlockPos freshClue = helper.absolutePos(new BlockPos(19, 81, 6));
        long[] initialEvidenceTick = new long[1];
        long[] freshHintDeadline = new long[1];
        boolean[] awaitingFreshHint = new boolean[1];
        boolean[] finished = new boolean[1];
        ClueInvestigationGoal goal = new ClueInvestigationGoal(mob);

        helper.onEachTick(() -> {
            if (!awaitingFreshHint[0] || finished[0]) return;
            try {
                ClueDirector.registerInterest(mob);
                ClueDirector.Hint hint = ClueDirector.getActiveHint(mob);
                boolean resumed = hint != null && hint.position().equals(freshClue)
                        && mob.getNavigation().getPath() != null && !mob.getNavigation().isDone();
                if (!resumed && level.getServer().getTickCount() < freshHintDeadline[0]) return;
                require(resumed, "reenabled brain must issue a destination and resume movement only for fresh evidence: hint="
                        + hint + " stats=" + ClueDirector.stats(level.getServer()) + " perf="
                        + com.example.soundattract.event.StealthDetectionEvents.getPerfTier(level.getServer())
                        + " tick=" + level.getServer().getTickCount() + " target=" + mob.getTarget()
                        + " navigationInProgress=" + mob.getNavigation().isInProgress() + " path="
                        + mob.getNavigation().getPath());
                finished[0] = true;
                helper.succeed();
                cleanup(previousSecondBrain, previousSmartEdge, mob);
            } catch (Throwable failure) {
                finished[0] = true;
                helper.fail("second-brain reenable fixture failed: " + failure.getMessage());
                cleanup(previousSecondBrain, previousSmartEdge, mob);
            }
        });

        for (int x = 4; x <= 25; x++) {
            for (int z = 3; z <= 9; z++) {
                for (int y = 81; y <= 84; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                helper.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
            }
        }

        helper.runAfterDelay(5, () -> {
            try {
                initialEvidenceTick[0] = level.getGameTime();
                require(ClueDirector.onSoundAccepted(level.dimension().location().toString(), firstClue,
                            100, 24.0, 1.0, null) != 0L,
                    "accepted sound evidence must enter the server-wide clue director");
                boolean interestRegistered = ClueDirector.registerInterest(mob);
                require(interestRegistered, "eligible idle mob must queue clue interest; brain="
                    + SoundAttractConfig.COMMON.enableSecondBrain.get() + " stats=" + ClueDirector.stats(level.getServer())
                    + " target=" + mob.getTarget() + " noAi=" + mob.isNoAi() + " asleep=" + mob.isSleeping()
                    + " attracted=" + SoundAttractionEvents.getCachedAttractedEntityTypes().contains(mob.getType())
                    + " attackMemory=" + mob.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)
                    + " leader=" + MobGroupManager.getLeader(mob) + " controlled=" + LivingWallCoordinator.isControlled(mob)
                    + " raid=" + RaidManager.isRaidTicking(mob) + "/" + RaidManager.isRaidAdvancing(mob));

                helper.runAfterDelay(22, () -> {
                try {
                    require(ClueDirector.getActiveHint(mob) != null,
                            "queued interest must survive until a later dispatch grants a lease; stats="
                                    + ClueDirector.stats(level.getServer()));
                    mob.goalSelector.addGoal(6, goal);
                    require(goal.canUse(), "native goal must consume the director's dispatched hint");
                    goal.start();
                    require(mob.getNavigation().getPath() != null && !mob.getNavigation().isDone(),
                            "native second-brain goal must own an active path toward its clue");
                    helper.runAfterDelay(20, () -> {
                        try {
                            require(mob.position().distanceToSqr(initialPosition) > 0.25,
                                    "mob must advance toward the dispatched sound clue");
                            SoundAttractConfig.COMMON.enableSecondBrain.set(false);
                            ClueDirector.stats(level.getServer());
                            require(ClueInvestigationGoal.activeHint(mob) == null,
                                    "turning the brain off must release its active clue lease");
                            require(mob.getNavigation().isDone(),
                                    "turning the brain off must stop only its owned pursuit path");
                            require(ClueDirector.stats(level.getServer()).cells() == 0,
                                    "disabled brain must discard queued clue state");
                            SoundAttractConfig.COMMON.enableSecondBrain.set(true);
                            require(ClueDirector.getActiveHint(mob) == null
                                            && ClueDirector.stats(level.getServer()).cells() == 0,
                                    "reenabling the brain must not revive stale evidence");
                            helper.runAfterDelay(5, () -> {
                                try {
                                    require(ClueDirector.onScentAccepted(level, firstClue, initialEvidenceTick[0],
                                                    100, 24.0, 1.0, java.util.UUID.randomUUID()) == 0L,
                                            "evidence sampled before reenable must not enter the new brain session");
                                    require(ClueDirector.onSoundAccepted(level.dimension().location().toString(), freshClue,
                                                    100, 24.0, 1.0, null) != 0L,
                                            "fresh evidence must be accepted after reenabling");
                                    require(ClueDirector.registerInterest(mob),
                                            "idle mob must be able to register again after reenabling");
                                    freshHintDeadline[0] = level.getServer().getTickCount() + 35L;
                                    awaitingFreshHint[0] = true;
                                } catch (Throwable failure) {
                                    helper.fail("second-brain fresh-evidence fixture failed: " + failure.getMessage());
                                    cleanup(previousSecondBrain, previousSmartEdge, mob);
                                }
                            });
                        } catch (Throwable failure) {
                            helper.fail("second-brain toggle fixture failed: " + failure.getMessage());
                            cleanup(previousSecondBrain, previousSmartEdge, mob);
                        }
                    });
                } catch (Throwable failure) {
                    helper.fail("second-brain dispatch fixture failed: " + failure.getMessage());
                    cleanup(previousSecondBrain, previousSmartEdge, mob);
                }
                });
            } catch (Throwable failure) {
                helper.fail("second-brain setup fixture failed: " + failure.getMessage());
                cleanup(previousSecondBrain, previousSmartEdge, mob);
            }
        });
    }

    private static void removeTargetAcquisitionGoals(Mob mob) {
        List<net.minecraft.world.entity.ai.goal.Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof TargetGoal) goals.add(wrapped.getGoal());
        }
        for (var goal : goals) mob.targetSelector.removeGoal(goal);
    }

    private static void removeMovementGoals(Mob mob) {
        List<net.minecraft.world.entity.ai.goal.Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            goals.add(wrapped.getGoal());
        }
        for (var goal : goals) mob.goalSelector.removeGoal(goal);
    }

    private static void cleanup(boolean previousSecondBrain, boolean previousSmartEdge, Mob mob) {
        SoundAttractConfig.COMMON.enableSecondBrain.set(previousSecondBrain);
        SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(previousSmartEdge);
        mob.discard();
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
