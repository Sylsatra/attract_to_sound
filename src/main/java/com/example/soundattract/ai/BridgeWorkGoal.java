package com.example.soundattract.ai;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class BridgeWorkGoal extends Goal {
    public static final int PRIORITY = -4;
    private final Mob mob;
    private long generation = Long.MIN_VALUE;
    private BlockPos targetCell;
    private double previousTargetDistance = Double.POSITIVE_INFINITY;
    private int noProgressTicks;
    private long nextBlockClearTick;
    private BlockBreakerPosGoal blockClearGoal;

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
            stopBlockClear();
            generation = currentGeneration;
            LivingWallCoordinator.reportControllerStarted(mob, generation);
            resetProgress(target);
        }
        if (mob.noPhysics) {
            stopBlockClear();
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target.x, target.y, target.z);
            LivingWallCoordinator.tickBridgeWork(mob, target);
            return;
        }
        double distSq = mob.distanceToSqr(target.x, target.y, target.z);
        updateProgress(target, distSq);
        if (distSq <= 2.25D) {
            stopBlockClear();
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            LivingWallCoordinator.tickBridgeWork(mob, target);
            return;
        }
        if (blockClearGoal != null && LivingWallCoordinator.hasValidCombatPreemption(mob)) {
            stopBlockClear();
        }
        if (blockClearGoal == null && SoundAttractConfig.COMMON.enableBlockBreaking.get()
                && noProgressTicks >= 40 && mob.level().getGameTime() >= nextBlockClearTick) {
            tryStartBlockClear(target);
        }
        boolean clearingBlock = blockClearGoal != null;
        if ((mob.tickCount + mob.getId()) % 3 == 0) {
            mob.getLookControl().setLookAt(target.x, target.y, target.z);
        }
        if (!clearingBlock && (mob.getNavigation().isDone() || (mob.tickCount % 20 == 0 && (mob.getNavigation().getTargetPos() == null
                || mob.getNavigation().getTargetPos().distToCenterSqr(target) > 4.0D)))) {
            com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, target, 1.0D,
                    LivingWallCoordinator.hasCommittedNavigationWork(mob)
                            ? com.example.soundattract.pathfinding.PathTaskScheduler.Priority.COMMITTED
                            : com.example.soundattract.pathfinding.PathTaskScheduler.Priority.OPTIONAL);
        }
        LivingWallCoordinator.tickBridgeWork(mob, target, clearingBlock);
        if (clearingBlock) {
            blockClearGoal.tick();
            if (blockClearGoal.isFinished()) stopBlockClear();
        }
    }

    private void tryStartBlockClear(Vec3 target) {
        nextBlockClearTick = mob.level().getGameTime() + 40L;
        if (!LivingWallCoordinator.hasCommittedNavigationWork(mob)
                || LivingWallCoordinator.getBasinEvacuationTarget(mob) != null
                || LivingWallCoordinator.getCrosserTarget(mob) != null) {
            noProgressTicks = 0;
            return;
        }
        BlockPos targetPos = BlockPos.containing(target);
        BlockPos obstruction = BlockBreakerPosGoal.findFirstBreakableObstruction(mob, target);
        if (obstruction == null || obstruction.equals(targetPos.below())
                || obstruction.equals(mob.blockPosition().below())) {
            noProgressTicks = 0;
            return;
        }
        double timeMultiplier = SoundAttractConfig.COMMON.blockBreakingTimeMultiplier.get();
        boolean toolOnly = SoundAttractConfig.COMMON.blockBreakingToolOnly.get();
        boolean properTool = SoundAttractConfig.COMMON.blockBreakingProperToolOnly.get();
        BlockBreakerPosGoal candidate = new BlockBreakerPosGoal(mob, obstruction, timeMultiplier,
                toolOnly, properTool, properTool, obstruction);
        if (!candidate.canUse()) {
            noProgressTicks = 0;
            return;
        }
        mob.getNavigation().stop();
        candidate.start();
        if (!candidate.hasRequiredToolForCurrentBlock()) {
            candidate.stop();
            noProgressTicks = 0;
            return;
        }
        blockClearGoal = candidate;
    }

    private void updateProgress(Vec3 target, double distanceSquared) {
        BlockPos nextTargetCell = BlockPos.containing(target);
        if (!nextTargetCell.equals(targetCell)) {
            stopBlockClear();
            targetCell = nextTargetCell;
            resetProgress(target);
            previousTargetDistance = distanceSquared;
            return;
        }
        if (distanceSquared + 0.04D < previousTargetDistance) {
            noProgressTicks = 0;
        } else {
            noProgressTicks++;
        }
        previousTargetDistance = distanceSquared;
    }

    private void resetProgress(Vec3 target) {
        targetCell = target == null ? null : BlockPos.containing(target);
        previousTargetDistance = Double.POSITIVE_INFINITY;
        noProgressTicks = 0;
        nextBlockClearTick = mob.level().getGameTime();
    }

    private void stopBlockClear() {
        if (blockClearGoal != null) {
            blockClearGoal.stop();
            blockClearGoal = null;
        }
    }

    @Override
    public void stop() {
        stopBlockClear();
        LivingWallCoordinator.reportControllerStopped(mob);
        if (mob.isAlive() && !mob.isRemoved()) mob.getNavigation().stop();
    }
}
