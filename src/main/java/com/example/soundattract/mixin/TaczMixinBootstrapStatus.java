package com.example.soundattract.mixin;

/** Bootstrap-safe flags shared with the post-start TACZ diagnostics. Keep this class free of Minecraft types. */
public final class TaczMixinBootstrapStatus {
    private static volatile boolean npcPluginLoaded;
    private static volatile boolean hostilesPluginLoaded;

    private TaczMixinBootstrapStatus() {
    }

    public static void markNpcPluginLoaded() {
        npcPluginLoaded = true;
    }

    public static void markHostilesPluginLoaded() {
        hostilesPluginLoaded = true;
    }

    public static boolean isNpcPluginLoaded() {
        return npcPluginLoaded;
    }

    public static boolean isHostilesPluginLoaded() {
        return hostilesPluginLoaded;
    }
}
