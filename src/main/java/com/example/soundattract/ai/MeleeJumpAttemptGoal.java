package com.example.soundattract.ai;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

public final class MeleeJumpAttemptGoal extends Goal {
    private static final int CHECK_INTERVAL_TICKS = 4;
    private static final int STATIONARY_TICKS = 12;
    private static final int ATTEMPT_COOLDOWN_TICKS = 30;
    private static final int BLOCKED_RECHECK_TICKS = 16;
    private static final double STATIONARY_DISTANCE_SQR = 0.125 * 0.125;
    private static final double MIN_TARGET_RISE = 0.5;
    private static final double USABLE_JUMP_RISE = 1.0;
    private static final double JUMP_CLEARANCE = 1.25;

    private final Mob mob;
    private long nextCheckTick;
    private UUID watchedTarget;
    private UUID readyTarget;
    private long stationarySince;
    private int watchedPathNode;
    private double watchX;
    private double watchY;
    private double watchZ;

    public MeleeJumpAttemptGoal(Mob mob) {
        this.mob = mob;
        this.nextCheckTick = mob.level().getGameTime()
                + Math.floorMod(mob.getUUID().hashCode(), CHECK_INTERVAL_TICKS);
        setFlags(EnumSet.of(Flag.JUMP));
    }

    public static boolean supports(Mob mob) {
        if (!(mob.getNavigation() instanceof GroundPathNavigation) || mob instanceof RangedAttackMob
                || mob.goalSelector == null) {
            return false;
        }
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof MeleeAttackGoal) return true;
        }
        return false;
    }

    @Override
    public boolean canUse() {
        readyTarget = null;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        if (now < nextCheckTick) return false;
        if (now - nextCheckTick > 2L) watchedTarget = null;
        nextCheckTick = now + CHECK_INTERVAL_TICKS;

        LivingEntity target = mob.getTarget();
        if (!canJumpToward(target)) {
            watchedTarget = null;
            return false;
        }

        Path path = mob.getNavigation().getPath();
        int pathNode = path == null || path.isDone() ? -1 : path.getNextNodeIndex();
        UUID targetId = target.getUUID();
        if (!targetId.equals(watchedTarget) || pathNode != watchedPathNode
                || mob.distanceToSqr(watchX, watchY, watchZ) > STATIONARY_DISTANCE_SQR) {
            watchedTarget = targetId;
            watchedPathNode = pathNode;
            stationarySince = now;
            watchX = mob.getX();
            watchY = mob.getY();
            watchZ = mob.getZ();
            return false;
        }
        if (now - stationarySince < STATIONARY_TICKS) return false;

        if (hasOtherMovementOwner() || level.getEntity(targetId) != target
                || !mob.canAttack(target) || mob.isAlliedTo(target) || mob.onClimbable() || !hasHeadroom(level)) {
            nextCheckTick = now + BLOCKED_RECHECK_TICKS;
            watchedTarget = null;
            return false;
        }
        readyTarget = targetId;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target != null && target.getUUID().equals(readyTarget) && canJumpToward(target)) {
            mob.getJumpControl().jump();
            nextCheckTick = mob.level().getGameTime() + ATTEMPT_COOLDOWN_TICKS;
        }
        readyTarget = null;
        watchedTarget = null;
    }

    private boolean canJumpToward(LivingEntity target) {
        if (target == null || !target.isAlive() || !mob.isAlive() || target.level() != mob.level()
                || !mob.onGround() || mob.isInWater() || mob.isInLava() || mob.isPassenger() || mob.isVehicle()
                || !(mob.getNavigation() instanceof GroundPathNavigation)) {
            return false;
        }
        double rise = target.getY() - mob.getY();
        if (rise < MIN_TARGET_RISE) return false;
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double remainingRise = Math.max(0.0, rise - USABLE_JUMP_RISE);
        double distanceAfterJumpSqr = dx * dx + dz * dz + remainingRise * remainingRise;
        return distanceAfterJumpSqr <= mob.getMeleeAttackRangeSqr(target) && !mob.isWithinMeleeAttackRange(target);
    }

    private boolean hasOtherMovementOwner() {
        if (LivingWallCoordinator.isControlled(mob)) return true;
        Mob leader = MobGroupManager.getLeader(mob);
        if (leader == null) leader = mob;
        if (RaidManager.isRaidTicking(leader) || RaidManager.isRaidAdvancing(leader)) return true;
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            if (goal.isRunning() && goal.getFlags().contains(Flag.MOVE)
                    && !(goal.getGoal() instanceof MeleeAttackGoal)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHeadroom(ServerLevel level) {
        AABB bounds = mob.getBoundingBox();
        AABB overhead = new AABB(bounds.minX, bounds.maxY, bounds.minZ,
                bounds.maxX, bounds.maxY + JUMP_CLEARANCE, bounds.maxZ);
        if (overhead.maxY >= level.getMaxBuildHeight()) return false;

        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(overhead.minX) - 1);
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(overhead.maxX) + 1);
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(overhead.minZ) - 1);
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(overhead.maxZ) + 1);
        ServerChunkCache chunks = level.getChunkSource();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!chunks.hasChunk(x, z)) return false;
            }
        }
        return !level.getBlockCollisions(mob, overhead).iterator().hasNext();
    }
}
