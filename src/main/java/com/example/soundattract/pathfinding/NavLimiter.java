package com.example.soundattract.pathfinding;

import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public final class NavLimiter {
    private static final class State {
        long lastMoveTick;
        Vec3 lastDest;
    }

    private static final Map<Mob, State> STATE = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private static final class TeamBudget {
        long lastTick;
        int used;
    }

    private static final Map<Long, TeamBudget> TEAM_BUDGET = new java.util.concurrent.ConcurrentHashMap<>();

    private NavLimiter() {}

    private static long teamBudgetKey(Mob mob) {
        int dim = 0;
        try { dim = mob.level().dimension().identifier().hashCode(); } catch (Throwable ignored) {}
        Mob leader = MobGroupManager.getLeader(mob);
        int groupId = leader != null ? leader.getId() : mob.getId();
        return (((long) dim) << 32) ^ (groupId & 0xffffffffL);
    }

    private static boolean tryConsumeTeamMoveBudget(Mob mob, long nowTick) {
        int limit = 8;
        try { limit = Math.max(0, SoundAttractConfig.COMMON.moveToTeamBudgetPerTick.get()); } catch (Throwable ignored) {}
        if (limit <= 0) return true;

        if (TEAM_BUDGET.size() > 64) {
            TEAM_BUDGET.entrySet().removeIf(e -> Math.abs(nowTick - e.getValue().lastTick) > 200);
        }

        long k = teamBudgetKey(mob);
        TeamBudget b = TEAM_BUDGET.computeIfAbsent(k, kk -> new TeamBudget());
        if (b.lastTick != nowTick) {
            b.lastTick = nowTick;
            b.used = 0;
        }
        if (b.used >= limit) {
            PFStats.moveToDroppedCooldown++;
            return false;
        }
        b.used++;
        return true;
    }

    public static boolean maybeMoveTo(Mob mob, double x, double y, double z, double speed) {
        if (mob == null || mob.level() == null) return false;
        State s = STATE.computeIfAbsent(mob, m -> new State());
        long now = mob.level().getGameTime();

        if (!tryConsumeTeamMoveBudget(mob, now)) {
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

        if (dist < 16.0) {
            boolean walkable = NodeRouter.isWalkable(mob, desired);
            if (walkable) {
                net.minecraft.world.phys.HitResult res = mob.level().clip(new net.minecraft.world.level.ClipContext(
                        mob.position().add(0, 0.5, 0),
                        desired.add(0, 0.5, 0),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        mob
                ));
                if (res == null || res.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                    mob.getMoveControl().setWantedPosition(x, y, z, speed);
                    if (mob.horizontalCollision && mob.onGround()) mob.getJumpControl().jump();
                    s.lastMoveTick = now;
                    s.lastDest = desired;
                    PFStats.moveToIssued++;
                    return true;
                }
            }
        }

        Vec3 waypoint = NodeRouter.getWaypoint(mob, desired);
        boolean ok;
        try {
            ok = mob.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, speed);
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            try {
                PFStats.moveToFallbackAttempted++;
                double vx = waypoint.x - mob.getX();
                double vz = waypoint.z - mob.getZ();
                double len = Math.sqrt(vx * vx + vz * vz);
                if (len < 1e-3) len = 1.0;
                double nx = -vz / len;
                double nz = vx / len;
                double off = 0.75;
                boolean altOk = mob.getNavigation().moveTo(waypoint.x + nx * off, waypoint.y, waypoint.z + nz * off, speed)
                        || mob.getNavigation().moveTo(waypoint.x - nx * off, waypoint.y, waypoint.z - nz * off, speed);
                if (altOk) {
                    ok = true;
                    PFStats.moveToFallbackSucceeded++;
                }
            } catch (Throwable ignored) {}
        }
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
