package com.example.soundattract.integration.hotbath;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.UUID;

public final class HotBathScentStateCache {
    private static final Cache<UUID, State> STATES = Caffeine.newBuilder()
            .maximumSize(4096)
            .build();

    private HotBathScentStateCache() {}

    public record State(double dirtyMultiplier, ResourceLocation aromaFluidId, double aromaMultiplier, long aromaExpiresAt) {
        static final State DEFAULT = new State(1.0, null, 1.0, 0L);
    }

    public static void update(UUID playerId, State state) {
        if (playerId == null) return;
        if (state == null) {
            STATES.invalidate(playerId);
        } else {
            STATES.put(playerId, state);
        }
    }

    public static void remove(UUID playerId) {
        if (playerId != null) STATES.invalidate(playerId);
    }

    public static State get(UUID playerId) {
        if (playerId == null) return State.DEFAULT;
        State state = STATES.getIfPresent(playerId);
        return state == null ? State.DEFAULT : state;
    }

    public static void clear() {
        STATES.invalidateAll();
    }

    public static double scentMultiplier(Player player, long now) {
        if (player == null) return 1.0;
        State state = get(player.getUUID());
        double aroma = now <= state.aromaExpiresAt() ? state.aromaMultiplier() : 1.0;
        return Math.max(0.0, state.dirtyMultiplier() * aroma);
    }
}