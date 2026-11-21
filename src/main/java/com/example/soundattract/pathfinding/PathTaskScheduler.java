package com.example.soundattract.pathfinding;

import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PathTaskScheduler {
    public enum Priority {
        OPTIONAL,
        COMBAT,
        COMMITTED
    }

    private static final Map<ResourceKey<Level>, TickBudget> DIMENSION_BUDGETS = new HashMap<>();
    private static final TickBudget SERVER_BUDGET = new TickBudget();

    private PathTaskScheduler() {
    }

    static final class TickBudget {
        private long tick = Long.MIN_VALUE;
        private int attempts;

        void reset(long currentTick) {
            if (tick != currentTick) {
                tick = currentTick;
                attempts = 0;
            }
        }

        boolean canConsume(Priority priority, int limit) {
            int max = Math.max(0, limit);
            if (attempts >= max) {
                return false;
            }
            int reserve = reservation(max, 5);
            int committedReserve = reservation(max, 10);
            int threshold = switch (priority) {
                case OPTIONAL -> max - reserve;
                case COMBAT -> max - committedReserve;
                case COMMITTED -> max;
            };
            return attempts < Math.max(0, threshold);
        }

        void consume() {
            attempts++;
        }

        int attempts() {
            return attempts;
        }
    }

    private static int reservation(int limit, int divisor) {
        if (limit < divisor) return 0;
        return Math.min(limit, Math.max(1, (limit + divisor - 1) / divisor));
    }

    public static synchronized void reinitializeCaches() {
        DIMENSION_BUDGETS.clear();
        SERVER_BUDGET.reset(Long.MIN_VALUE);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.PATHFINDING)) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[PathTaskScheduler] Reset per-dimension and server path budgets");
        }
    }

    public static synchronized void clear() {
        DIMENSION_BUDGETS.clear();
        SERVER_BUDGET.reset(Long.MIN_VALUE);
    }

    public static boolean tryConsumePathAttempt(Level level) {
        return tryConsumePathAttempt(level, Priority.OPTIONAL);
    }

    public static synchronized boolean tryConsumePathAttempt(Level level, Priority priority) {
        if (level == null || level.isClientSide) {
            return false;
        }
        Priority selectedPriority = priority == null ? Priority.OPTIONAL : priority;
        int dimensionLimit = 10;
        int serverLimit = 40;
        try {
            if (SoundAttractConfig.COMMON != null) {
                dimensionLimit = Math.max(0, SoundAttractConfig.COMMON.maxPathAttemptsPerTick.get());
                serverLimit = Math.max(0, SoundAttractConfig.COMMON.maxPathAttemptsPerServerTick.get());
            }
        } catch (Throwable ignored) {
        }

        long dimensionTick = level.getGameTime();
        long serverTick = level.getServer() == null ? dimensionTick : level.getServer().getTickCount();
        TickBudget dimensionBudget = DIMENSION_BUDGETS.computeIfAbsent(level.dimension(), key -> new TickBudget());
        dimensionBudget.reset(dimensionTick);
        SERVER_BUDGET.reset(serverTick);

        if (!dimensionBudget.canConsume(selectedPriority, dimensionLimit)
                || !SERVER_BUDGET.canConsume(selectedPriority, serverLimit)) {
            PFStats.pathAttemptsRejected++;
            return false;
        }

        dimensionBudget.consume();
        SERVER_BUDGET.consume();
        PFStats.pathAttemptsConsumed++;
        return true;
    }

    static synchronized int dimensionAttempts(Level level) {
        if (level == null) {
            return 0;
        }
        TickBudget budget = DIMENSION_BUDGETS.get(level.dimension());
        if (budget == null || budget.tick != level.getGameTime()) {
            return 0;
        }
        return budget.attempts();
    }

    static synchronized int serverAttempts() {
        return SERVER_BUDGET.attempts();
    }
}
