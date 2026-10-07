package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import org.admany.quantified.api.QuantifiedAPI;
import net.minecraftforge.fml.ModList;

import java.util.concurrent.atomic.AtomicBoolean;

public final class QuantifiedIntegration {
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private QuantifiedIntegration() {
    }

    public static void bootstrap() {
        if (!INIT.compareAndSet(false, true)) return;
        if (!ModList.get().isLoaded("quantified")) {
            throw new IllegalStateException("Quantified API is mandatory for SoundAttract");
        }

        try {
            if (!QuantifiedOptionalBridge.isAvailable()) {
                throw new IllegalStateException("Quantified API 2.0 bridge is unavailable");
            }
            if (!QuantifiedAPI.registerV2(SoundAttractMod.MOD_ID)) {
                throw new IllegalStateException("Quantified API rejected SoundAttract registration");
            }
            SoundAttractMod.LOGGER.info("[Quantified] Quantified API 2.2 initialized as the mandatory worker backend");
            SoundAttractMod.LOGGER.info("[Quantified] Sound-score acceleration backend: {}",
                    QuantifiedGpuSoundScoreBridge.backendName());
        } catch (Throwable t) {
            throw new IllegalStateException("Quantified API initialization failed", t);
        }
    }
}