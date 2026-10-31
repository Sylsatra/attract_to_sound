package com.example.soundattract.integration.hotbath;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HotBathScentStateCache {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private HotBathScentStateCache() {
    }

    public record State(double dirtyMultiplier, Identifier aromaFluidId, double aromaMultiplier, long aromaExpiresAt) {
        static final State DEFAULT = new State(1.0, null, 1.0, 0L);
    }

    public static void update(UUID playerId, State state) {
        if (playerId == null) return;
        if (state == null) {
            STATES.remove(playerId);
        } else {
            STATES.put(playerId, state);
        }
    }

    public static void remove(UUID playerId) {
        if (playerId != null) STATES.remove(playerId);
    }

    public static State get(UUID playerId) {
        if (playerId == null) return State.DEFAULT;
        return STATES.getOrDefault(playerId, State.DEFAULT);
    }

    public static void clear() {
        STATES.clear();
    }

    public static double scentMultiplier(Player player, long now) {
        if (player == null) return 1.0;
        State state = get(player.getUUID());
        double aroma = now <= state.aromaExpiresAt() ? state.aromaMultiplier() : 1.0;
        return Math.max(0.0, state.dirtyMultiplier() * aroma);
    }
}
