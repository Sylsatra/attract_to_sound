package com.example.soundattract.integration.spoiled;

import com.example.soundattract.ModChecker;
import com.example.soundattract.event.AwarenessServerEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Exercises the Spoiled Fabric integration using the exact stack tags written by Spoiled. */
public final class SpoiledScentGameTests {
    private static final String SPOIL_TIMER_TAG = "spoiled:SpoilTimer";
    private static final String SPOIL_MAX_TIME_TAG = "spoiled:SpoilMaxTime";

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:spoiled_scent", timeoutTicks = 100)
    public void spoiledFoodProgressContributesToEffectivePlayerScent(GameTestHelper helper) {
        if (!ModChecker.isLoaded("spoiled")) {
            helper.fail("Spoiled integration profile is required for this test");
            return;
        }

        ItemStack fresh = new ItemStack(Items.ROTTEN_FLESH);
        ItemStack halfSpoiled = spoiledStack(Items.ROTTEN_FLESH, 4, 50, 100);
        ItemStack fullySpoiled = spoiledStack(Items.ROTTEN_FLESH, 1, 150, 100);
        ItemStack invalidMaximum = spoiledStack(Items.ROTTEN_FLESH, 1, 50, 0);
        ItemStack negativeProgress = spoiledStack(Items.ROTTEN_FLESH, 1, -1, 100);

        double halfProgress = SpoiledScentIntegration.spoilageProgress(halfSpoiled);
        double overrunProgress = SpoiledScentIntegration.spoilageProgress(fullySpoiled);
        boolean invalidValuesRejected = SpoiledScentIntegration.spoilageProgress(fresh) == 0.0
                && SpoiledScentIntegration.spoilageProgress(invalidMaximum) == 0.0
                && SpoiledScentIntegration.spoilageProgress(negativeProgress) == 0.0;

        ServerLevel level = helper.getLevel();
        ServerPlayer baselinePlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer spoiledPlayer = helper.makeMockServerPlayerInLevel();
        var baselinePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        var spoiledPos = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 1));
        double baseline;
        double withSpoiledFood;
        try {
            baselinePlayer.setPos(baselinePos.getX() + 0.5, baselinePos.getY(), baselinePos.getZ() + 0.5);
            spoiledPlayer.setPos(spoiledPos.getX() + 0.5, spoiledPos.getY(), spoiledPos.getZ() + 0.5);
            baselinePlayer.getInventory().setItem(0, new ItemStack(Items.ROTTEN_FLESH, 4));
            spoiledPlayer.getInventory().setItem(0, halfSpoiled);
            baseline = AwarenessServerEvents.getEffectivePlayerScent(level, baselinePlayer);
            withSpoiledFood = AwarenessServerEvents.getEffectivePlayerScent(level, spoiledPlayer);
        } finally {
            level.removePlayerImmediately(baselinePlayer, Entity.RemovalReason.DISCARDED);
            level.removePlayerImmediately(spoiledPlayer, Entity.RemovalReason.DISCARDED);
        }

        double configuredOdor = com.example.soundattract.config.SoundAttractConfig.COMMON.spoiledFoodOdorPerItem.get();
        double expectedContribution = halfProgress * configuredOdor * halfSpoiled.getCount();
        if (!invalidValuesRejected || Math.abs(halfProgress - 0.5) > 0.001 || overrunProgress != 1.0
                || !com.example.soundattract.integration.spoiled.SpoiledScentIntegration.isEnabled()
                || !Double.isFinite(baseline) || !Double.isFinite(withSpoiledFood)
                || Math.abs((withSpoiledFood - baseline) - expectedContribution) > 0.01) {
            helper.fail("Spoiled odor parity failed: enabled=" + SpoiledScentIntegration.isEnabled()
                    + ", invalidValuesRejected=" + invalidValuesRejected + ", halfProgress=" + halfProgress
                    + ", overrunProgress=" + overrunProgress + ", baseline=" + baseline
                    + ", withSpoiledFood=" + withSpoiledFood + ", expectedContribution=" + expectedContribution);
            return;
        }
        helper.succeed();
    }

    private static ItemStack spoiledStack(net.minecraft.world.item.Item item, int count, int timer, int maximum) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putInt(SPOIL_TIMER_TAG, timer);
        tag.putInt(SPOIL_MAX_TIME_TAG, maximum);
        stack.setTag(tag);
        return stack;
    }
}
