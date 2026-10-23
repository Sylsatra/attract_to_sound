package com.example.soundattract.raid;

import com.example.soundattract.ai.RaidManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.MobAccessor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/** Exercises per-type and per-raid total caps through the real spawning path. */
public final class RaidReinforcementGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void raidReinforcementHonorsEntityAndTotalCapsAndClearsOwnership(GameTestHelper helper) {
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 12; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                helper.setBlock(x, 1, z, Blocks.AIR);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        ServerLevel level = helper.getLevel();
        Zombie zombieLeader = helper.spawn(EntityType.ZOMBIE, 5, 1, 6);
        Skeleton skeletonLeader = helper.spawn(EntityType.SKELETON, 11, 1, 6);
        BlockPos zombieTarget = helper.absolutePos(new BlockPos(8, 1, 10));
        BlockPos skeletonTarget = helper.absolutePos(new BlockPos(14, 1, 10));

        int previousTotal = SoundAttractConfig.COMMON.raidMaxTotalMobs.get();
        int previousInterval = SoundAttractConfig.COMMON.raidReinforcementInterval.get();
        double previousDifficulty = SoundAttractConfig.COMMON.raidReinforcementDifficultyMultiplier.get();
        List<? extends String> previousCaps = SoundAttractConfig.COMMON.raidReinforcementCaps.get();
        String zombieId = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE).toString();
        String skeletonId = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.SKELETON).toString();
        Runnable cleanup = () -> {
            RaidManager.clearRaid(zombieLeader);
            RaidManager.clearRaid(skeletonLeader);
            SoundAttractConfig.COMMON.raidMaxTotalMobs.set(previousTotal);
            SoundAttractConfig.COMMON.raidReinforcementInterval.set(previousInterval);
            SoundAttractConfig.COMMON.raidReinforcementDifficultyMultiplier.set(previousDifficulty);
            SoundAttractConfig.COMMON.raidReinforcementCaps.set(previousCaps);
        };

        try {
            SoundAttractConfig.COMMON.raidReinforcementInterval.set(20);
            SoundAttractConfig.COMMON.raidReinforcementDifficultyMultiplier.set(1.0);
            SoundAttractConfig.COMMON.raidReinforcementCaps.set(List.of(zombieId + ",1", skeletonId + ",2"));
            SoundAttractConfig.COMMON.raidMaxTotalMobs.set(10);
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }

        helper.runAfterDelay(21, () -> {
            try {
                RaidReinforcementManager.trySpawnReinforcement(level, zombieLeader, zombieTarget, EntityType.ZOMBIE);
                List<Mob> zombies = RaidReinforcementManager.spawnedEntitiesForTesting(level, zombieLeader);
                if (zombies.size() != 1 || zombies.get(0).getType() != EntityType.ZOMBIE) {
                    cleanup.run();
                    helper.fail("Expected one zombie reinforcement under its per-type cap, got " + zombies.size());
                    return;
                }
                helper.runAfterDelay(21, () -> afterZombieCap(helper, level, zombieLeader, skeletonLeader,
                        zombieTarget, skeletonTarget, zombies, cleanup));
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
    }

    private static void afterZombieCap(GameTestHelper helper, ServerLevel level, Zombie zombieLeader,
                                       Skeleton skeletonLeader, BlockPos zombieTarget, BlockPos skeletonTarget,
                                       List<Mob> zombies, Runnable cleanup) {
        try {
            RaidReinforcementManager.trySpawnReinforcement(level, zombieLeader, zombieTarget, EntityType.ZOMBIE);
            int zombieCountAtTypeCap = RaidReinforcementManager.spawnedEntitiesForTesting(level, zombieLeader).size();

            SoundAttractConfig.COMMON.raidMaxTotalMobs.set(1);
            RaidReinforcementManager.trySpawnReinforcement(level, zombieLeader, zombieTarget, EntityType.SKELETON);
            int zombieLeaderTotalAtCap = RaidReinforcementManager.spawnedEntitiesForTesting(level, zombieLeader).size();

            SoundAttractConfig.COMMON.raidMaxTotalMobs.set(10);
            RaidReinforcementManager.trySpawnReinforcement(level, skeletonLeader, skeletonTarget, EntityType.SKELETON);
            List<Mob> firstSkeleton = RaidReinforcementManager.spawnedEntitiesForTesting(level, skeletonLeader);
            if (zombieCountAtTypeCap != 1 || zombieLeaderTotalAtCap != 1
                    || firstSkeleton.size() != 1 || firstSkeleton.get(0).getType() != EntityType.SKELETON) {
                cleanup.run();
                helper.fail("Per-type or total cap failed: zombies=" + zombieCountAtTypeCap
                        + ", zombieLeaderTotal=" + zombieLeaderTotalAtCap
                        + ", skeletons=" + firstSkeleton.size());
                return;
            }
            helper.runAfterDelay(21, () -> afterFirstSkeleton(helper, level, zombieLeader, skeletonLeader,
                    zombieTarget, skeletonTarget, zombies, cleanup));
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    private static void afterFirstSkeleton(GameTestHelper helper, ServerLevel level, Zombie zombieLeader,
                                          Skeleton skeletonLeader, BlockPos zombieTarget, BlockPos skeletonTarget,
                                          List<Mob> zombies, Runnable cleanup) {
        try {
            RaidReinforcementManager.trySpawnReinforcement(level, skeletonLeader, skeletonTarget, EntityType.SKELETON);
            List<Mob> skeletons = RaidReinforcementManager.spawnedEntitiesForTesting(level, skeletonLeader);
            if (skeletons.size() != 2 || skeletons.stream().anyMatch(mob -> mob.getType() != EntityType.SKELETON)) {
                cleanup.run();
                helper.fail("A second skeleton was not allowed under its per-type cap of two: " + skeletons.size());
                return;
            }
            helper.runAfterDelay(21, () -> finishCapsAndOwnership(helper, level, zombieLeader, skeletonLeader,
                    zombieTarget, skeletonTarget, zombies, cleanup));
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    private static void finishCapsAndOwnership(GameTestHelper helper, ServerLevel level, Zombie zombieLeader,
                                               Skeleton skeletonLeader, BlockPos zombieTarget, BlockPos skeletonTarget,
                                               List<Mob> zombies, Runnable cleanup) {
        try {
            RaidReinforcementManager.trySpawnReinforcement(level, skeletonLeader, skeletonTarget, EntityType.SKELETON);
            List<Mob> skeletons = RaidReinforcementManager.spawnedEntitiesForTesting(level, skeletonLeader);
            boolean totalCapApplied = skeletons.size() == 2;

            RaidManager.scheduleRaid(zombieLeader, zombieTarget, level.getGameTime());
            RaidManager.scheduleRaid(skeletonLeader, skeletonTarget, level.getGameTime());
            zombies.forEach(mob -> RaidManager.registerPersistentRaidMob(zombieLeader, mob));
            skeletons.forEach(mob -> RaidManager.registerPersistentRaidMob(skeletonLeader, mob));
            boolean allOwnedBeforeClear = ((MobAccessor) zombieLeader).isPersistenceRequiredFlag()
                    && ((MobAccessor) skeletonLeader).isPersistenceRequiredFlag()
                    && zombies.stream().allMatch(mob -> ((MobAccessor) mob).isPersistenceRequiredFlag())
                    && skeletons.stream().allMatch(mob -> ((MobAccessor) mob).isPersistenceRequiredFlag());

            cleanup.run();
            boolean allOwnershipCleared = !((MobAccessor) zombieLeader).isPersistenceRequiredFlag()
                    && !((MobAccessor) skeletonLeader).isPersistenceRequiredFlag()
                    && zombies.stream().noneMatch(mob -> ((MobAccessor) mob).isPersistenceRequiredFlag())
                    && skeletons.stream().noneMatch(mob -> ((MobAccessor) mob).isPersistenceRequiredFlag());
            boolean reinforcementStateCleared = RaidReinforcementManager
                    .spawnedEntitiesForTesting(level, zombieLeader).isEmpty()
                    && RaidReinforcementManager.spawnedEntitiesForTesting(level, skeletonLeader).isEmpty();
            if (!totalCapApplied || !allOwnedBeforeClear || !allOwnershipCleared || !reinforcementStateCleared) {
                helper.fail("Raid cap/ownership mismatch: skeletonCount=" + skeletons.size()
                        + ", totalCapApplied=" + totalCapApplied + ", ownedBeforeClear=" + allOwnedBeforeClear
                        + ", cleared=" + allOwnershipCleared
                        + ", reinforcementStateCleared=" + reinforcementStateCleared);
                return;
            }
            helper.succeed();
        } finally {
            cleanup.run();
        }
    }
}
