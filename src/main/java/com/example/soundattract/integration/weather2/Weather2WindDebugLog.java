package com.example.soundattract.integration.weather2;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class Weather2WindDebugLog {
    private static final Cache<String, String> LAST_REPORTED_STATES = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private Weather2WindDebugLog() {
    }

    public static void report(ServerLevel level, String component, String state, Supplier<String> message) {
        if (level == null || component == null || state == null || message == null
                || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.WEATHER2_WIND)) {
            return;
        }

        Identifier dimension = level.dimension().identifier();
        String key = dimension + ":" + component;
        String previous = LAST_REPORTED_STATES.asMap().put(key, state);
        if (!state.equals(previous)) {
            SoundAttractMod.LOGGER.info("[Weather2 Wind] {}", message.get());
        }
    }

    public static void clear() {
        LAST_REPORTED_STATES.invalidateAll();
    }

    public static void clear(ServerLevel level, String component) {
        if (level != null && component != null) {
            LAST_REPORTED_STATES.invalidate(level.dimension().identifier() + ":" + component);
        }
    }
}
