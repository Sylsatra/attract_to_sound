package com.example.soundattract;

import net.fabricmc.loader.api.FabricLoader;

public class ModChecker {
    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
