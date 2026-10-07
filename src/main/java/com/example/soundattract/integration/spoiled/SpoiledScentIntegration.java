package com.example.soundattract.integration.spoiled;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class SpoiledScentIntegration {
    private static final String SPOIL_TIMER_TAG = "spoiled:SpoilTimer";
    private static final String SPOIL_MAX_TIME_TAG = "spoiled:SpoilMaxTime";

    private SpoiledScentIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("spoiled");
    }

    public static boolean isEnabled() {
        return isLoaded()
                && SoundAttractConfig.COMMON != null
                && SoundAttractConfig.COMMON.enableSpoiledFoodScent.get();
    }

    public static double spoilageProgress(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return 0.0;
        }
        CompoundTag tag = stack.getTag();
        if (!tag.contains(SPOIL_TIMER_TAG, Tag.TAG_INT) || !tag.contains(SPOIL_MAX_TIME_TAG, Tag.TAG_INT)) {
            return 0.0;
        }

        int maxTime = tag.getInt(SPOIL_MAX_TIME_TAG);
        if (maxTime <= 0) {
            return 0.0;
        }
        int timer = tag.getInt(SPOIL_TIMER_TAG);
        return Math.max(0.0, Math.min(1.0, timer / (double) maxTime));
    }

    public static void logStatus() {
        if (!DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SPOILED)) {
            return;
        }
        if (!isLoaded()) {
            SoundAttractMod.LOGGER.info("[Spoiled] Mod not detected; Spoiled food scent is inactive.");
        } else if (SoundAttractConfig.COMMON.enableSpoiledFoodScent.get()) {
            SoundAttractMod.LOGGER.info("[Spoiled] Mod detected and spoilage-progress scent is enabled.");
        } else {
            SoundAttractMod.LOGGER.info("[Spoiled] Mod detected, but Spoiled food scent is disabled in integration config.");
        }
    }
}
