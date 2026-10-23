package com.example.soundattract.integration.spoiled;

import net.minecraft.core.component.DataComponents;

import com.example.soundattract.ModChecker;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class SpoiledScentIntegration {
    private static final String SPOIL_TIMER_TAG = "spoiled:SpoilTimer";
    private static final String SPOIL_MAX_TIME_TAG = "spoiled:SpoilMaxTime";

    private SpoiledScentIntegration() {
    }

    public static boolean isEnabled() {
        return ModChecker.isLoaded("spoiled") && SoundAttractConfig.COMMON != null
                && SoundAttractConfig.COMMON.enableSpoiledFoodScent.get();
    }

    public static double spoilageProgress(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            return 0.0;
        }
        CompoundTag tag = CamoUtil.getCustomTagReadOnly(stack);
        if (!(tag.get(SPOIL_TIMER_TAG) instanceof net.minecraft.nbt.IntTag) || !(tag.get(SPOIL_MAX_TIME_TAG) instanceof net.minecraft.nbt.IntTag)) {
            return 0.0;
        }
        int maximum = tag.getIntOr(SPOIL_MAX_TIME_TAG, 0);
        if (maximum <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, tag.getIntOr(SPOIL_TIMER_TAG, 0) / (double) maximum));
    }
}
