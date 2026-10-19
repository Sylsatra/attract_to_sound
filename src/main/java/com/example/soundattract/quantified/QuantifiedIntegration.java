package com.example.soundattract.quantified;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.util.concurrent.atomic.AtomicBoolean;

public final class QuantifiedIntegration {
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private QuantifiedIntegration() {
    }

    public static void bootstrap() {
        if (!INIT.compareAndSet(false, true)) return;
        if (!FabricLoader.getInstance().isModLoaded("quantified")) {
            throw new IllegalStateException("Sound Attract requires Quantified API 2.2.3 or newer");
        }
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) {
                throw new IllegalStateException("Quantified integration cannot be disabled because Quantified is mandatory");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to read Quantified integration configuration", t);
        }

        try {
            if (!QuantifiedOptionalBridge.registerV2(Soundattract.MOD_ID)) {
                throw new IllegalStateException("Quantified API V2 registration failed");
            }
            Soundattract.LOGGER.info("[Quantified] Mandatory Quantified API V2 integration initialized");
        } catch (Throwable t) {
            throw new IllegalStateException("Mandatory Quantified API initialization failed", t);
        }
    }
}
