package com.example.soundattract.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public final class CueScanBudget {
    private static final Cache<Object, State> STATES = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(32)
            .build();

    private CueScanBudget() {}

    public static boolean tryAcquire(Mob mob) {
        if (mob == null || mob.level().isClientSide()) return false;
        Level level = mob.level();
        MinecraftServer server = level.getServer();
        if (server != null && !server.isSameThread()) return false;
        State state = STATES.get(server != null ? server : level, ignored -> new State());
        long tick = server != null ? server.getTickCount() : level.getGameTime();
        int limit = Math.max(1, com.example.soundattract.config.SoundAttractConfig.COMMON.cueScansPerTick.get());
        if (state.tick != tick) {
            state.tick = tick;
            state.used = 0;
        }
        if (state.used >= limit) return false;
        state.used++;
        return true;
    }

    private static final class State {
        private long tick = Long.MIN_VALUE;
        private int used;
    }
}
