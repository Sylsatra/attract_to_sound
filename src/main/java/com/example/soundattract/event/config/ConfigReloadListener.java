package com.example.soundattract.event.config;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.worker.WorkSchedulerManager;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeModConfigEvents;
import net.minecraftforge.fml.config.ModConfig;

public class ConfigReloadListener {
    public static void register() {
        ForgeModConfigEvents.loading(Soundattract.MOD_ID).register(ConfigReloadListener::onConfigLoad);
        ForgeModConfigEvents.reloading(Soundattract.MOD_ID).register(ConfigReloadListener::onConfigReload);
    }

    public static void onConfigLoad() {
        refreshDerivedConfig();
    }

    public static void onConfigReload() {
        refreshDerivedConfig();
    }

    private static void onConfigLoad(ModConfig config) {
        refreshDerivedConfig();
    }

    private static void onConfigReload(ModConfig config) {
        refreshDerivedConfig();
    }

    private static void refreshDerivedConfig() {
        SoundAttractConfig.bakeConfig();
        SoundAttractConfig.parseAndCachePlayerActionConfig();
        SoundAttractionEvents.invalidateCachedEntityTypes();
        WorkSchedulerManager.refresh();
    }
}
