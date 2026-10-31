package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.worker.WorkSchedulerManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class ConfigReloadListener {
    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
    }
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(SoundAttractMod.MOD_ID) && SoundAttractConfig.isConfigReady()) {
            SoundAttractConfig.bakeConfig();
            SoundAttractionEvents.invalidateCachedEntityTypes();
            WorkSchedulerManager.refresh();
        }
    }
}
