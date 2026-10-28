package com.example.soundattract.integration.spoiled;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.fml.ModList;

public final class SpoiledScentIntegration {
    private static final ResourceLocation SPOIL_TIMER_ID = ResourceLocation.fromNamespaceAndPath("spoiled", "spoil_timer");

    private SpoiledScentIntegration() {}

    public static boolean isLoaded() { return ModList.get().isLoaded("spoiled"); }

    public static boolean isEnabled() {
        return isLoaded() && SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.enableSpoiledFoodScent.get();
    }

    public static double spoilageProgress(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SPOIL_TIMER_ID);
        if (type == null) return 0.0;
        Object timer = stack.get(type);
        if (timer == null) return 0.0;
        try {
            int current = ((Number) timer.getClass().getMethod("timer").invoke(timer)).intValue();
            int max = ((Number) timer.getClass().getMethod("maxTime").invoke(timer)).intValue();
            return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, current / (double) max));
        } catch (ReflectiveOperationException | ClassCastException error) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SPOILED)) {
                SoundAttractMod.LOGGER.debug("[Spoiled] Could not read spoil_timer data component: {}", error.toString());
            }
            return 0.0;
        }
    }

    public static void logStatus() {
        if (!DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SPOILED)) return;
        SoundAttractMod.LOGGER.info(isEnabled() ? "[Spoiled] Mod detected and spoilage-progress scent is enabled." : "[Spoiled] Integration inactive.");
    }
}
