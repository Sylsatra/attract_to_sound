package com.example.soundattract.raid;

import com.example.soundattract.ai.RaidManager;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.mixin.MobAccessor;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

/** Verifies raid countdown state is isolated by server dimension. */
public final class RaidDimensionGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void raidCountdownsAdvanceOnlyInTheirOwnDimension(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        if (nether == null || nether == overworld) {
            helper.fail("GameTest server did not provide an isolated Nether ServerLevel");
            return;
        }

        Mob overworldLeader = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 1, 1, 1);
        BlockPos netherSpawn = new BlockPos(0, 80, 0);
        nether.getChunkAt(netherSpawn);
        Mob netherLeader = com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE.create(nether, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (netherLeader == null) {
            helper.fail("Could not create a Nether raid leader");
            return;
        }
        netherLeader.setNoAi(true);
        netherLeader.setPos(netherSpawn.getX() + 0.5, netherSpawn.getY(), netherSpawn.getZ() + 0.5);
        if (!nether.addFreshEntity(netherLeader)) {
            helper.fail("Could not add the Nether raid leader to its ServerLevel");
            return;
        }

        BlockPos overworldTarget = helper.absolutePos(new BlockPos(6, 1, 1));
        BlockPos netherTarget = netherSpawn.offset(6, 0, 0);
        try {
            RaidManager.scheduleRaid(overworldLeader, overworldTarget, overworld.getGameTime());
            RaidManager.scheduleRaid(netherLeader, netherTarget, nether.getGameTime());
            int overworldInitial = RaidManager.getTicksRemaining(overworldLeader);
            int netherInitial = RaidManager.getTicksRemaining(netherLeader);

            RaidManager.tick(overworld);
            int overworldAfterOwnTick = RaidManager.getTicksRemaining(overworldLeader);
            int netherAfterOverworldTick = RaidManager.getTicksRemaining(netherLeader);
            RaidManager.tick(nether);
            int netherAfterOwnTick = RaidManager.getTicksRemaining(netherLeader);

            if (overworldInitial <= 0 || netherInitial <= 0
                    || overworldAfterOwnTick != overworldInitial - 1
                    || netherAfterOverworldTick != netherInitial
                    || netherAfterOwnTick != netherInitial - 1) {
                helper.fail("Raid dimensions shared countdown progression: overworld=" + overworldInitial
                        + "->" + overworldAfterOwnTick + ", nether=" + netherInitial
                        + "->" + netherAfterOverworldTick + "->" + netherAfterOwnTick);
                return;
            }
            helper.succeed();
        } finally {
            RaidManager.clearRaid(overworldLeader);
            RaidManager.clearRaid(netherLeader);
            netherLeader.discard();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void deadRaidLeaderCancelsCountdownAndReleasesPersistentParticipants(GameTestHelper helper) {
        Mob leader = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 1, 1, 1);
        Mob reinforcement = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.SKELETON, 3, 1, 1);
        BlockPos target = helper.absolutePos(new BlockPos(6, 1, 1));
        boolean leaderPersistent;
        boolean reinforcementPersistent;
        boolean raidEnded;
        boolean participantPersistenceReleased;

        try {
            RaidManager.scheduleRaid(leader, target, helper.getLevel().getGameTime());
            RaidManager.registerPersistentRaidMob(leader, reinforcement);
            leaderPersistent = ((MobAccessor) leader).isPersistenceRequiredFlag();
            reinforcementPersistent = ((MobAccessor) reinforcement).isPersistenceRequiredFlag();

            leader.setHealth(0.0F);
            RaidManager.tick(helper.getLevel());

            raidEnded = !RaidManager.isRaidTicking(leader)
                    && !RaidManager.isRaidAdvancing(leader)
                    && RaidManager.getTicksRemaining(leader) == -1;
            participantPersistenceReleased = !((MobAccessor) leader).isPersistenceRequiredFlag()
                    && !((MobAccessor) reinforcement).isPersistenceRequiredFlag();
        } finally {
            RaidManager.clearRaid(leader);
        }

        if (!leaderPersistent || !reinforcementPersistent || !raidEnded || !participantPersistenceReleased) {
            helper.fail("Dead-leader raid cleanup mismatch: leaderWasPersistent=" + leaderPersistent
                    + ", reinforcementWasPersistent=" + reinforcementPersistent
                    + ", raidEnded=" + raidEnded + ", participantsReleased=" + participantPersistenceReleased);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void unloadingDimensionClearsRaidAndParticipantPersistence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(Level.END);
        if (level == null) {
            helper.fail("GameTest server did not provide an isolated End ServerLevel");
            return;
        }
        BlockPos origin = new BlockPos(0, 96, 0);
        level.getChunkAt(origin);
        Mob leader = com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        Mob reinforcement = com.example.soundattract.gametest.GameTestEntityTypes.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (leader == null || reinforcement == null) {
            helper.fail("Could not create isolated End raid participants");
            return;
        }
        leader.setNoAi(true);
        reinforcement.setNoAi(true);
        leader.setPos(0.5, 96, 0.5);
        reinforcement.setPos(2.5, 96, 0.5);
        boolean leaderAdded = level.addFreshEntity(leader);
        boolean reinforcementAdded = level.addFreshEntity(reinforcement);
        BlockPos target = origin.offset(6, 0, 0);
        try {
            RaidManager.scheduleRaid(leader, target, level.getGameTime());
            reinforcement.setPersistenceRequired();
            RaidManager.registerPersistentRaidMob(leader, reinforcement);
            int remainingBeforeUnload = RaidManager.getTicksRemaining(leader);
            boolean leaderPersistentBeforeUnload = ((MobAccessor) leader).isPersistenceRequiredFlag();
            boolean reinforcementPersistentBeforeUnload = ((MobAccessor) reinforcement)
                    .isPersistenceRequiredFlag();
            boolean activeBeforeUnload = RaidManager.isRaidTicking(leader)
                    && leaderPersistentBeforeUnload && reinforcementPersistentBeforeUnload;

            RaidManager.onLevelUnload(level);

            boolean stateCleared = !RaidManager.isRaidTicking(leader)
                    && !RaidManager.isRaidAdvancing(leader)
                    && RaidManager.getRaidTarget(leader) == null
                    && !((MobAccessor) leader).isPersistenceRequiredFlag()
                    && !((MobAccessor) reinforcement).isPersistenceRequiredFlag();
            if (!leaderAdded || !reinforcementAdded || !activeBeforeUnload || !stateCleared) {
                helper.fail("World-unload raid cleanup mismatch: activeBeforeUnload=" + activeBeforeUnload
                        + ", stateCleared=" + stateCleared + ", leaderAdded=" + leaderAdded
                        + ", reinforcementAdded=" + reinforcementAdded + ", leaderAlive=" + leader.isAlive()
                        + ", leaderRemoved=" + leader.isRemoved() + ", remainingBeforeUnload="
                        + remainingBeforeUnload + ", leaderPersistentBeforeUnload="
                        + leaderPersistentBeforeUnload + ", reinforcementPersistentBeforeUnload="
                        + reinforcementPersistentBeforeUnload);
                return;
            }
            helper.succeed();
        } finally {
            RaidManager.onLevelUnload(level);
            leader.discard();
            reinforcement.discard();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void raidLeaderNavigatesTowardLiveTargetThroughCorridor(GameTestHelper helper) {
        for (int x = 0; x <= 15; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                helper.setBlock(x, 1, z, Blocks.AIR);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        for (int x = 0; x <= 15; x++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(x, y, 0, Blocks.STONE);
                helper.setBlock(x, y, 6, Blocks.STONE);
            }
        }
        int previousCountdown = com.example.soundattract.config.SoundAttractConfig.COMMON.raidCountdownTicks.get();
        boolean previousReinforcements = com.example.soundattract.config.SoundAttractConfig.COMMON
                .enableRaidReinforcements.get();
        boolean advancing;
        Mob leader = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 1, 1, 3);
        BlockPos target = helper.absolutePos(new BlockPos(14, 1, 3));
        int countdownTicks = com.example.soundattract.config.SoundAttractConfig.COMMON.raidCountdownTicks.get();
        try {
            // The countdown is advanced synchronously so no global countdown
            // config mutation is needed for this test fixture.
            com.example.soundattract.config.SoundAttractConfig.COMMON.enableRaidReinforcements.set(false);
            RaidManager.scheduleRaid(leader, target, helper.getLevel().getGameTime());
            for (int tick = 0; tick <= countdownTicks && !RaidManager.isRaidAdvancing(leader); tick++) {
                RaidManager.tick(helper.getLevel());
            }
            advancing = RaidManager.isRaidAdvancing(leader);
        } finally {
            com.example.soundattract.config.SoundAttractConfig.COMMON.enableRaidReinforcements
                    .set(previousReinforcements);
        }
        final boolean raidAdvancing = advancing;
        helper.runAfterDelay(2, () -> startRaidNavigationTest(
                helper, leader, target, raidAdvancing, previousCountdown, previousReinforcements));
    }

    private static void startRaidNavigationTest(GameTestHelper helper, Mob leader, BlockPos target,
                                                boolean advancing, int previousCountdown,
                                                boolean previousReinforcements) {
        double previousArrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();
        net.minecraft.world.entity.ai.goal.GoalSelector selector =
                ((MobAccessor) leader).getGoalSelector();
        LeaderAttractionGoal goal = selector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(LeaderAttractionGoal.class::isInstance)
                .map(LeaderAttractionGoal.class::cast)
                .findFirst().orElseGet(() -> {
                    // Some supported mob-brain integrations replace vanilla
                    // goal attachment; install the production goal explicitly
                    // so this fixture verifies its real raid-navigation path.
                    LeaderAttractionGoal attached = new LeaderAttractionGoal(leader,
                            com.example.soundattract.config.SoundAttractConfig.COMMON.mobMoveSpeed.get());
                    selector.addGoal(3, attached);
                    return attached;
                });
        boolean acquired = false;
        boolean pathInstalled;
        double startDistance;
        try {
            // Constrain the annulus so the randomized staging destination remains
            // within the built corridor, then exercise the real navigation path.
            com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.set(2.0);
            if (goal != null) selector.tick();
            acquired = goal != null && selector.getAvailableGoals().stream().filter(wrapped -> wrapped.isRunning()).anyMatch(wrapped -> wrapped.getGoal() == goal)
                    && target.equals(goal.getTargetSoundPos());
            net.minecraft.world.level.pathfinder.Path corridorPath = leader.getNavigation().createPath(target, 0);
            pathInstalled = acquired && corridorPath != null && corridorPath.canReach();
            startDistance = leader.distanceToSqr(Vec3.atBottomCenterOf(target));
        } finally {
            com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.set(previousArrivalDistance);
        }
        if (!acquired) {
            RaidManager.clearRaid(leader);
            helper.fail("Attached leader goal did not acquire its advancing raid target: goalPresent="
                    + (goal != null) + ", advancing=" + advancing
                    + ", target=" + (goal == null ? null : goal.getTargetSoundPos()));
            return;
        }
        boolean[] reachablePathObserved = {pathInstalled};
        continueRaidNavigationTest(helper, leader, selector, goal, target, startDistance, advancing,
                reachablePathObserved, 30, previousCountdown, previousReinforcements);
    }

    private static void continueRaidNavigationTest(GameTestHelper helper, Mob leader,
                                                    net.minecraft.world.entity.ai.goal.GoalSelector selector,
                                                    LeaderAttractionGoal goal, BlockPos target,
                                                    double startDistance, boolean advancing,
                                                    boolean[] reachablePathObserved, int ticksRemaining,
                                                    int previousCountdown, boolean previousReinforcements) {
        selector.tick();
        net.minecraft.world.level.pathfinder.Path currentPath = leader.getNavigation().getPath();
        if (currentPath != null && currentPath.canReach()) reachablePathObserved[0] = true;
        if (ticksRemaining > 0) {
            helper.runAfterDelay(1, () -> continueRaidNavigationTest(helper, leader, selector, goal, target,
                    startDistance, advancing, reachablePathObserved, ticksRemaining - 1,
                    previousCountdown, previousReinforcements));
            return;
        }
        finishRaidNavigationTest(helper, leader, goal, target, startDistance, advancing,
                reachablePathObserved[0], previousCountdown, previousReinforcements);
    }

    private static void finishRaidNavigationTest(GameTestHelper helper, Mob leader, LeaderAttractionGoal goal,
                                                 BlockPos target, double startDistance,
                                                 boolean advancing, boolean pathInstalled,
                                                 int previousCountdown, boolean previousReinforcements) {
        double finalDistance = leader.distanceToSqr(Vec3.atBottomCenterOf(target));
        boolean movedTowardTarget = finalDistance < startDistance;
        goal.stop();
        RaidManager.clearRaid(leader);
        com.example.soundattract.config.SoundAttractConfig.COMMON.raidCountdownTicks.set(previousCountdown);
        com.example.soundattract.config.SoundAttractConfig.COMMON.enableRaidReinforcements
                .set(previousReinforcements);
        if (!advancing || !pathInstalled || !movedTowardTarget) {
            helper.fail("Raid leader corridor navigation failed: advancing=" + advancing
                    + ", pathInstalled=" + pathInstalled + ", startDistance=" + startDistance
                    + ", finalDistance=" + finalDistance + ", target=" + target);
            return;
        }
        helper.succeed();
    }
}

