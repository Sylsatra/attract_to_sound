package com.example.soundattract.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.phys.Vec3;
import com.example.soundattract.pathfinding.PursuitNavigation;

import java.util.EnumSet;
import java.util.UUID;

public final class ClueInvestigationGoal extends Goal {
    private static final int MOVE_INTERVAL = 20;
    private static final int STUCK_LIMIT = 20;

    private final Mob mob;
    private ClueDirector.Hint hint;
    private BlockPos moveTarget;
    private Path ownedNavigationPath;
    private long nextMoveTick;
    private long lastProgressTick;
    private Vec3 lastPosition;
    private int stuckTicks;
    private boolean ownsNavigation;
    private boolean alternateAttempted;

    public ClueInvestigationGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat.managesSecondBrainMovement(mob)
                || !(mob.level() instanceof ServerLevel) || mob.getTarget() != null || !mob.getNavigation().isDone()
                || LivingWallCoordinator.isControlled(mob)) return false;
        if (!ClueDirector.registerInterest(mob)) return false;
        hint = ClueDirector.takeHint(mob);
        return hint != null;
    }

    @Override
    public boolean canContinueToUse() {
        return hint != null && mob.getTarget() == null && !LivingWallCoordinator.isControlled(mob)
                && ClueDirector.isHintActive(mob, hint);
    }

    @Override
    public void start() {
        moveTarget = hint.position().immutable();
        nextMoveTick = mob.level().getGameTime();
        lastPosition = null;
        lastProgressTick = 0L;
        stuckTicks = 0;
        alternateAttempted = false;
        issueMove();
    }

    @Override
    public void tick() {
        if (hint == null || !(mob.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (mob.getTarget() != null || LivingWallCoordinator.isControlled(mob)) {
            finish(ClueDirector.Resolution.PREEMPTED);
            stopOwnedNavigation();
            return;
        }
        if (!mob.getNavigation().isDone() && (!ownsNavigation || mob.getNavigation().getPath() != ownedNavigationPath)) {
            finish(ClueDirector.Resolution.PREEMPTED);
            return;
        }
        if (moveTarget != null && mob.blockPosition().distSqr(moveTarget) <= 4.0) {
            if (!moveTarget.equals(hint.position())) {
                moveTarget = hint.position().immutable();
                nextMoveTick = now;
                stuckTicks = 0;
                lastPosition = mob.position();
                issueMove();
                return;
            }
            finish(ClueDirector.Resolution.ARRIVED);
            stopOwnedNavigation();
            return;
        }

        boolean stillOwnsPath = ownsNavigation && ownedNavigationPath != null
                && mob.getNavigation().getPath() == ownedNavigationPath;
        if (lastPosition != null && mob.position().distanceToSqr(lastPosition) < 0.04) {
            if (stillOwnsPath && now - lastProgressTick >= STUCK_LIMIT) stuckTicks = STUCK_LIMIT;
        } else {
            stuckTicks = 0;
            lastPosition = mob.position();
            lastProgressTick = now;
        }

        if (stuckTicks >= STUCK_LIMIT) {
            stopOwnedNavigation();
            if (!alternateAttempted && hint.expiresAt() - now > MOVE_INTERVAL) {
                alternateAttempted = true;
                moveTarget = alternateWaypoint(hint.position(), mob.getUUID(), mob, level);
                if (moveTarget.equals(hint.position())) {
                    finish(ClueDirector.Resolution.FAILED);
                    return;
                }
                nextMoveTick = now;
                stuckTicks = 0;
                lastPosition = mob.position();
                lastProgressTick = now;
            } else {
                finish(ClueDirector.Resolution.FAILED);
                return;
            }
        }

        if (stillOwnsPath && !mob.getNavigation().isDone()) return;
        if (now >= nextMoveTick) issueMove();
    }

    @Override
    public void stop() {
        if (hint != null) finish(ClueDirector.Resolution.PREEMPTED);
        ClueDirector.cancelInterest(mob);
        stopOwnedNavigation();
        moveTarget = null;
        lastPosition = null;
        stuckTicks = 0;
    }

    private void issueMove() {
        if (hint == null || moveTarget == null || !(mob.level() instanceof ServerLevel level)) return;
        nextMoveTick = level.getGameTime() + MOVE_INTERVAL;
        if (!level.hasChunkAt(moveTarget)) return;
        if (!mob.getNavigation().isDone()) return;
        ownsNavigation = false;
        ownedNavigationPath = null;
        boolean accepted = PursuitNavigation.maybeMoveTo(mob, Vec3.atBottomCenterOf(moveTarget), 1.0);
        Path path = mob.getNavigation().getPath();
        if (accepted && path != null) {
            ownsNavigation = true;
            ownedNavigationPath = path;
            lastPosition = mob.position();
            lastProgressTick = level.getGameTime();
            stuckTicks = 0;
        }
    }

    private void stopOwnedNavigation() {
        if (ownsNavigation && ownedNavigationPath != null
                && mob.getNavigation().getPath() == ownedNavigationPath) {
            mob.getNavigation().stop();
        }
        ownsNavigation = false;
        ownedNavigationPath = null;
    }

    private void finish(ClueDirector.Resolution resolution) {
        if (hint == null) return;
        ClueDirector.releaseHint(mob, hint, resolution);
        hint = null;
    }

    private static BlockPos alternateWaypoint(BlockPos clue, UUID id, Mob mob, ServerLevel level) {
        int direction = Math.floorMod(id.hashCode(), 4);
        int dx = direction == 0 ? 3 : direction == 1 ? -3 : 0;
        int dz = direction == 2 ? 3 : direction == 3 ? -3 : 0;
        BlockPos candidate = clue.offset(dx, 0, dz);
        if (!level.hasChunkAt(candidate) || !level.isEmptyBlock(candidate)) return clue;
        return mob.getNavigation() instanceof FlyingPathNavigation || level.getBlockState(candidate.below()).blocksMotion()
                ? candidate : clue;
    }

    private void cancelForBrainDisabled() {
        if (hint != null) ClueDirector.releaseHint(mob, hint, ClueDirector.Resolution.PREEMPTED);
        hint = null;
        ClueDirector.cancelInterest(mob);
        stopOwnedNavigation();
        moveTarget = null;
        lastPosition = null;
        stuckTicks = 0;
    }

    public static void stopForBrainDisabled(Mob mob) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ClueInvestigationGoal goal) goal.cancelForBrainDisabled();
        }
    }

    public static ClueDirector.Hint activeHint(Mob mob) {
        if (mob == null) return null;
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ClueInvestigationGoal goal && goal.hint != null) return goal.hint;
        }
        return null;
    }

    public static boolean ownsActiveHintPath(Mob mob, ClueDirector.Hint hint) {
        if (mob == null || hint == null) return false;
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ClueInvestigationGoal goal && goal.hint == hint) {
                return goal.ownsNavigation && goal.ownedNavigationPath != null
                        && mob.getNavigation().getPath() == goal.ownedNavigationPath
                        && !mob.getNavigation().isDone();
            }
        }
        return false;
    }
}
