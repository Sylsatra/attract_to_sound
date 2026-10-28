package com.example.soundattract.reload;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.worker.WorkSchedulerManager;

/**
 * Stub: Config reload listener for Fabric.
 * Uses Fabric config API for full implementation.
 */
public class ConfigReloadListener {

    public static void onConfigLoad() {
        SoundAttractConfig.bakeConfig();
        SoundAttractionEvents.invalidateCachedEntityTypes();
        WorkSchedulerManager.refresh();
    }

    public static void onConfigReload() {
        SoundAttractConfig.bakeConfig();
        SoundAttractionEvents.invalidateCachedEntityTypes();
        WorkSchedulerManager.refresh();
    }
}
