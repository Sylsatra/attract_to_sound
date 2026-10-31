package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import net.neoforged.fml.ModList;

import java.util.concurrent.atomic.AtomicBoolean;

public final class QuantifiedIntegration {
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private QuantifiedIntegration() {
    }

    public static void bootstrap() {
        if (!INIT.compareAndSet(false, true)) return;
        if (!ModList.get().isLoaded("quantified")) return;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return;
        } catch (Throwable ignored) {
        }

        try {
            if (!QuantifiedOptionalBridge.isAvailable()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.debug("[Quantified] Quantified API 2.0 bridge unavailable; falling back to local workers/cache.");
                }
                return;
            }

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[Quantified] Successfully initialized with Quantified API 2.0");
            }
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[Quantified] Initialization failed: {}", t.getMessage());
            }
        }
    }
}
