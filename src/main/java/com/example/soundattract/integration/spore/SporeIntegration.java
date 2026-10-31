package com.example.soundattract.integration.spore;

import net.neoforged.fml.ModList;

public class SporeIntegration {
    private static final boolean IS_SPORE_LOADED = ModList.get().isLoaded("spore");

    public static boolean isSporeLoaded() {
        return IS_SPORE_LOADED;
    }
}