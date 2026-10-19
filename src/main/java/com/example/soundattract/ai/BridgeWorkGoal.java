package com.example.soundattract.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class BridgeWorkGoal extends Goal {
    public static final int PRIORITY = -4;
    private final Mob mob;
    private long generation = Long.MIN_VALUE;

    public BridgeWorkGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean canUse() {
        return mob.isAlive() && !mob.isRemoved()
                && LivingWallCoordinator.getBridgeWorkTarget(mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        generation = LivingWallCoordinator.getBridgeWorkGeneration(mob);
        LivingWallCoordinator.reportControllerStarted(mob, generation);
        mob.setTarget(null);
        mob.setAggressive(false);
    }

    @Override
    public void tick() {
        if (!mob.isAlive() || mob.isRemoved()) return;
        mob.setTarget(null);
        mob.setAggressive(false);
        Vec3 target = LivingWallCoordinator.getBridgeWorkTarget(mob);
        if (target == null) return;
        long currentGeneration = LivingWallCoordinator.getBridgeWorkGeneration(mob);
        if (generation != currentGeneration) {
            generation = currentGeneration;
            LivingWallCoordinator.reportControllerStarted(mob, generation);
        }
        if (mob.noPhysics) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target.x, target.y, target.z);
            LivingWallCoordinator.tickBridgeWork(mob, target);
            return;
        }
        double distSq = mob.distanceToSqr(target.x, target.y, target.z);
        if (distSq <= 2.25D) {
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            LivingWallCoordinator.tickBridgeWork(mob, target);
            return;
        }
        if ((mob.tickCount + mob.getId()) % 3 == 0) {
            mob.getLookControl().setLookAt(target.x, target.y, target.z);
        }
        if (mob.getNavigation().isDone() || (mob.tickCount % 20 == 0 && (mob.getNavigation().getTargetPos() == null
                || mob.getNavigation().getTargetPos().distToCenterSqr(target) > 4.0D))) {
            com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, target, 1.0D);
        }
        LivingWallCoordinator.tickBridgeWork(mob, target);
    }

    @Override
    public void stop() {
        LivingWallCoordinator.reportControllerStopped(mob);
        if (mob.isAlive() && !mob.isRemoved()) mob.getNavigation().stop();
    }
}
