package com.example.soundattract.integration.csgrenades;

import net.minecraftforge.fml.ModList;

public class CsGrenadesCompat {
    private static final boolean LOADED = ModList.get().isLoaded("csgrenades");

    public static boolean isLoaded() {
        return LOADED;
    }
}