package com.example.soundattract.ai;

import com.example.soundattract.worker.WorkerScheduler.GroupComputeResult;
import com.example.soundattract.ai.RaidManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.MobAccessor;
import com.example.soundattract.worker.WorkerComputations;
import com.example.soundattract.worker.WorkerScheduler;
import com.example.soundattract.gametest.GameTestEntityTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/** Exercises group application and relay consumption with real level entities. */
public final class MobGroupGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void groupAssignmentRelaysSoundOnce(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                helper.setBlock(x, 1, z, Blocks.AIR);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        Zombie leader = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 1, 1, 1);
        Zombie follower = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 3, 1, 1);
        Zombie edgeFollower = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 4, 1, 1);
        Zombie deserter = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 5, 1, 1);
        GroupComputeResult group = new GroupComputeResult(
                helper.getLevel().dimension().identifier(),
                Map.of(leader.getUUID(), leader.getUUID(), follower.getUUID(), leader.getUUID(),
                        edgeFollower.getUUID(), leader.getUUID(), deserter.getUUID(), deserter.getUUID()),
                Map.of(leader.getUUID(), Set.of(edgeFollower.getUUID())), Set.of(deserter.getUUID()),
                MobGroupManager.currentServerGeneration());

        MobGroupManager.applyGroupResult(helper.getLevel(), group, List.of(leader, follower, edgeFollower, deserter));
        if (MobGroupManager.getLeader(follower) != leader || MobGroupManager.getFollowerCount(leader) != 2
                || !MobGroupManager.getFollowers(leader).contains(follower)
                || !MobGroupManager.isEdgeMob(edgeFollower) || !MobGroupManager.isDeserter(deserter)) {
            MobGroupManager.onLevelUnload(helper.getLevel());
            helper.fail("Group application lost leader/follower, edge-mob, or deserter state");
            return;
        }

        long now = helper.getLevel().getGameTime();
        MobGroupManager.relaySoundToLeader(follower, "soundattract:gametest", 2.0, 1.0, 1.0, 16.0, 1.0, now);
        List<MobGroupManager.SoundRelay> delivered = MobGroupManager.consumeRelayedSounds(leader);
        List<MobGroupManager.SoundRelay> redelivered = MobGroupManager.consumeRelayedSounds(leader);
        MobGroupManager.relaySoundToLeader(follower, "soundattract:rate_limited", 3.0, 1.0, 1.0, 16.0, 1.0, now + 5);
        List<MobGroupManager.SoundRelay> rateLimited = MobGroupManager.consumeRelayedSounds(leader);
        MobGroupManager.relaySoundToLeader(leader, "soundattract:leader-self", 2.0, 1.0, 1.0, 16.0, 1.0, now);
        List<MobGroupManager.SoundRelay> selfRelay = MobGroupManager.consumeRelayedSounds(leader);
        MobGroupManager.relaySoundToLeader(edgeFollower, "soundattract:stale-relay",
                4.0, 1.0, 1.0, 16.0, 1.0, now - 41L);
        List<MobGroupManager.SoundRelay> staleRelay = MobGroupManager.consumeRelayedSounds(leader);
        MobGroupManager.onLevelUnload(helper.getLevel());
        boolean levelStateCleared = MobGroupManager.getLeader(follower) == follower
                && MobGroupManager.getFollowerCount(leader) == 0
                && MobGroupManager.consumeRelayedSounds(leader).isEmpty();

        List<Zombie> capFollowers = List.of(follower, edgeFollower, deserter);
        Map<java.util.UUID, java.util.UUID> capAssignment = new java.util.HashMap<>();
        capAssignment.put(leader.getUUID(), leader.getUUID());
        capFollowers.forEach(capFollower -> capAssignment.put(capFollower.getUUID(), leader.getUUID()));
        GroupComputeResult capGroup = new GroupComputeResult(
                helper.getLevel().dimension().identifier(), capAssignment, Map.of(), Set.of(),
                MobGroupManager.currentServerGeneration());
        List<MobGroupManager.SoundRelay> cappedRelays = List.of();
        boolean capAssignmentApplied;
        try {
            List<net.minecraft.world.entity.Mob> capCandidates = new ArrayList<>(capFollowers);
            capCandidates.add(leader);
            MobGroupManager.applyGroupResult(helper.getLevel(), capGroup, capCandidates);
            capAssignmentApplied = MobGroupManager.getLeader(capFollowers.get(0)) == leader
                    && MobGroupManager.getLeader(capFollowers.get(capFollowers.size() - 1)) == leader;
            for (int index = 0; index < capFollowers.size(); index++) {
                MobGroupManager.relaySoundToLeader(capFollowers.get(index), "soundattract:cap_" + index,
                        index, 1.0, 1.0, 16.0, 1.0, now, 2);
            }
            cappedRelays = MobGroupManager.consumeRelayedSounds(leader);
        } finally {
            MobGroupManager.onLevelUnload(helper.getLevel());
        }
        boolean capKeptNewestRelays = cappedRelays.size() == 2
                && "soundattract:cap_1".equals(cappedRelays.get(0).soundId)
                && "soundattract:cap_2".equals(cappedRelays.get(1).soundId);

        if (delivered.size() != 1 || !"soundattract:gametest".equals(delivered.get(0).soundId)) {
            helper.fail("Follower sound did not reach the assigned leader exactly once");
            return;
        }
        if (!redelivered.isEmpty() || !rateLimited.isEmpty() || !selfRelay.isEmpty()
                || !staleRelay.isEmpty() || !levelStateCleared) {
            helper.fail("Consumed relay repeated, rate limit/TTL failed, leader self-relay occurred, or level state survived unload: stale="
                    + staleRelay + ", cleared=" + levelStateCleared);
            return;
        }
        if (!capAssignmentApplied || !capKeptNewestRelays) {
            helper.fail("Per-leader relay buffer cap failed: assigned=" + capAssignmentApplied
                    + ", retained=" + cappedRelays.stream().map(relay -> relay.soundId).toList()
                    + ", expected=[soundattract:cap_1, soundattract:cap_2] (oldest relay evicted)");
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void groupComputeHonorsCapsAndReassignsAfterLeaderRemoval(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 15; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                helper.setBlock(x, 1, z, Blocks.AIR);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }

        List<WorkerScheduler.MobSnapshot> snapshots = new ArrayList<>();
        for (int z : new int[] {1, 13}) {
            for (int x = 1; x <= 4; x++) {
                Zombie mob = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, x, 1, z);
                snapshots.add(new WorkerScheduler.MobSnapshot(
                        mob.getUUID(), mob.getX(), mob.getY(), mob.getZ(), mob.getHealth(), mob.isAlive()));
            }
        }

        var dimension = helper.getLevel().dimension().identifier();
        WorkerScheduler.ConfigSnapshot config = new WorkerScheduler.ConfigSnapshot(
                5.0, 2, 3, 1.0, 4, 1, 17L);
        WorkerScheduler.GroupComputeResult initial = WorkerComputations.computeGroups(
                snapshots, config, System.currentTimeMillis() + 5_000L, dimension);
        if (initial == null) {
            helper.fail("Group worker returned no result for valid mob snapshots");
            return;
        }

        Map<java.util.UUID, Long> groupSizes = initial.mobUuidToLeaderUuid().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(id -> id, java.util.stream.Collectors.counting()));
        long leaderCount = initial.mobUuidToLeaderUuid().entrySet().stream()
                .filter(entry -> entry.getKey().equals(entry.getValue())).count();
        if (leaderCount > 2 || groupSizes.values().stream().anyMatch(size -> size > 3)
                || initial.deserterUuids().size() != 2) {
            helper.fail("Group compute exceeded configured caps or failed to mark overflow mobs as deserters: leaders="
                    + leaderCount + ", sizes=" + groupSizes + ", deserters=" + initial.deserterUuids().size());
            return;
        }

        java.util.UUID removedLeaderId = initial.mobUuidToLeaderUuid().entrySet().stream()
                .filter(entry -> entry.getKey().equals(entry.getValue()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        List<WorkerScheduler.MobSnapshot> survivingSnapshots = snapshots.stream()
                .filter(snapshot -> !snapshot.uuid().equals(removedLeaderId))
                .map(snapshot -> new WorkerScheduler.MobSnapshot(
                        snapshot.uuid(), snapshot.x(), snapshot.y(), snapshot.z(), snapshot.health(), snapshot.alive()))
                .toList();
        WorkerScheduler.GroupComputeResult afterRemoval = WorkerComputations.computeGroups(
                survivingSnapshots, config, System.currentTimeMillis() + 5_000L, dimension);
        if (afterRemoval == null || afterRemoval.mobUuidToLeaderUuid().containsKey(removedLeaderId)
                || afterRemoval.mobUuidToLeaderUuid().values().contains(removedLeaderId)) {
            helper.fail("Recomputed groups retained a removed leader UUID");
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void serverStopClearsMobGroupStateAndAdvancesGeneration(GameTestHelper helper) {
        Zombie leader = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 1, 1, 1);
        Zombie follower = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 3, 1, 1);
        long generationBefore = MobGroupManager.currentServerGeneration();
        GroupComputeResult result = new GroupComputeResult(
                helper.getLevel().dimension().identifier(),
                Map.of(leader.getUUID(), leader.getUUID(), follower.getUUID(), leader.getUUID()),
                Map.of(), Set.of(), generationBefore);
        MobGroupManager.applyGroupResult(helper.getLevel(), result, List.of(leader, follower));
        boolean groupWasActive = MobGroupManager.getLeader(follower) == leader
                && MobGroupManager.getFollowers(leader).contains(follower);

        MobGroupManager.relaySoundToLeader(follower, "soundattract:server-stop", 2.0, 1.0, 1.0,
                16.0, 1.0, helper.getLevel().getGameTime());
        MobGroupManager.onServerStopped();

        boolean stateCleared = MobGroupManager.getLeader(follower) == follower
                && MobGroupManager.getFollowers(leader).isEmpty()
                && MobGroupManager.consumeRelayedSounds(leader).isEmpty();
        boolean generationAdvanced = MobGroupManager.currentServerGeneration() > generationBefore;
        if (!groupWasActive || !stateCleared || !generationAdvanced) {
            helper.fail("Server-stop mob-group cleanup failed: activeBefore=" + groupWasActive
                    + ", stateCleared=" + stateCleared + ", generation=" + generationBefore
                    + "->" + MobGroupManager.currentServerGeneration());
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void raidReinforcementBudgetAllowsOnlyOneAttemptPerDimensionTick(GameTestHelper helper) {
        var level = helper.getLevel();
        // The GameTest server reuses one dimension for every test, so another test may
        // already have claimed the current level tick. Exercise an unused tick value.
        long tick = level.getGameTime() + 100L;
        boolean firstAttemptAccepted = RaidManager.claimReinforcementTick(level, tick);
        boolean secondSameTickRejected = !RaidManager.claimReinforcementTick(level, tick);
        boolean nextTickAccepted = RaidManager.claimReinforcementTick(level, tick + 1L);
        RaidManager.onLevelUnload(level);
        if (!firstAttemptAccepted || !secondSameTickRejected || !nextTickAccepted) {
            helper.fail("Raid reinforcement tick budget mismatch: first=" + firstAttemptAccepted
                    + ", duplicateRejected=" + secondSameTickRejected + ", nextTick=" + nextTickAccepted);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void raidCountdownTracksTargetAndRestoresPersistenceOnClear(GameTestHelper helper) {
        Zombie leader = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 1, 1, 1);
        int previousCountdown = SoundAttractConfig.COMMON.raidCountdownTicks.get();
        boolean previousReinforcements = SoundAttractConfig.COMMON.enableRaidReinforcements.get();
        BlockPos initialTarget = helper.absolutePos(new BlockPos(6, 1, 1));
        BlockPos updatedTarget = helper.absolutePos(new BlockPos(7, 1, 1));
        Zombie disappearingLeader = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 2, 1, 2);
        boolean leaderRemovalCancelled = false;
        try {
            SoundAttractConfig.COMMON.raidCountdownTicks.set(20);
            SoundAttractConfig.COMMON.enableRaidReinforcements.set(false);
            RaidManager.scheduleRaid(leader, initialTarget, helper.getLevel().getGameTime());
            boolean scheduled = RaidManager.isRaidTicking(leader)
                    && initialTarget.equals(RaidManager.getRaidTarget(leader))
                    && ((MobAccessor) leader).isPersistenceRequiredFlag();
            for (int tick = 0; tick < 5; tick++) RaidManager.tick(helper.getLevel());
            int countdownBeforeDuplicate = RaidManager.getTicksRemaining(leader);
            RaidManager.scheduleRaid(leader, updatedTarget, helper.getLevel().getGameTime());
            boolean duplicateSuppressed = RaidManager.getTicksRemaining(leader) == countdownBeforeDuplicate
                    && initialTarget.equals(RaidManager.getRaidTarget(leader));
            for (int tick = 5; tick < 20; tick++) RaidManager.tick(helper.getLevel());
            boolean advanced = RaidManager.isRaidAdvancing(leader)
                    && RaidManager.getTicksRemaining(leader) == 0;
            RaidManager.updateRaidTarget(leader, updatedTarget);
            boolean targetUpdated = updatedTarget.equals(RaidManager.getRaidTarget(leader));
            RaidManager.clearRaid(leader);
            boolean cleared = !RaidManager.isRaidTicking(leader) && !RaidManager.isRaidAdvancing(leader)
                    && RaidManager.getRaidTarget(leader) == null
                    && !((MobAccessor) leader).isPersistenceRequiredFlag();
            RaidManager.scheduleRaid(disappearingLeader, initialTarget, helper.getLevel().getGameTime());
            disappearingLeader.discard();
            RaidManager.tick(helper.getLevel());
            leaderRemovalCancelled = !RaidManager.isRaidTicking(disappearingLeader)
                    && !RaidManager.isRaidAdvancing(disappearingLeader)
                    && RaidManager.getRaidTarget(disappearingLeader) == null;
            if (!scheduled || !duplicateSuppressed || !advanced || !targetUpdated || !cleared
                    || !leaderRemovalCancelled) {
                helper.fail("Raid state transition/cleanup failed: scheduled=" + scheduled
                        + ", duplicateSuppressed=" + duplicateSuppressed + ", advanced=" + advanced
                        + ", targetUpdated=" + targetUpdated + ", cleared=" + cleared
                        + ", leaderRemovalCancelled=" + leaderRemovalCancelled);
                return;
            }
            helper.succeed();
        } finally {
            RaidManager.clearRaid(leader);
            RaidManager.clearRaid(disappearingLeader);
            SoundAttractConfig.COMMON.raidCountdownTicks.set(previousCountdown);
            SoundAttractConfig.COMMON.enableRaidReinforcements.set(previousReinforcements);
        }
    }

}
 
