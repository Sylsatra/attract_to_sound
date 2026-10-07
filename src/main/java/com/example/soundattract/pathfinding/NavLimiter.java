package com.example.soundattract.pathfinding;

import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.TimeUnit;

public final class NavLimiter {
    private static final class State {
        long lastMoveTick;
        Vec3 lastDest;
    }

    private static final Cache<Mob, State> STATE = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build();

    private static final class TeamBudget {
        long lastTick;
        int used;
    }

    private static final Cache<Long, TeamBudget> TEAM_BUDGET = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .build();

    private NavLimiter() {}

    private static long teamBudgetKey(Mob mob) {
        int dim = 0;
        try { dim = mob.level().dimension().location().hashCode(); } catch (Throwable ignored) {}
        Mob leader = MobGroupManager.getLeader(mob);
        int groupId = leader != null ? leader.getId() : mob.getId();
        return (((long) dim) << 32) ^ (groupId & 0xffffffffL);
    }

    private static boolean tryConsumeTeamMoveBudget(Mob mob, long nowTick) {
        int limit = 8;
        try { limit = Math.max(0, SoundAttractConfig.COMMON.moveToTeamBudgetPerTick.get()); } catch (Throwable ignored) {}
        if (limit <= 0) return true;

        long k = teamBudgetKey(mob);
        TeamBudget b = TEAM_BUDGET.asMap().computeIfAbsent(k, kk -> new TeamBudget());
        synchronized (b) {
            if (b.lastTick != nowTick) {
                b.lastTick = nowTick;
                b.used = 0;
            }
            if (b.used >= limit) {
                PFStats.moveToDroppedCooldown++;
                return false;
            }
            b.used++;
        }
        return true;
    }

    public static void clear() {
        STATE.invalidateAll();
        TEAM_BUDGET.invalidateAll();
        NodeRouter.clear();
    }

    public static boolean maybeMoveTo(Mob mob, double x, double y, double z, double speed) {
        if (mob == null || mob.level() == null) return false;
        State s = STATE.get(mob, ignored -> new State());
        long now = mob.level().getGameTime();
        if (!tryConsumeTeamMoveBudget(mob, now)) {
            PFStats.moveToDroppedCooldown++;
            return false;
        }
        int baseCooldown = 5;
        double baseMinDelta = 0.5;
        try {
            baseCooldown = Math.max(0, SoundAttractConfig.COMMON.moveToCooldownTicks.get());
            baseMinDelta = Math.max(0.0, SoundAttractConfig.COMMON.moveToMinDelta.get());
        } catch (Throwable ignored) {}
        Vec3 desired = new Vec3(x, y, z);
        double dist;
        try { dist = mob.position().distanceTo(desired); } catch (Throwable t) { dist = Double.MAX_VALUE; }
        int cooldown = baseCooldown;
        double minDelta = baseMinDelta;
        if (dist < 4.0) {
            cooldown = Math.min(cooldown, 3);
            minDelta = Math.min(minDelta, 0.25);
        } else if (dist < 8.0) {
            cooldown = Math.min(cooldown, 5);
            minDelta = Math.min(minDelta, 0.50);
        }
        boolean sameRecentDestination = s.lastDest != null && s.lastDest.distanceToSqr(desired) < (minDelta * minDelta);
        if (sameRecentDestination) {
            boolean navigationDone = false;
            try { navigationDone = mob.getNavigation().isDone(); } catch (Throwable ignored) {}
            if (!navigationDone || dist <= minDelta + 0.01) {
                PFStats.moveToDroppedDelta++;
                return false;
            }
        }
        if (cooldown > 0 && (now - s.lastMoveTick) < cooldown) {
            PFStats.moveToDroppedCooldown++;
            return false;
        }
        Vec3 desiredWaypoint = NodeRouter.getWaypoint(mob, desired);
        boolean ok = PursuitMoveExecutor.issueVanillaMove(mob, desiredWaypoint, speed);
        if (ok) {
            s.lastMoveTick = now;
            s.lastDest = desired;
            PFStats.moveToIssued++;
        } else {
            s.lastMoveTick = now;
        }
        return ok;
    }
}