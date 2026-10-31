package com.example.soundattract.integration.toughasnails;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ToughAsNailsStateCache {
    public record State(double scentMultiplier, double soundMultiplier, long updatedAt) {}

    private static final ConcurrentHashMap<UUID, State> STATES = new ConcurrentHashMap<>();

    private ToughAsNailsStateCache() {}

    public static void update(UUID playerId, State state) {
        if (playerId == null || state == null) return;
        STATES.put(playerId, state);
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
            STATES.remove(playerId);
        }
    }

    public static void clear() {
        STATES.clear();
    }

    private static State state(ServerPlayer player, long now) {
        if (player == null) return null;
        State state = STATES.get(player.getUUID());
        if (state == null) return null;
        if ((now - state.updatedAt()) > staleTicks()) return null;
        return state;
    }

    private static long staleTicks() {
        int poll = 40;
        try {
            if (SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks != null) {
                poll = SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks.get();
            }
        } catch (Throwable ignored) {
        }
        return Math.max(40L, (long) Math.max(1, poll) * 2L + 20L);
    }

    private static double validMultiplier(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 1.0;
    }
}
