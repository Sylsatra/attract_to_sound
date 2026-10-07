package com.example.soundattract.event.config;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.worker.WorkSchedulerManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ConfigReloadListener {
    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(SoundAttractMod.MOD_ID)) {
            SoundAttractConfig.bakeConfig();
            SoundAttractionEvents.invalidateCachedEntityTypes();
            WorkSchedulerManager.refresh();
        }
    }
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(SoundAttractMod.MOD_ID)) {
            SoundAttractConfig.bakeConfig();
            SoundAttractionEvents.invalidateCachedEntityTypes();
            WorkSchedulerManager.refresh();
        }
    }
}