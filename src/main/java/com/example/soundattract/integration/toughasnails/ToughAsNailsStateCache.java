package com.example.soundattract.integration.toughasnails;

import com.example.soundattract.config.SoundAttractConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ToughAsNailsStateCache {
    public record State(double scentMultiplier, double soundMultiplier, long updatedAt) {
    }

    private static final Cache<UUID, State> STATES = Caffeine.newBuilder().maximumSize(4096).build();

    private ToughAsNailsStateCache() {
    }

    public static void update(UUID playerId, State state) {
        if (playerId != null && state != null) {
            STATES.put(playerId, state);
        }
    }

    public static double scentMultiplier(ServerPlayer player, long now) {
        State state = state(player, now);
        return state == null ? 1.0 : validMultiplier(state.scentMultiplier());
    }

    public static double soundMultiplier(ServerPlayer player, long now) {
        State state = state(player, now);
        return state == null ? 1.0 : validMultiplier(state.soundMultiplier());
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            STATES.invalidate(playerId);
        }
    }

    public static void clear() {
        STATES.invalidateAll();
    }

    private static State state(ServerPlayer player, long now) {
        if (player == null) {
            return null;
        }
        State state = STATES.getIfPresent(player.getUUID());
        if (state == null || now - state.updatedAt() > staleTicks()) {
            return null;
        }
        return state;
    }

    private static long staleTicks() {
        int interval = 40;
        if (SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks != null) {
            interval = SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks.get();
        }
        return Math.max(40L, (long) Math.max(1, interval) * 2L + 20L);
    }

    private static double validMultiplier(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 1.0;
    }
}
